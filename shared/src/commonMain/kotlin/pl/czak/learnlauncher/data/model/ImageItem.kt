package pl.czak.learnlauncher.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ImageItemJson(
    val id: String,
    val filename: String,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val difficulty: String? = null
)

@Serializable
data class ImageManifest(
    val version: Int,
    val images: List<ImageItemJson>
)

@Serializable
data class ChapterManifest(
    val version: Int,
    val series: String,
    val chapters: List<ChapterEntry>
)

@Serializable
data class ChapterEntry(
    val name: String,
    val path: String,
    val image_count: Int,
    val region_count: Int
)

data class ImageItem(
    val id: String,
    val filename: String,
    val title: String,
    val chapter: String = "",
    val description: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val difficulty: String? = null,
    val lastShown: Long = 0,
    val timesShown: Int = 0,
    val timesCorrect: Int = 0
) {
    companion object {
        fun fromJson(
            json: ImageItemJson,
            chapter: String = "",
            lastShown: Long = 0,
            timesShown: Int = 0,
            timesCorrect: Int = 0
        ): ImageItem = ImageItem(
            id = json.id,
            filename = json.filename,
            title = json.title,
            chapter = chapter,
            description = json.description,
            category = json.category,
            tags = json.tags,
            difficulty = json.difficulty,
            lastShown = lastShown,
            timesShown = timesShown,
            timesCorrect = timesCorrect
        )
    }
}
