package com.example.llmnotes.core.data.repository

import com.example.llmnotes.core.database.NoteDao
import com.example.llmnotes.core.database.NoteEntity
import com.example.llmnotes.domain.model.Note
import com.example.llmnotes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao
) : NoteRepository {

    override fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getNoteById(id: String): Note? {
        return noteDao.getById(id)?.toDomain()
    }

    override suspend fun saveNote(note: Note) {
        noteDao.insert(note.toEntity())
    }

    override suspend fun deleteNote(id: String) {
        noteDao.delete(id)
    }

    private fun NoteEntity.toDomain(): Note {
        return Note(
            id = id,
            title = title ?: "",
            content = content ?: "",
            createdAt = createdAt,
            updatedAt = updatedAt,
            tags = tags,
            embedding = embedding
        )
    }

    private fun Note.toEntity(): NoteEntity {
        return NoteEntity(
            id = id,
            title = title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            tags = tags,
            embedding = embedding
        )
    }
}
