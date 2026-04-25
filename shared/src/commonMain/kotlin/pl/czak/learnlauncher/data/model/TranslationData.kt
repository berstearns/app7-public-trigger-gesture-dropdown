package pl.czak.learnlauncher.data.model

data class RegionTranslation(
    val id: String,
    val imageId: String,
    val bubbleIndex: Int,
    val originalText: String,
    val meaningTranslation: String,
    val literalTranslation: String,
    val sourceLanguage: String = "ja",
    val targetLanguage: String = "en"
)

data class DownloadableComic(
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
