package com.synapsenotes.ai.core.data.repository

import com.synapsenotes.ai.core.database.ChatDao
import com.synapsenotes.ai.core.database.ChatMessageEntity
import com.synapsenotes.ai.core.database.ChatSessionEntity
import com.synapsenotes.ai.core.database.NoteDao
import com.synapsenotes.ai.domain.model.ChatMessage
import com.synapsenotes.ai.domain.model.SourceNote
import com.synapsenotes.ai.domain.repository.ChatRepository
import com.synapsenotes.ai.domain.repository.ChatSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val noteDao: NoteDao
) : ChatRepository {

    override fun getAllSessions(): Flow<List<ChatSession>> {
        return chatDao.getAllSessions().map { entities ->
            entities.map { ChatSession(it.id, it.title, it.updatedAt) }
        }
    }

    override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId).map { entities ->
            entities.map { entity ->
                // Map sourceNoteIds to SourceNote objects
                // This is a bit inefficient (N+1 equivalent), but for local DB it's okay.
                // Ideally we'd optimize this.
                val sourceNotes = entity.sourceNoteIds?.map { noteId ->
                    val note = noteDao.getById(noteId)
                    SourceNote(noteId, note?.title ?: "Unknown Note")
                } ?: emptyList()

                ChatMessage(
                    id = entity.id,
                    content = entity.content,
                    isUser = entity.isUser,
                    timestamp = entity.timestamp,
                    sourceNotes = sourceNotes,
                    thoughtProcess = entity.thoughtProcess
                )
            }
        }
    }

    override suspend fun createSession(title: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        chatDao.insertSession(ChatSessionEntity(id, title, now, now))
        return id
    }

    override suspend fun saveMessage(sessionId: String, message: ChatMessage) {
        val entity = ChatMessageEntity(
            id = message.id,
            sessionId = sessionId,
            content = message.content,
            isUser = message.isUser,
            timestamp = message.timestamp,
            sourceNoteIds = message.sourceNotes.map { it.noteId },
            thoughtProcess = message.thoughtProcess
        )
        chatDao.insertMessage(entity)
        // Update session timestamp
        chatDao.updateSessionTitle(sessionId, "Chat ${System.currentTimeMillis()}", message.timestamp) 
        // We might want a better title strategy later
    }

    override suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
    }

    override suspend fun updateSessionTitle(sessionId: String, title: String) {
        // We need to get current timestamp or keep existing? Let's just update title.
        // But the DAO method requires timestamp.
        val now = System.currentTimeMillis()
        chatDao.updateSessionTitle(sessionId, title, now)
    }
}
