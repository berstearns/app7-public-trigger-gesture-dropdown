package pl.czak.learnlauncher.domain.navigation

import pl.czak.learnlauncher.data.model.ImageItem

class ImageSequenceNavigator {

    private var images: List<ImageItem> = emptyList()
    private var index = -1

    private var chapterNames: List<String> = emptyList()
    private var chapterStartIndices: List<Int> = emptyList()

    fun initialize(allImages: List<ImageItem>) {
        images = allImages.sortedWith(compareBy({ it.chapter }, { it.filename }))
        index = -1
        buildChapterIndex()
    }

    private fun buildChapterIndex() {
        val names = mutableListOf<String>()
        val starts = mutableListOf<Int>()
        var lastChapter = ""
        for ((i, img) in images.withIndex()) {
            if (img.chapter != lastChapter) {
                names.add(img.chapter)
                starts.add(i)
                lastChapter = img.chapter
            }
        }
        chapterNames = names
        chapterStartIndices = starts
    }

    fun next(): ImageItem? {
        if (images.isEmpty()) return null
        if (index < images.size - 1) {
            index++
            return images[index]
        }
        return null
    }

    fun previous(): ImageItem? {
        if (images.isEmpty()) return null
        if (index > 0) {
            index--
            return images[index]
        }
        return null
    }

    fun current(): ImageItem? {
        if (images.isEmpty() || index < 0 || index >= images.size) return null
        return images[index]
    }

    fun nextChapter(): ImageItem? {
        if (images.isEmpty() || chapterStartIndices.isEmpty()) return null
        val ci = currentChapterIndex()
        if (ci < chapterStartIndices.size - 1) {
            index = chapterStartIndices[ci + 1]
            return images[index]
        }
        return null
    }

    fun previousChapter(): ImageItem? {
        if (images.isEmpty() || chapterStartIndices.isEmpty()) return null
        val ci = currentChapterIndex()
        if (ci > 0) {
            index = chapterStartIndices[ci - 1]
            return images[index]
        }
        return null
    }

    fun goToChapter(chapterIdx: Int): ImageItem? {
        if (chapterIdx < 0 || chapterIdx >= chapterStartIndices.size) return null
        index = chapterStartIndices[chapterIdx]
        return images[index]
    }

    val chapterCount: Int
        get() = chapterNames.size

    fun getChapterNames(): List<String> = chapterNames

    val canGoForward: Boolean
        get() = images.isNotEmpty() && index < images.size - 1

    val canGoBack: Boolean
        get() = index > 0

    val canGoNextChapter: Boolean
        get() = chapterStartIndices.isNotEmpty() && currentChapterIndex() < chapterStartIndices.size - 1

    val canGoPreviousChapter: Boolean
        get() = currentChapterIndex() > 0

    val currentChapterName: String
        get() {
            if (chapterNames.isEmpty() || index < 0) return ""
            return chapterNames[currentChapterIndex()]
        }

    val position: Int
        get() = index

    val size: Int
        get() = images.size

    fun restorePosition(savedIndex: Int): ImageItem? {
        if (images.isEmpty() || savedIndex < 0 || savedIndex >= images.size) return null
        index = savedIndex
        return images[index]
    }

    fun goToImageId(imageId: String): ImageItem? {
        val idx = images.indexOfFirst { it.id == imageId }
        if (idx < 0) return null
        index = idx
        return images[index]
    }

    private fun currentChapterIndex(): Int {
        if (index < 0 || chapterStartIndices.isEmpty()) return 0
        var lo = 0
        var hi = chapterStartIndices.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (chapterStartIndices[mid] <= index) lo = mid else hi = mid - 1
        }
        return lo
    }
}
