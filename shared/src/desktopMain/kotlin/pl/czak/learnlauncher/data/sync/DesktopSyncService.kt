package pl.czak.learnlauncher.data.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import pl.czak.learnlauncher.currentTimeMillis
import pl.czak.learnlauncher.data.InMemoryAnnotationRepository
import pl.czak.learnlauncher.data.auth.DesktopAuthManager
import pl.czak.learnlauncher.data.model.*
import pl.czak.learnlauncher.data.sync.tcp.QueueProtocol
import pl.czak.learnlauncher.data.sync.tcp.QueueSubmitException
import pl.czak.learnlauncher.data.sync.tcp.QueueTcpClient
import java.io.IOException

data class SyncResult(
    val success: Boolean,
    val totalSynced: Int = 0,
    val message: String
)

class DesktopSyncService(
    private val annotationRepository: InMemoryAnnotationRepository,
    private val authManager: DesktopAuthManager,
    private val queueHost: String,
    private val queuePort: Int,
    private val activeComicId: String = "_no_comic_",
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val client = QueueTcpClient(queueHost, queuePort)

    suspend fun sync(): SyncResult {
        if (activeComicId == "_no_comic_" || activeComicId.isBlank()) {
            return SyncResult(success = false, message = "No comic selected — refusing to sync without comic_id")
        }
        val token = authManager.getToken()
            ?: return SyncResult(success = false, message = "Session expired")
        val userId = authManager.getUserId()
            ?: return SyncResult(success = false, message = "Not logged in")

        val unsynced = annotationRepository.getUnsynced()
        if (unsynced.isEmpty()) {
            return SyncResult(success = true, totalSynced = 0, message = "Already up to date")
        }

        val annotationRecords = unsynced.map { ann ->
            AnnotationRecord(
                localId = ann.timestamp,
                imageId = ann.imageId,
                boxIndex = ann.boxIndex,
                boxX = ann.boxX, boxY = ann.boxY,
                boxWidth = ann.boxWidth, boxHeight = ann.boxHeight,
                label = ann.label,
                timestamp = ann.timestamp,
                tapX = ann.tapX, tapY = ann.tapY,
                regionType = ann.regionType.name,
                parentBubbleIndex = ann.parentBubbleIndex,
                tokenIndex = ann.tokenIndex,
                comicId = activeComicId
            )
        }

        val payload = UnifiedPayload(
            schemaVersion = 5,
            exportTimestamp = currentTimeMillis(),
            appVersion = "1.0-desktop",
            deviceId = "desktop-${System.getProperty("user.name", "unknown")}",
            userId = userId,
            mode = "sync",
            tables = UnifiedTables(
                annotationRecords = annotationRecords
            )
        )

        return try {
            val payloadJson = json.encodeToJsonElement(payload)
            val serialized = json.encodeToString(payload)
            if (serialized.toByteArray(Charsets.UTF_8).size <= QueueProtocol.SAFE_CHUNK_BYTES) {
                client.submitIngest(payloadJson, bearerToken = token)
            } else {
                for (ann in annotationRecords) {
                    val chunk = payload.copy(tables = UnifiedTables(annotationRecords = listOf(ann)))
                    client.submitIngest(json.encodeToJsonElement(chunk), bearerToken = token)
                }
            }
            annotationRepository.markSynced(unsynced.map { it.timestamp })
            SyncResult(success = true, totalSynced = unsynced.size, message = "Synced ${unsynced.size} records")
        } catch (e: QueueSubmitException) {
            SyncResult(success = false, message = e.message ?: "Queue submit failed")
        } catch (e: IOException) {
            SyncResult(success = false, message = "Network error: ${e.message}")
        } catch (e: Exception) {
            SyncResult(success = false, message = "Sync failed: ${e.message}")
        }
    }
}
