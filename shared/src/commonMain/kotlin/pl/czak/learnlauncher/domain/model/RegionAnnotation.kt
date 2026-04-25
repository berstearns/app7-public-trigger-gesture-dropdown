package pl.czak.learnlauncher.domain.model

data class RegionAnnotation(
    val imageId: String,
    val boxIndex: Int,
    val boxX: Float,
    val boxY: Float,
    val boxWidth: Float,
    val boxHeight: Float,
    val label: String,
    val timestamp: Long,
    val tapX: Float,
    val tapY: Float,
    val regionType: RegionType = RegionType.BUBBLE,
    val parentBubbleIndex: Int? = null,
    val tokenIndex: Int? = null
)
