package pl.czak.learnlauncher.data.source

import com.google.gson.Gson
import pl.czak.learnlauncher.data.model.*
import pl.czak.learnlauncher.domain.model.ConversationBox
import pl.czak.learnlauncher.domain.model.ConversationManifest
import pl.czak.learnlauncher.domain.model.RegionType
import pl.czak.learnlauncher.domain.model.TokenRegionsManifest
import java.io.File

class FileImageDataSource(
    private val comicDir: File
) : ImageDataSource {

    private val gson = Gson()
    private var cachedChapterManifest: ChapterManifest? = null
    private val cachedImageManifests = mutableMapOf<String, ImageManifest>()
    private val cachedConversationManifests = mutableMapOf<String, ConversationManifest>()
    private val tokenCache: Map<String, Map<Int, List<ConversationBox>>> by lazy { loadTokenRegions() }

    private fun readJson(path: String): String = File(comicDir, path).readText()

    private fun loadChapterManifest(): ChapterManifest? {
        cachedChapterManifest?.let { return it }
        return try {
            val json = readJson("manifest.json")
            val manifest = gson.fromJson(json, ChapterManifest::class.java)
            cachedChapterManifest = manifest
            manifest
        } catch (e: Exception) { null }
    }

    private fun loadImageManifestForChapter(chapterPath: String): ImageManifest {
        cachedImageManifests[chapterPath]?.let { return it }
        return try {
            val json = readJson("$chapterPath/images.json")
            val manifest = gson.fromJson(json, ImageManifest::class.java)
            cachedImageManifests[chapterPath] = manifest
            manifest
        } catch (e: Exception) { ImageManifest(version = 1, images = emptyList()) }
    }

    private fun loadConversationManifestForChapter(chapterPath: String): ConversationManifest {
        cachedConversationManifests[chapterPath]?.let { return it }
        return try {
            // Prefer OCR-enriched > sorted > base
            val text = tryReadJson(
                "$chapterPath/conversations_ocr.json",
                "$chapterPath/conversations_sorted.json",
                "$chapterPath/conversations.json",
            )
            val manifest = gson.fromJson(text, ConversationManifest::class.java)
            cachedConversationManifests[chapterPath] = manifest
            manifest
        } catch (e: Exception) { ConversationManifest(version = 1, conversations = emptyMap()) }
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
            loadLegacyImageManifest().images.map { json -> ImageItem.fromJson(json = json) }
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
            val json = readJson("images.json")
            val manifest = gson.fromJson(json, ImageManifest::class.java)
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
            val manifest = gson.fromJson(text, ConversationManifest::class.java)
            cachedConversationManifests[""] = manifest
            manifest
        } catch (e: Exception) { ConversationManifest(version = 1, conversations = emptyMap()) }
    }

    private fun loadTokenRegions(): Map<String, Map<Int, List<ConversationBox>>> {
        val result = mutableMapOf<String, MutableMap<Int, List<ConversationBox>>>()
        try {
            val json = readJson("token_regions.json")
            val manifest = gson.fromJson(json, TokenRegionsManifest::class.java) ?: return emptyMap()
            manifest.tokenRegions.forEach { entry ->
                val bubbleMap = result.getOrPut(entry.imageId) { mutableMapOf() }
                bubbleMap[entry.bubbleIndex] = entry.tokens.map { token ->
                    ConversationBox(
                        x = token.x, y = token.y,
                        width = token.width, height = token.height,
                        regionType = RegionType.TOKEN,
                        parentBubbleIndex = entry.bubbleIndex,
                        tokenIndex = token.tokenIndex,
                        text = token.text
                    )
                }
            }
        } catch (_: Exception) { }
        return result
    }

    override fun getTokensForBubble(imageId: String, bubbleIndex: Int): List<ConversationBox> =
        tokenCache[imageId]?.get(bubbleIndex) ?: emptyList()
}
