package com.synapsenotes.ai.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapsenotes.ai.domain.repository.DriveFile
import com.synapsenotes.ai.domain.repository.DriveRepository
import com.synapsenotes.ai.domain.model.Note
import com.synapsenotes.ai.domain.usecase.SaveNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    private val saveNoteUseCase: SaveNoteUseCase
) : ViewModel() {

    private val _files = MutableStateFlow<List<DriveFile>>(emptyList())
    val files: StateFlow<List<DriveFile>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    init {
        checkSignInAndLoad()
    }

    fun checkSignInAndLoad() {
        val signedIn = driveRepository.isSignedIn()
        _isSignedIn.value = signedIn
        if (signedIn) {
            loadFiles()
        }
    }

    fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _files.value = driveRepository.listFiles()
            _isLoading.value = false
        }
    }
    
    fun importFile(file: DriveFile) {
        viewModelScope.launch {
            _isLoading.value = true
            val content = driveRepository.downloadFile(file.id, file.mimeType)
            if (content != null) {
                val newNote = Note(
                    id = java.util.UUID.randomUUID().toString(),
                    title = file.name,
                    content = content,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    tags = emptyList(),
                    embedding = null
                )
                saveNoteUseCase(newNote)
                _importMessage.value = "Imported '${file.name}'"
            } else {
                _importMessage.value = "Failed to import '${file.name}'"
            }
            _isLoading.value = false
        }
    }
    
    fun clearImportMessage() {
        _importMessage.value = null
    }
}
