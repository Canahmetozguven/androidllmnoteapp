package com.synapsenotes.ai.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapsenotes.ai.domain.model.Note
import com.synapsenotes.ai.domain.usecase.DeleteNoteUseCase
import com.synapsenotes.ai.domain.usecase.GetNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.synapsenotes.ai.core.preferences.AppPreferences

@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val getNotesUseCase: GetNotesUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()
    
    val lastSyncTimestamp: Long
        get() = appPreferences.lastSyncTimestamp

    init {
        viewModelScope.launch {
            getNotesUseCase().collectLatest {
                _notes.value = it
            }
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            deleteNoteUseCase(id)
        }
    }
}
