package com.example.llmnotes.domain.repository

import com.example.llmnotes.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllSessions(): Flow<List<ChatSession>>
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>
    suspend fun createSession(title: String): String
    suspend fun saveMessage(sessionId: String, message: ChatMessage)
    suspend fun deleteSession(sessionId: String)
    suspend fun updateSessionTitle(sessionId: String, title: String)
}

data class ChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long
)
