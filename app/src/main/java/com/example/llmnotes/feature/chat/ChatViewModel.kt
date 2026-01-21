package com.example.llmnotes.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llmnotes.core.ai.LlmEngine
import com.example.llmnotes.domain.model.ChatMessage
import com.example.llmnotes.domain.model.Note
import com.example.llmnotes.domain.model.SourceNote
import com.example.llmnotes.domain.repository.ChatRepository
import com.example.llmnotes.domain.repository.ChatSession
import com.example.llmnotes.domain.repository.NoteRepository
import com.example.llmnotes.domain.usecase.VectorSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val vectorSearchUseCase: VectorSearchUseCase,
    private val llmEngine: LlmEngine,
    private val chatRepository: ChatRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId = _currentSessionId.asStateFlow()

    // Load messages when sessionId changes
    // We maintain a local list for immediate UI updates, but also sync with DB
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    val sessions: StateFlow<List<ChatSession>> = chatRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Context Selection
    private val _selectedNotes = MutableStateFlow<List<Note>>(emptyList())
    val selectedNotes = _selectedNotes.asStateFlow()
    
    val allNotes: StateFlow<List<Note>> = noteRepository.getAllNotes()
         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isGpuEnabled = MutableStateFlow(false)
    val isGpuEnabled: StateFlow<Boolean> = _isGpuEnabled.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking = _isThinking.asStateFlow()

    init {
        _isGpuEnabled.value = llmEngine.isGpuEnabled()
        createNewSession()
    }

    fun createNewSession() {
        _currentSessionId.value = null
        _messages.value = emptyList()
        _selectedNotes.value = emptyList()
    }
    
    fun stopGeneration() {
        viewModelScope.launch {
            llmEngine.stopGeneration()
            _isLoading.value = false
            _isThinking.value = false
        }
    }
    
    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            _currentSessionId.value = sessionId
            chatRepository.getMessagesForSession(sessionId).collect { dbMessages ->
                _messages.value = dbMessages
            }
        }
    }
    
    fun toggleNoteSelection(note: Note) {
        _selectedNotes.update { current ->
            if (current.any { it.id == note.id }) {
                current.filter { it.id != note.id }
            } else {
                current + note
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(content = text, isUser = true)
        
        // Optimistic update
        _messages.update { it + userMsg }
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Ensure session exists
                var sessionId = _currentSessionId.value
                if (sessionId == null) {
                    // Create new session with first message as title
                    val title = text.take(30) + if (text.length > 30) "..." else ""
                    sessionId = chatRepository.createSession(title)
                    _currentSessionId.value = sessionId
                }
                
                // Save user message
                chatRepository.saveMessage(sessionId, userMsg)

                // RAG Logic
                val relevantNotes = if (_selectedNotes.value.isNotEmpty()) {
                    // Use manually selected notes
                    _selectedNotes.value
                } else {
                    // Use Vector Search
                    vectorSearchUseCase(text)
                }

                var context = relevantNotes.joinToString("\n\n") { "Note: ${it.title}\n${it.content}" }
                
                // Build source notes for citations
                val sources = relevantNotes.map { note ->
                    SourceNote(noteId = note.id, noteTitle = note.title)
                }
                
                // Truncate context to avoid exceeding token limit (approx 4 chars per token)
                // Keeping it under ~2500 tokens (10000 chars) to allow space for response
                if (context.length > 10000) {
                     context = context.take(10000) + "\n\n[System: Context truncated due to length]"
                }

                val contextString = if (context.isNotBlank()) "Context:\n$context\n\n" else ""
                
                // Construct the prompt content
                // We don't add "User:" or "Assistant:" prefixes here because the native engine
                // applies the correct chat template (e.g., ChatML) automatically.
                val prompt = if (contextString.isNotBlank()) {
                    "$contextString\n$text"
                } else {
                    text
                }
                
                // Debug log for RAG verification
                println("LLM Prompt Content: $prompt")

                // The system prompt is also handled by the native engine's template mechanism
                
                var currentResponse = ""
                var currentThought = ""
                var isThinking = false
                
                // Initial empty message to be updated
                var aiMsg = ChatMessage(
                    content = "",
                    isUser = false,
                    sourceNotes = sources
                )
                
                // Add placeholder message to UI
                _messages.update { it + aiMsg }

                llmEngine.completionFlow(prompt).collect { token ->
                    var processedToken = token
                    
                    if (token.contains("<think>")) {
                        isThinking = true
                        processedToken = token.replace("<think>", "")
                        _isThinking.value = true
                    }
                    
                    if (token.contains("</think>")) {
                        isThinking = false
                        processedToken = token.replace("</think>", "")
                        _isThinking.value = false
                    }
                    
                    if (isThinking) {
                        currentThought += processedToken
                    } else {
                        currentResponse += processedToken
                    }
                    
                    // Update the last message (AI response) in the UI state
                    _messages.update { current ->
                        if (current.isNotEmpty()) {
                            val last = current.last()
                            if (!last.isUser) {
                                // Create new list with updated last message
                                current.dropLast(1) + last.copy(
                                    content = currentResponse,
                                    thoughtProcess = if (currentThought.isNotEmpty()) currentThought else null
                                )
                            } else {
                                current // Should not happen if we just added it
                            }
                        } else {
                            current
                        }
                    }
                }
                
                // Final save to DB
                chatRepository.saveMessage(sessionId, aiMsg.copy(content = currentResponse, thoughtProcess = if (currentThought.isNotEmpty()) currentThought else null))
                
                // Note: The flow collection in loadSession/init will update UI, 
                // but we also updated optimistically. 
                // Ideally, we depend on the Flow observation. 
                // But since loadSession collects, we don't need to manually update _messages if we are observing.
                // However, for a smoother UX on new session, we might need to handle the state carefully.
                // For now, let's let the Flow update it if we are observing.
                
            } catch (e: Exception) {
                val errorMsg = ChatMessage(content = "Error: ${e.message}", isUser = false)
                _messages.update { it + errorMsg }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
