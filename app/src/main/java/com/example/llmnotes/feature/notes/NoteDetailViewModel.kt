package com.example.llmnotes.feature.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llmnotes.core.ai.LlmEngine
import com.example.llmnotes.domain.model.Note
import com.example.llmnotes.domain.repository.NoteRepository
import com.example.llmnotes.domain.usecase.SaveNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val saveNoteUseCase: SaveNoteUseCase,
    private val repository: NoteRepository, // Direct access for getById for simplicity, or use UseCase
    private val llmEngine: LlmEngine,
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
                _uiState.value = _uiState.value.copy(
                    id = note.id,
                    title = note.title,
                    content = note.content
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

    fun generateCompletion() {
        viewModelScope.launch {
            val currentContent = _uiState.value.content
            _uiState.value = _uiState.value.copy(isGenerating = true)
            
            try {
                // Mock prompt construction
                val prompt = "Continue this text: $currentContent"
                val completion = llmEngine.completion(prompt)
                
                _uiState.value = _uiState.value.copy(
                    content = currentContent + completion,
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
    val isGenerating: Boolean = false
)
