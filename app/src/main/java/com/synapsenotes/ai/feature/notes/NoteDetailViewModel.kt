package com.synapsenotes.ai.feature.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapsenotes.ai.core.ai.LlmEngine
import com.synapsenotes.ai.domain.model.Note
import com.synapsenotes.ai.domain.repository.NoteRepository
import com.synapsenotes.ai.domain.usecase.DeleteNoteUseCase
import com.synapsenotes.ai.domain.usecase.SaveNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.synapsenotes.ai.core.preferences.AppPreferences

enum class AiAction {
    AUTO_COMPLETE,
    SUMMARIZE,
    REWRITE,
    BULLET_POINTS
}

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val repository: NoteRepository, // Direct access for getById for simplicity, or use UseCase
    private val llmEngine: LlmEngine,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: String? = savedStateHandle["noteId"]

    private val _uiState = MutableStateFlow(NoteDetailUiState())
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    init {
        if (noteId != null && noteId != "new") {
            loadNote(noteId)
        }
    }

    private fun loadNote(id: String) {
        viewModelScope.launch {
            val note = repository.getNoteById(id)
            if (note != null) {
                val lastSync = appPreferences.lastSyncTimestamp
                val isSynced = note.updatedAt <= lastSync
                
                _uiState.value = _uiState.value.copy(
                    id = note.id,
                    title = note.title,
                    content = note.content,
                    isSynced = isSynced
                )
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        _uiState.value = _uiState.value.copy(title = newTitle)
    }

    fun onContentChange(newContent: String) {
        _uiState.value = _uiState.value.copy(content = newContent)
    }

    fun saveNote() {
        viewModelScope.launch {
            val current = _uiState.value
            val note = Note(
                id = current.id ?: UUID.randomUUID().toString(),
                title = current.title,
                content = current.content,
                createdAt = System.currentTimeMillis(), // Should preserve if existing, but simplifying
                updatedAt = System.currentTimeMillis(),
                tags = emptyList(),
                embedding = null
            )
            saveNoteUseCase(note)
        }
    }

    fun deleteNote() {
        val id = _uiState.value.id ?: return
        viewModelScope.launch {
            deleteNoteUseCase(id)
        }
    }

    fun generateCompletion() {
        performAiAction(AiAction.AUTO_COMPLETE)
    }

    fun performAiAction(action: AiAction) {
        viewModelScope.launch {
            val currentContent = _uiState.value.content
            if (currentContent.isBlank()) return@launch

            _uiState.value = _uiState.value.copy(isGenerating = true)
            
            try {
                val prompt = when(action) {
                    AiAction.SUMMARIZE -> "Summarize the following text:\n$currentContent"
                    AiAction.REWRITE -> "Rewrite the following text to be more clear and concise:\n$currentContent"
                    AiAction.BULLET_POINTS -> "Convert the following text into a bulleted list:\n$currentContent"
                    AiAction.AUTO_COMPLETE -> "Continue this text:\n$currentContent"
                }
                
                val result = llmEngine.completion(prompt)
                
                val newContent = if (action == AiAction.AUTO_COMPLETE) {
                    currentContent + result
                } else {
                    currentContent + "\n\n--- AI ${action.name} ---\n" + result
                }
                
                _uiState.value = _uiState.value.copy(
                    content = newContent,
                    isGenerating = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isGenerating = false)
            }
        }
    }
}

data class NoteDetailUiState(
    val id: String? = null,
    val title: String = "",
    val content: String = "",
    val isGenerating: Boolean = false,
    val isSynced: Boolean = false
)
