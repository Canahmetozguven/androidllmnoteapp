package com.synapsenotes.ai.domain.usecase

import com.synapsenotes.ai.core.ai.LlmEngine
import com.synapsenotes.ai.core.util.VectorMath
import com.synapsenotes.ai.domain.model.Note
import com.synapsenotes.ai.domain.repository.NoteRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class VectorSearchUseCase @Inject constructor(
    private val repository: NoteRepository,
    private val llmEngine: LlmEngine
) {
    suspend operator fun invoke(query: String, topK: Int = 3): List<Note> {
        val allNotes = repository.getAllNotes().first()

        val queryEmbedding = try {
            llmEngine.embed(query)
        } catch (e: Exception) {
            // Fallback to simple keyword search if embedding fails
            return allNotes.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.content.contains(query, ignoreCase = true) 
            }.take(topK)
        }
        
        return allNotes
            .filter { it.embedding != null }
            .map { note ->
                val similarity = VectorMath.cosineSimilarity(queryEmbedding, note.embedding!!.toFloatArray())
                note to similarity
            }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }
}
