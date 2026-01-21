package com.example.llmnotes.domain.usecase

import com.example.llmnotes.core.ai.LlmEngine
import com.example.llmnotes.domain.model.Note
import com.example.llmnotes.domain.repository.NoteRepository
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
