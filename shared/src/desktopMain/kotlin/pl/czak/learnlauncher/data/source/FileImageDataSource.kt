package pl.czak.learnlauncher.data.source

import kotlinx.serialization.json.Json
import pl.czak.learnlauncher.data.model.*
import pl.czak.learnlauncher.domain.model.ConversationBox
import pl.czak.learnlauncher.domain.model.ConversationManifest
import java.io.File

class FileImageDataSource(
    private val comicDir: File
) : ImageDataSource {

    private val json = Json { ignoreUnknownKeys = true }
    private var cachedChapterManifest: ChapterManifest? = null
    private val cachedImageManifests = mutableMapOf<String, ImageManifest>()
    private val cachedConversationManifests = mutableMapOf<String, ConversationManifest>()

    private fun readJson(path: String): String = File(comicDir, path).readText()

    private fun loadChapterManifest(): ChapterManifest? {
        cachedChapterManifest?.let { return it }
        return try {
            val text = readJson("manifest.json")
            val manifest = json.decodeFromString<ChapterManifest>(text)
            cachedChapterManifest = manifest
            manifest
        } catch (e: Exception) { null }
    }

    private fun loadImageManifestForChapter(chapterPath: String): ImageManifest {
        cachedImageManifests[chapterPath]?.let { return it }
        return try {
            val text = readJson("$chapterPath/images.json")
            val manifest = json.decodeFromString<ImageManifest>(text)
            cachedImageManifests[chapterPath] = manifest
            manifest
        } catch (e: Exception) { ImageManifest(version = 1, images = emptyList()) }
    }

    private fun loadConversationManifestForChapter(chapterPath: String): ConversationManifest {
        cachedConversationManifests[chapterPath]?.let { return it }
        return try {
            val text = tryReadJson(
                "$chapterPath/conversations_ocr.json",
                "$chapterPath/conversations_sorted.json",
                "$chapterPath/conversations.json",
            )
            val manifest = json.decodeFromString<ConversationManifest>(text)
            cachedConversationManifests[chapterPath] = manifest
            manifest
        } catch (e: Exception) { ConversationManifest(version = 1, conversations = emptyMap()) }
    }

    private fun tryReadJson(vararg paths: String): String {
        for (path in paths) {
            try {
                val text = readJson(path)
                println("[FileImageDataSource] Loaded: $path (${text.length} chars)")
                return text
            } catch (_: Exception) {}
        }
        throw java.io.FileNotFoundException("None found: ${paths.toList()}")
    }

    override fun getImageItems(): List<ImageItem> {
        val chapterManifest = loadChapterManifest()
        return if (chapterManifest != null) {
            chapterManifest.chapters.flatMap { chapter ->
                val imageManifest = loadImageManifestForChapter(chapter.path)
                imageManifest.images.map { imgJson ->
                    val qualifiedId = "${chapter.path}/${imgJson.id}"
                    ImageItem.fromJson(json = imgJson, chapter = chapter.path).copy(id = qualifiedId)
                }
            }
        } else {
            loadLegacyImageManifest().images.map { imgJson -> ImageItem.fromJson(json = imgJson) }
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
            val path = if (chapter.isNotEmpty()) "$chapter/images/$filename" else "images/$filename"
            File(comicDir, path).readBytes()
        } catch (e: Exception) { null }
    }

    private fun loadLegacyImageManifest(): ImageManifest {
        cachedImageManifests[""]?.let { return it }
        return try {
            val text = readJson("images.json")
            val manifest = json.decodeFromString<ImageManifest>(text)
            cachedImageManifests[""] = manifest
            manifest
        } catch (e: Exception) { ImageManifest(version = 1, images = emptyList()) }
    }

    private fun loadLegacyConversationManifest(): ConversationManifest {
        cachedConversationManifests[""]?.let { return it }
        return try {
            val text = tryReadJson(
                "conversations_ocr.json",
                "conversations_sorted.json",
                "conversations.json",
            )
            val manifest = json.decodeFromString<ConversationManifest>(text)
            cachedConversationManifests[""] = manifest
            manifest
        } catch (e: Exception) { ConversationManifest(version = 1, conversations = emptyMap()) }
    }
}
