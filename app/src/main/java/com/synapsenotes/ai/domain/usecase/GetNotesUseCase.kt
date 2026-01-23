package com.synapsenotes.ai.domain.usecase

import com.synapsenotes.ai.domain.model.Note
import com.synapsenotes.ai.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNotesUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    operator fun invoke(): Flow<List<Note>> {
        return repository.getAllNotes()
    }
}
