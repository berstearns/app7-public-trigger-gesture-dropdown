package pl.czak.learnlauncher.data.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.czak.learnlauncher.data.auth.DesktopSettingsStore
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

@Serializable
data class CatalogComic(
    val id: String,
    val name: String,
    val file: String,
    val size_mb: Double,
    val chapters: Int = 0,
    val version: Int = 1
)

@Serializable
private data class CatalogResponse(val comics: List<CatalogComic>)

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val comicId: String, val progress: Int) : DownloadState()
    data class Extracting(val comicId: String) : DownloadState()
    data class Done(val comicId: String) : DownloadState()
    data class Error(val comicId: String, val message: String) : DownloadState()
}

object DesktopComicCatalogApi {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchCatalog(baseUrl: String): List<CatalogComic> = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl/catalog.json")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val code = conn.responseCode
            if (code != 200) throw IOException("Server returned HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val catalog = json.decodeFromString<CatalogResponse>(body)
            catalog.comics
        } finally {
            conn.disconnect()
        }
    }
}

class DesktopComicDownloadManager(private val settingsStore: DesktopSettingsStore) {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state

    val comicsDir: File = File(System.getProperty("user.home"), ".local/share/manga-reader/comics").apply { mkdirs() }

    fun isDownloaded(comicId: String): Boolean =
        settingsStore.getBoolean("downloaded_comic_$comicId", false)

    fun getDownloadedIds(): List<String> {
        val ids = settingsStore.getString("downloaded_comic_ids", null) ?: return emptyList()
        return ids.split(",").filter { it.isNotBlank() }
    }

    private fun addDownloadedId(comicId: String) {
        val current = getDownloadedIds().toMutableSet()
        current.add(comicId)
        settingsStore.putString("downloaded_comic_ids", current.joinToString(","))
        settingsStore.putBoolean("downloaded_comic_$comicId", true)
    }

    private fun removeDownloadedId(comicId: String) {
        val current = getDownloadedIds().toMutableSet()
        current.remove(comicId)
        settingsStore.putString("downloaded_comic_ids", current.joinToString(","))
        settingsStore.putBoolean("downloaded_comic_$comicId", false)
    }

    suspend fun remove(comicId: String): Boolean = withContext(Dispatchers.IO) {
        val destDir = File(comicsDir, comicId)
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        removeDownloadedId(comicId)
        true
    }

    suspend fun download(baseUrl: String, comic: CatalogComic) {
        val comicId = comic.id
        _state.value = DownloadState.Downloading(comicId, 0)
        try {
            val zipFile = downloadZip(baseUrl, comic)
            _state.value = DownloadState.Extracting(comicId)
            extractZip(zipFile, comicId)
            zipFile.delete()
            addDownloadedId(comicId)
            _state.value = DownloadState.Done(comicId)
        } catch (e: Exception) {
            _state.value = DownloadState.Error(comicId, e.message ?: "Unknown error")
        }
    }

    private suspend fun downloadZip(baseUrl: String, comic: CatalogComic): File =
        withContext(Dispatchers.IO) {
            val url = URL("$baseUrl/download/${comic.file}")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000
                val code = conn.responseCode
                if (code != 200) throw IOException("Download failed: HTTP $code")
                val totalBytes = conn.contentLength.toLong()
                comicsDir.mkdirs()
                val zipFile = File(comicsDir, comic.file)
                conn.inputStream.use { input ->
                    zipFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var len: Int
                        while (input.read(buffer).also { len = it } != -1) {
                            output.write(buffer, 0, len)
                            bytesRead += len
                            if (totalBytes > 0) {
                                val progress = ((bytesRead * 100) / totalBytes).toInt()
                                _state.value = DownloadState.Downloading(comic.id, progress)
                            }
                        }
                    }
                }
                zipFile
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun extractZip(zipFile: File, comicId: String) = withContext(Dispatchers.IO) {
        val destDir = File(comicsDir, comicId)
        destDir.mkdirs()
        val topLevelPrefix = detectSingleRootPrefix(zipFile)
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                var name = entry.name
                if (topLevelPrefix != null && name.startsWith(topLevelPrefix)) {
                    name = name.removePrefix(topLevelPrefix)
                    if (name.isEmpty()) { zis.closeEntry(); entry = zis.nextEntry; continue }
                }
                val outFile = File(destDir, name)
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    throw IOException("Zip entry outside target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun detectSingleRootPrefix(zipFile: File): String? {
        var commonRoot: String? = null
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val slashIdx = name.indexOf('/')
                if (slashIdx <= 0) return null
                val root = name.substring(0, slashIdx + 1)
                if (commonRoot == null) commonRoot = root
                else if (commonRoot != root) return null
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return commonRoot
    }
}
