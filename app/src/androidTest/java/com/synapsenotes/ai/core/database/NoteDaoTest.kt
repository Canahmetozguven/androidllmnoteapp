package com.synapsenotes.ai.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var noteDao: NoteDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        noteDao = db.noteDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeUserAndReadInList() = runBlocking {
        val note = NoteEntity("1", "Title", "Content", 123L, 123L)
        noteDao.insert(note)
        val byId = noteDao.getById("1")
        assertEquals(note, byId)
    }

    @Test
    fun deleteNote() = runBlocking {
        val note = NoteEntity("1", "Title", "Content", 123L, 123L)
        noteDao.insert(note)
        noteDao.delete("1")
        val byId = noteDao.getById("1")
        assertNull(byId)
    }
}
