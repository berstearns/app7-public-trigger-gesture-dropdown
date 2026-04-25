package pl.czak.learnlauncher.data.sync

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.czak.learnlauncher.data.AndroidLearnerDataRepository
import pl.czak.learnlauncher.data.auth.AuthManager
import pl.czak.learnlauncher.data.export.buildUnifiedPayload
import pl.czak.learnlauncher.data.model.UnifiedPayload
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class SyncResult(
    val success: Boolean,
    val totalSynced: Int = 0,
    val message: String
)

data class SyncResponse(val accepted: Boolean, val counts: Map<String, Int>?, val error: String?)

/**
 * Transport-agnostic sync API. Implementations: [RemoteSyncApi] (HTTP shim),
 * [pl.czak.learnlauncher.data.sync.tcp.TcpQueueSyncApi] (direct TCP to the
 * simple-tcp-comm job queue).
 */
interface SyncApi {
    suspend fun upload(payload: UnifiedPayload, token: String): SyncResponse
}

class RemoteSyncApi(private val baseUrl: String) : SyncApi {
    private val json = Json { encodeDefaults = true }

    override suspend fun upload(payload: UnifiedPayload, token: String): SyncResponse = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl/sync/upload")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.outputStream.use { os ->
                os.write(json.encodeToString(payload).toByteArray())
            }
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val respJson = org.json.JSONObject(body)
                val countsJson = respJson.optJSONObject("counts")
                val counts = mutableMapOf<String, Int>()
                countsJson?.keys()?.forEach { key -> counts[key] = countsJson.getInt(key) }
                SyncResponse(accepted = true, counts = counts, error = null)
            } else {
                val errorMsg = when (code) {
                    401 -> "Session expired"
                    in 400..499 -> "Request rejected by server"
                    else -> "Server error"
                }
                SyncResponse(accepted = false, counts = null, error = errorMsg)
            }
        } catch (e: IOException) {
            SyncResponse(accepted = false, counts = null, error = "Network error")
        } finally {
            conn.disconnect()
        }
    }
}

class SyncService(
    private val repo: AndroidLearnerDataRepository,
    private val syncApi: SyncApi,
    private val authManager: AuthManager
) {
    suspend fun sync(): SyncResult {
        val token = authManager.getToken()
            ?: return SyncResult(success = false, message = "Session expired")
        val userId = authManager.getUserId()
            ?: return SyncResult(success = false, message = "Not logged in")

        val db = repo.db

        val payload = buildUnifiedPayload(
            db = db,
            deviceId = Build.MODEL,
            appVersion = "1.0-kmp",
            userId = userId,
            unsyncedOnly = true
        )

        val total = with(payload.tables) {
            sessionEvents.size + annotationRecords.size + chatMessages.size +
                pageInteractions.size + appLaunchRecords.size + settingsChanges.size
        }

        if (total == 0) {
            return SyncResult(success = true, totalSynced = 0, message = "Already up to date")
        }

        val response = syncApi.upload(payload, token)

        if (!response.accepted) {
            return SyncResult(success = false, message = response.error ?: "Sync failed")
        }

        // Mark synced
        val tables = payload.tables
        if (tables.sessionEvents.isNotEmpty())
            db.sessionEventDao().markSynced(tables.sessionEvents.map { it.localId })
        if (tables.annotationRecords.isNotEmpty())
            db.annotationRecordDao().markSynced(tables.annotationRecords.map { it.localId })
        if (tables.chatMessages.isNotEmpty())
            db.chatMessageDao().markSynced(tables.chatMessages.map { it.localId })
        if (tables.pageInteractions.isNotEmpty())
            db.pageInteractionDao().markSynced(tables.pageInteractions.map { it.localId })
        if (tables.appLaunchRecords.isNotEmpty())
            db.appLaunchRecordDao().markSynced(tables.appLaunchRecords.map { it.localId })
        if (tables.settingsChanges.isNotEmpty())
            db.settingsChangeDao().markSynced(tables.settingsChanges.map { it.localId })
        // v5 session hierarchy
        if (tables.appSessions.isNotEmpty())
            db.appSessionDao().markSynced(tables.appSessions.map { it.localId })
        if (tables.comicSessions.isNotEmpty())
            db.comicSessionDao().markSynced(tables.comicSessions.map { it.localId })
        if (tables.chapterSessions.isNotEmpty())
            db.chapterSessionDao().markSynced(tables.chapterSessions.map { it.localId })
        if (tables.pageSessions.isNotEmpty())
            db.pageSessionDao().markSynced(tables.pageSessions.map { it.localId })

        return SyncResult(success = true, totalSynced = total, message = "Synced $total records")
    }
}
