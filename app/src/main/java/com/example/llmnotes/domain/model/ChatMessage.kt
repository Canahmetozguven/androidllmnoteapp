package com.example.llmnotes.domain.model

import java.util.UUID

data class SourceNote(
    val noteId: String,
    val noteTitle: String
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceNotes: List<SourceNote> = emptyList(),
    val thoughtProcess: String? = null
)
