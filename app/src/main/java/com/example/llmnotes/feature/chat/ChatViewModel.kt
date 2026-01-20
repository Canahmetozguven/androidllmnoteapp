package com.example.llmnotes.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llmnotes.core.ai.LlmEngine
import com.example.llmnotes.domain.usecase.VectorSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val vectorSearchUseCase: VectorSearchUseCase,
    private val llmEngine: LlmEngine
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isGpuEnabled = MutableStateFlow(false)
    val isGpuEnabled: StateFlow<Boolean> = _isGpuEnabled.asStateFlow()

    init {
        _isGpuEnabled.value = llmEngine.isGpuEnabled()
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(content = text, isUser = true)
        _messages.update { it + userMsg }
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // RAG: Find relevant notes
                val relevantNotes = vectorSearchUseCase(text)
                val context = relevantNotes.joinToString("\n\n") { "Note: ${it.title}\n${it.content}" }
                
                val contextString = if (context.isNotBlank()) "Context:\n$context\n\n" else ""
                val prompt = "${contextString}Question: $text"

                val response = llmEngine.completion(prompt)
                
                val aiMsg = ChatMessage(content = response, isUser = false)
                _messages.update { it + aiMsg }
            } catch (e: Exception) {
                val errorMsg = ChatMessage(content = "Error: ${e.message}", isUser = false)
                _messages.update { it + errorMsg }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
