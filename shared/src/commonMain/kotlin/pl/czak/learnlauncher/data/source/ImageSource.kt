package pl.czak.learnlauncher.data.source

import pl.czak.learnlauncher.data.model.ImageItem
import pl.czak.learnlauncher.domain.model.ConversationBox

interface ImageDataSource {
    fun getImageItems(): List<ImageItem>
    fun getConversationBoxes(imageId: String): List<ConversationBox>
    fun loadImageBytes(filename: String, chapter: String = ""): ByteArray?
    fun getTokensForBubble(imageId: String, bubbleIndex: Int): List<ConversationBox> = emptyList()
}
