package pl.czak.learnlauncher.data.source

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.Json
import pl.czak.learnlauncher.data.model.*
import pl.czak.learnlauncher.domain.model.ConversationBox
import pl.czak.learnlauncher.domain.model.ConversationManifest
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

class BundleImageDataSource : ImageDataSource {

    private val json = Json { ignoreUnknownKeys = true }
    private var cachedChapterManifest: ChapterManifest? = null
    private val cachedImageManifests = mutableMapOf<String, ImageManifest>()
    private val cachedConversationManifests = mutableMapOf<String, ConversationManifest>()

    @OptIn(ExperimentalForeignApi::class)
    private fun readJson(path: String): String? {
        val bundlePath = NSBundle.mainBundle.pathForResource(path.removeSuffix(".json"), "json") ?: return null
        return NSString.stringWithContentsOfFile(bundlePath, NSUTF8StringEncoding, null)
    }

    private fun loadChapterManifest(): ChapterManifest? {
        cachedChapterManifest?.let { return it }
        return try {
            val text = readJson("manifest.json") ?: return null
            val manifest = json.decodeFromString<ChapterManifest>(text)
            cachedChapterManifest = manifest
            manifest
        } catch (e: Exception) {
            null
        }
    }

    override fun getImageItems(): List<ImageItem> {
        val chapterManifest = loadChapterManifest()
        return if (chapterManifest != null) {
            chapterManifest.chapters.flatMap { chapter ->
                val text = readJson("${chapter.path}/images.json") ?: return@flatMap emptyList()
                val imageManifest = json.decodeFromString<ImageManifest>(text)
                imageManifest.images.map { imgJson ->
                    val qualifiedId = "${chapter.path}/${imgJson.id}"
                    ImageItem.fromJson(json = imgJson, chapter = chapter.path).copy(id = qualifiedId)
                }
            }
        } else {
            emptyList()
        }
    }

    override fun getConversationBoxes(imageId: String): List<ConversationBox> {
        return emptyList()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun loadImageBytes(filename: String, chapter: String): ByteArray? {
        val name = filename.substringBeforeLast(".")
        val ext = filename.substringAfterLast(".", "")
        val path = if (chapter.isNotEmpty()) {
            NSBundle.mainBundle.pathForResource("$chapter/$name", ext)
        } else {
            NSBundle.mainBundle.pathForResource(name, ext)
        } ?: return null
        return try {
            val data = NSData.dataWithContentsOfFile(path) ?: return null
            val length = data.length.toInt()
            if (length == 0) return ByteArray(0)
            ByteArray(length).also { bytes ->
                bytes.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), data.bytes, data.length)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
