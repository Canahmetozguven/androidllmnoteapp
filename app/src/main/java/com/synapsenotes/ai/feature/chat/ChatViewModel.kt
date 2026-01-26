package com.synapsenotes.ai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapsenotes.ai.core.ai.HardwareInfo
import com.synapsenotes.ai.core.ai.LlmEngine
import com.synapsenotes.ai.domain.model.ChatMessage
import com.synapsenotes.ai.domain.model.Note
import com.synapsenotes.ai.domain.model.SourceNote
import com.synapsenotes.ai.domain.repository.ChatRepository
import com.synapsenotes.ai.domain.repository.ChatSession
import com.synapsenotes.ai.domain.repository.NoteRepository
import com.synapsenotes.ai.domain.usecase.VectorSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    val sessions: StateFlow<List<ChatSession>> = chatRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedNotes = MutableStateFlow<List<Note>>(emptyList())
    val selectedNotes = _selectedNotes.asStateFlow()
    
    val allNotes: StateFlow<List<Note>> = noteRepository.getAllNotes()
         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hardwareInfo = MutableStateFlow(HardwareInfo(false, "CPU"))
    val hardwareInfo: StateFlow<HardwareInfo> = _hardwareInfo.asStateFlow()

    private val _isGpuEnabled = MutableStateFlow(false)
    val isGpuEnabled: StateFlow<Boolean> = _isGpuEnabled.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking = _isThinking.asStateFlow()

    init {
        _hardwareInfo.value = llmEngine.getHardwareInfo()
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
        
        _messages.update { it + userMsg }
        _isLoading.value = true

        viewModelScope.launch {
            try {
                var sessionId = _currentSessionId.value
                if (sessionId == null) {
                    val title = text.take(30) + if (text.length > 30) "..." else ""
                    sessionId = chatRepository.createSession(title)
                    _currentSessionId.value = sessionId
                }
                
                chatRepository.saveMessage(sessionId, userMsg)

                val relevantNotes = if (_selectedNotes.value.isNotEmpty()) {
                    _selectedNotes.value
                } else {
                    vectorSearchUseCase(text)
                }

                var context = relevantNotes.joinToString("\n\n") { "Note: ${it.title}\n${it.content}" }
                val sources = relevantNotes.map { note ->
                    SourceNote(noteId = note.id, noteTitle = note.title)
                }
                
                if (context.length > 10000) {
                     context = context.take(10000) + "\n\n[System: Context truncated due to length]"
                }

                val contextString = if (context.isNotBlank()) "Context:\n$context\n\n" else ""
                val prompt = if (contextString.isNotBlank()) {
                    "$contextString\n$text"
                } else {
                    text
                }
                
                var currentResponse = ""
                var currentThought = ""
                var isThinking = false
                
                val aiMsg = ChatMessage(
                    content = "",
                    isUser = false,
                    sourceNotes = sources
                )
                
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
                    
                    _messages.update { current ->
                        if (current.isNotEmpty()) {
                            val last = current.last()
                            if (!last.isUser) {
                                current.dropLast(1) + last.copy(
                                    content = currentResponse,
                                    thoughtProcess = if (currentThought.isNotEmpty()) currentThought else null
                                )
                            } else {
                                current
                            }
                        } else {
                            current
                        }
                    }
                }
                
                chatRepository.saveMessage(sessionId, aiMsg.copy(content = currentResponse, thoughtProcess = if (currentThought.isNotEmpty()) currentThought else null))
                
            } catch (e: Exception) {
                val errorMsg = ChatMessage(content = "Error: ${e.message}", isUser = false)
                _messages.update { it + errorMsg }
            } finally {
                _isLoading.value = false
            }
        }
    }
}