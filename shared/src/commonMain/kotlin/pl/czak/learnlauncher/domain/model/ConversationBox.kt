package pl.czak.learnlauncher.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OcrToken(
    val text: String,
    val confidence: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
)

@Serializable
data class ConversationBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val regionType: RegionType = RegionType.BUBBLE,
    val parentBubbleIndex: Int? = null,
    val tokenIndex: Int? = null,
    val text: String? = null,
    val tokens: List<OcrToken>? = null,
    val ocrConfidence: Float? = null,
) {
    fun contains(nx: Float, ny: Float): Boolean =
        nx >= x && nx <= x + width && ny >= y && ny <= y + height

    fun createToken(
        relX: Float,
        relY: Float,
        relWidth: Float,
        relHeight: Float,
        bubbleIndex: Int,
        tokenIdx: Int,
        tokenText: String? = null
    ): ConversationBox {
        return ConversationBox(
            x = relX,
            y = relY,
            width = relWidth,
            height = relHeight,
            regionType = RegionType.TOKEN,
            parentBubbleIndex = bubbleIndex,
            tokenIndex = tokenIdx,
            text = tokenText
        )
    }

    /** Convert embedded OCR tokens to ConversationBox token regions for display.
     *  Token coords in JSON are page-normalized, but zoom mode needs them
     *  relative to the bubble (0..1 within the bubble's own bbox). */
    fun ocrTokenRegions(bubbleIndex: Int): List<ConversationBox> {
        return tokens?.mapIndexed { idx, token ->
            // Convert page-normalized → bubble-relative
            val relX = if (width > 0f) (token.x - x) / width else 0f
            val relY = if (height > 0f) (token.y - y) / height else 0f
            val relW = if (width > 0f) token.width / width else 0f
            val relH = if (height > 0f) token.height / height else 0f
            ConversationBox(
                x = relX.coerceIn(0f, 1f),
                y = relY.coerceIn(0f, 1f),
                width = relW.coerceIn(0f, 1f - relX.coerceIn(0f, 1f)),
                height = relH.coerceIn(0f, 1f - relY.coerceIn(0f, 1f)),
                regionType = RegionType.TOKEN,
                parentBubbleIndex = bubbleIndex,
                tokenIndex = idx,
                text = token.text,
            )
        } ?: emptyList()
    }
}

@Serializable
data class ConversationManifest(
    val version: Int,
    val conversations: Map<String, List<ConversationBox>>
)

data class TokenRegionEntry(
    val imageId: String,
    val bubbleIndex: Int,
    val tokens: List<TokenData>
)

data class TokenData(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val tokenIndex: Int,
    val text: String? = null
)

data class TokenRegionsManifest(
    val tokenRegions: List<TokenRegionEntry>
)
