package pl.czak.learnlauncher.data.sync.tcp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import pl.czak.learnlauncher.data.model.UnifiedPayload
import pl.czak.learnlauncher.data.model.UnifiedTables
import pl.czak.learnlauncher.data.sync.SyncApi
import pl.czak.learnlauncher.data.sync.SyncResponse
import java.io.IOException

/**
 * [SyncApi] implementation that talks directly to the simple-tcp-comm
 * TCP job queue instead of going through an HTTP shim.
 *
 * Responsibilities on top of [QueueTcpClient]:
 *
 *  1. Serialize the [UnifiedPayload] to JSON.
 *  2. Chunk the payload if its serialized size exceeds
 *     [QueueProtocol.SAFE_CHUNK_BYTES] — the server's `MAX_PAYLOAD` is 1 MB,
 *     and a single over-large submit would be rejected mid-RPC.
 *  3. Submit each chunk as an independent `ingest_unified_payload` job.
 *     Every chunk is itself a valid UnifiedPayload (schema v3) with
 *     smaller table subsets, so the worker's ingest handler doesn't need
 *     to know anything about chunking.
 *  4. Aggregate per-table counts from the original (unchunked) payload
 *     and return them in [SyncResponse.counts]. Per `docs/constraints.md`
 *     rule 5, these are *sent* counts, not *ingested* counts — the
 *     handler is idempotent so this is safe.
 *
 * Chunking is row-count based, not byte based: we start with all rows,
 * and if serialized size is too large we halve the per-table row counts
 * and try again. This converges fast and never produces a chunk bigger
 * than the previous one by more than a row.
 */
class TcpQueueSyncApi(
    private val host: String,
    private val port: Int,
    private val client: QueueTcpClient = QueueTcpClient(host, port),
) : SyncApi {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun upload(payload: UnifiedPayload, token: String): SyncResponse {
        return try {
            val chunks = chunkPayload(payload)
            for (chunk in chunks) {
                val chunkJson: JsonElement = json.encodeToJsonElement(chunk)
                client.submitIngest(chunkJson, bearerToken = token)
            }
            SyncResponse(accepted = true, counts = countRows(payload.tables), error = null)
        } catch (e: QueueSubmitException) {
            SyncResponse(accepted = false, counts = null, error = e.message ?: "Queue submit failed")
        } catch (e: IOException) {
            SyncResponse(accepted = false, counts = null, error = "Network error")
        } catch (e: Exception) {
            SyncResponse(accepted = false, counts = null, error = "Sync failed: ${e.message ?: e::class.simpleName}")
        }
    }

    // ────────────────────────────────────────────────────────────
    // Chunking
    // ────────────────────────────────────────────────────────────

    /**
     * Split [payload] into one or more UnifiedPayload chunks, each of which
     * serializes to at most [QueueProtocol.SAFE_CHUNK_BYTES] bytes of JSON.
     *
     * Strategy: start with the full payload, measure, and if it's over the
     * limit bisect by splitting each table's row list into halves (keeping
     * the envelope fields the same). Recurse on each half. This yields at
     * most ceil(log2(rowCount)) levels of splitting for a worst-case
     * payload.
     */
    internal fun chunkPayload(payload: UnifiedPayload): List<UnifiedPayload> {
        val serialized = json.encodeToString(payload)
        if (serialized.toByteArray(Charsets.UTF_8).size <= QueueProtocol.SAFE_CHUNK_BYTES) {
            return listOf(payload)
        }
        val totalRows = rowCount(payload.tables)
        if (totalRows <= 1) {
            // A single row can't be split further; ship it and let the
            // server reject it with "payload too large" if it doesn't fit.
            return listOf(payload)
        }
        val (left, right) = splitTables(payload.tables)
        return chunkPayload(payload.copy(tables = left)) +
            chunkPayload(payload.copy(tables = right))
    }

    /** Halve each table list into (first half, second half). */
    private fun splitTables(t: UnifiedTables): Pair<UnifiedTables, UnifiedTables> {
        fun <T> halves(xs: List<T>): Pair<List<T>, List<T>> {
            if (xs.size <= 1) return xs to emptyList()
            val mid = xs.size / 2
            return xs.subList(0, mid).toList() to xs.subList(mid, xs.size).toList()
        }

        val (s1, s2) = halves(t.sessionEvents)
        val (a1, a2) = halves(t.annotationRecords)
        val (c1, c2) = halves(t.chatMessages)
        val (p1, p2) = halves(t.pageInteractions)
        val (l1, l2) = halves(t.appLaunchRecords)
        val (se1, se2) = halves(t.settingsChanges)
        val (rt1, rt2) = halves(t.regionTranslations)

        val left = UnifiedTables(
            sessionEvents = s1,
            annotationRecords = a1,
            chatMessages = c1,
            pageInteractions = p1,
            appLaunchRecords = l1,
            settingsChanges = se1,
            regionTranslations = rt1,
        )
        val right = UnifiedTables(
            sessionEvents = s2,
            annotationRecords = a2,
            chatMessages = c2,
            pageInteractions = p2,
            appLaunchRecords = l2,
            settingsChanges = se2,
            regionTranslations = rt2,
        )
        return left to right
    }

    private fun rowCount(t: UnifiedTables): Int =
        t.sessionEvents.size + t.annotationRecords.size + t.chatMessages.size +
            t.pageInteractions.size + t.appLaunchRecords.size + t.settingsChanges.size +
            t.regionTranslations.size

    private fun countRows(t: UnifiedTables): Map<String, Int> = mapOf(
        "session_events" to t.sessionEvents.size,
        "annotation_records" to t.annotationRecords.size,
        "chat_messages" to t.chatMessages.size,
        "page_interactions" to t.pageInteractions.size,
        "app_launch_records" to t.appLaunchRecords.size,
        "settings_changes" to t.settingsChanges.size,
        "region_translations" to t.regionTranslations.size,
    )
}
