package pl.czak.learnlauncher.data.download

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class CatalogComic(
    val id: String,
    val name: String,
    val file: String,
    val sizeMb: Double,
    val chapters: Int,
    val version: Int
)

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val comicId: String, val progress: Int) : DownloadState()
    data class Extracting(val comicId: String) : DownloadState()
    data class Done(val comicId: String) : DownloadState()
    data class Error(val comicId: String, val message: String) : DownloadState()
}

object ComicCatalogApi {
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
            val json = JSONObject(body)
            val arr = json.getJSONArray("comics")
            val result = mutableListOf<CatalogComic>()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                result.add(CatalogComic(
                    id = c.getString("id"),
                    name = c.getString("name"),
                    file = c.getString("file"),
                    sizeMb = c.getDouble("size_mb"),
                    chapters = c.optInt("chapters", 0),
                    version = c.optInt("version", 1)
                ))
            }
            result
        } finally {
            conn.disconnect()
        }
    }
}

class ComicDownloadManager(private val context: Context) {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state

    private val prefs: SharedPreferences =
        context.getSharedPreferences("downloaded_comics", Context.MODE_PRIVATE)

    fun isDownloaded(comicId: String): Boolean = prefs.getBoolean(comicId, false)

    suspend fun remove(comicId: String): Boolean = withContext(Dispatchers.IO) {
        val comicsDir = context.getExternalFilesDir("comics") ?: return@withContext false
        val destDir = File(comicsDir, comicId)
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        prefs.edit().remove(comicId).apply()
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
            prefs.edit().putBoolean(comicId, true).apply()
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
                val comicsDir = context.getExternalFilesDir("comics")
                    ?: throw IOException("External storage not available")
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
        val comicsDir = context.getExternalFilesDir("comics")
            ?: throw IOException("External storage not available")
        val destDir = File(comicsDir, comicId)
        destDir.mkdirs()
        val topLevelPrefix = detectSingleRootPrefix(zipFile)
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                var name = entry.name
                if (topLevelPrefix != null && name.startsWith(topLevelPrefix)) {
                    name = name.removePrefix(topLevelPrefix)
                    if (name.isEmpty()) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
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
