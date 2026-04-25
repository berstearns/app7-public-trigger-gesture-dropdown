package pl.czak.learnlauncher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.czak.learnlauncher.currentTimeMillis
import pl.czak.learnlauncher.data.repository.ChatRepository
import pl.czak.learnlauncher.domain.model.ChatMessage

class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val botReply = "I'm a simple bot. I don't understand anything yet, but I'm here to listen!"

    init {
        viewModelScope.launch {
            _messages.value = chatRepository.getAll()
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage("user", text.trim(), currentTimeMillis())
        val botMsg = ChatMessage("bot", botReply, currentTimeMillis())

        _messages.value = _messages.value + userMsg + botMsg

        viewModelScope.launch {
            chatRepository.save(userMsg)
            chatRepository.save(botMsg)
        }
    }
}
