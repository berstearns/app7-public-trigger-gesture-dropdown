package pl.czak.learnlauncher.domain.model

sealed class DoubleTapAction {
    data class AnnotateBubble(
        val nx: Float,
        val ny: Float,
        val box: ConversationBox,
        val boxIndex: Int
    ) : DoubleTapAction()

    data object MarkUnderstoodAndAdvance : DoubleTapAction()
    data object GoToPrevious : DoubleTapAction()

    data class AnnotateToken(
        val nx: Float,
        val ny: Float,
        val token: ConversationBox,
        val parentBubbleIndex: Int,
        val tokenIndex: Int
    ) : DoubleTapAction()
}
