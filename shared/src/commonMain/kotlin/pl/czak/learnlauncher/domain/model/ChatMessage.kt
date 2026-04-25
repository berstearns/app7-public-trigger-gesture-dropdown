package pl.czak.learnlauncher.domain.model

data class ChatMessage(
    val sender: String,
    val text: String,
    val timestamp: Long
)
