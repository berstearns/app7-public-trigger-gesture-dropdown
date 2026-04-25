package pl.czak.learnlauncher.data.source

import android.content.Context
import com.google.gson.Gson
import pl.czak.learnlauncher.data.model.*
import pl.czak.learnlauncher.domain.model.ConversationBox
import pl.czak.learnlauncher.domain.model.ConversationManifest
import java.io.File

class AssetsImageDataSource(
    private val context: Context,
    private val comicDir: File? = null
) : ImageDataSource {

    private val gson = Gson()
    private var cachedChapterManifest: ChapterManifest? = null
    private val cachedImageManifests = mutableMapOf<String, ImageManifest>()
    private val cachedConversationManifests = mutableMapOf<String, ConversationManifest>()

    private fun readJson(path: String): String {
        return if (comicDir != null) {
            File(comicDir, path).readText()
        } else {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }
    }

    private fun loadChapterManifest(): ChapterManifest? {
        cachedChapterManifest?.let { return it }
        return try {
            val json = readJson("manifest.json")
            val manifest = gson.fromJson(json, ChapterManifest::class.java)
            cachedChapterManifest = manifest
            manifest
        } catch (e: Exception) {
            null
        }
    }

    private fun loadImageManifestForChapter(chapterPath: String): ImageManifest {
        cachedImageManifests[chapterPath]?.let { return it }
        return try {
            val json = readJson("$chapterPath/images.json")
            val manifest = gson.fromJson(json, ImageManifest::class.java)
            cachedImageManifests[chapterPath] = manifest
            manifest
        } catch (e: Exception) {
            ImageManifest(version = 1, images = emptyList())
        }
    }

    private fun loadConversationManifestForChapter(chapterPath: String): ConversationManifest {
        cachedConversationManifests[chapterPath]?.let { return it }
        return try {
            val json = tryReadJson(
                "$chapterPath/conversations_ocr.json",
                "$chapterPath/conversations_sorted.json",
                "$chapterPath/conversations.json",
            )
            val manifest = gson.fromJson(json, ConversationManifest::class.java)
            cachedConversationManifests[chapterPath] = manifest
            manifest
        } catch (e: Exception) {
            ConversationManifest(version = 1, conversations = emptyMap())
        }
    }

    private fun tryReadJson(vararg paths: String): String {
        for (path in paths) {
            try { return readJson(path) } catch (_: Exception) {}
        }
        throw java.io.FileNotFoundException("None found: ${paths.toList()}")
    }

    override fun getImageItems(): List<ImageItem> {
        val chapterManifest = loadChapterManifest()
        return if (chapterManifest != null) {
            chapterManifest.chapters.flatMap { chapter ->
                val imageManifest = loadImageManifestForChapter(chapter.path)
                imageManifest.images.map { json ->
                    val qualifiedId = "${chapter.path}/${json.id}"
                    ImageItem.fromJson(json = json, chapter = chapter.path).copy(id = qualifiedId)
                }
            }
        } else {
            loadLegacyImageManifest().images.map { json ->
                ImageItem.fromJson(json = json)
            }
        }
    }

    override fun getConversationBoxes(imageId: String): List<ConversationBox> {
        val slashIdx = imageId.indexOf('/')
        if (slashIdx > 0) {
            val chapterPath = imageId.substring(0, slashIdx)
            val localId = imageId.substring(slashIdx + 1)
            return loadConversationManifestForChapter(chapterPath).conversations[localId] ?: emptyList()
        }
        return loadLegacyConversationManifest().conversations[imageId] ?: emptyList()
    }

    override fun loadImageBytes(filename: String, chapter: String): ByteArray? {
        return try {
            val path = if (chapter.isNotEmpty()) "$chapter/$filename" else filename
            if (comicDir != null) {
                File(comicDir, path).readBytes()
            } else {
                context.assets.open(path).use { it.readBytes() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadLegacyImageManifest(): ImageManifest {
        cachedImageManifests[""]?.let { return it }
        return try {
            val json = readJson("images.json")
            val manifest = gson.fromJson(json, ImageManifest::class.java)
            cachedImageManifests[""] = manifest
            manifest
        } catch (e: Exception) {
            ImageManifest(version = 1, images = emptyList())
        }
    }

    private fun loadLegacyConversationManifest(): ConversationManifest {
        cachedConversationManifests[""]?.let { return it }
        return try {
            val json = readJson("conversations.json")
            val manifest = gson.fromJson(json, ConversationManifest::class.java)
            cachedConversationManifests[""] = manifest
            manifest
        } catch (e: Exception) {
            ConversationManifest(version = 1, conversations = emptyMap())
        }
    }
}
