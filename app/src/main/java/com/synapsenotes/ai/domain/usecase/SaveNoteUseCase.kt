package com.synapsenotes.ai.domain.usecase

import com.synapsenotes.ai.core.ai.LlmEngine
import com.synapsenotes.ai.domain.model.Note
import com.synapsenotes.ai.domain.repository.NoteRepository
import javax.inject.Inject

class SaveNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    private val llmEngine: LlmEngine
) {
    suspend operator fun invoke(note: Note) {
        // Generate embedding if content is present
        val embedding = if (note.content.isNotBlank()) {
            try {
                llmEngine.embed(note.content).toList()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        
        val noteWithEmbedding = note.copy(embedding = embedding)
        repository.saveNote(noteWithEmbedding)
    }
}
