package com.example.llmnotes.domain.usecase

import com.example.llmnotes.domain.model.Note
import com.example.llmnotes.domain.repository.NoteRepository
import javax.inject.Inject

class SaveNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(note: Note) {
        repository.saveNote(note)
    }
}
