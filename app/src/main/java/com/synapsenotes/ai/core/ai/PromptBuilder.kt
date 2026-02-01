package com.synapsenotes.ai.core.ai

import com.synapsenotes.ai.domain.model.Note
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptBuilder @Inject constructor() {

    companion object {
        private const val MAX_CONTEXT_LENGTH = 10000
    }

    fun buildPrompt(userMessage: String, relevantNotes: List<Note>): String {
        val context = buildContext(relevantNotes)
        return if (context.isNotBlank()) {
            "Context:\n$context\n\n$userMessage"
        } else {
            userMessage
        }
    }

    private fun buildContext(notes: List<Note>): String {
        if (notes.isEmpty()) return ""

        val rawContext = notes.joinToString("\n\n") { "Note: ${it.title}\n${it.content}" }
        
        return if (rawContext.length > MAX_CONTEXT_LENGTH) {
            // Smart truncation: Try to cut at the last newline before limit to avoid splitting words
            val safeLimit = rawContext.take(MAX_CONTEXT_LENGTH)
            val cutIndex = safeLimit.lastIndexOf('\n')
            
            if (cutIndex > 0) {
                 safeLimit.substring(0, cutIndex) + "\n\n[System: Context truncated due to length]"
            } else {
                 safeLimit + "\n\n[System: Context truncated due to length]"
            }
        } else {
            rawContext
        }
    }
}
