package pl.czak.learnlauncher.data.export

import android.os.Build
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.czak.learnlauncher.data.AndroidLearnerDataRepository
import pl.czak.learnlauncher.data.model.UnifiedPayload

class JsonExportService(
    private val repo: AndroidLearnerDataRepository
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun exportToJsonString(): String {
        val payload: UnifiedPayload = buildUnifiedPayload(
            db = repo.db,
            deviceId = Build.MODEL,
            appVersion = "1.0-kmp",
            unsyncedOnly = false
        )
        return json.encodeToString(payload)
    }
}
