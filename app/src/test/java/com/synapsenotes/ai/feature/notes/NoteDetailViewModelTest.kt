package com.synapsenotes.ai.feature.notes

import androidx.lifecycle.SavedStateHandle
import com.synapsenotes.ai.core.ai.LlmEngine
import com.synapsenotes.ai.core.preferences.AppPreferences
import com.synapsenotes.ai.domain.model.Note
import com.synapsenotes.ai.domain.repository.NoteRepository
import com.synapsenotes.ai.domain.usecase.DeleteNoteUseCase
import com.synapsenotes.ai.domain.usecase.SaveNoteUseCase
import com.synapsenotes.ai.test.CoroutineTestExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NoteDetailViewModelTest {

    @JvmField
    @RegisterExtension
    val coroutineExtension = CoroutineTestExtension()

    private lateinit var viewModel: NoteDetailViewModel
    private val saveNoteUseCase: SaveNoteUseCase = mockk(relaxed = true)
    private val deleteNoteUseCase: DeleteNoteUseCase = mockk(relaxed = true)
    private val repository: NoteRepository = mockk(relaxed = true)
    private val llmEngine: LlmEngine = mockk(relaxed = true)
    private val appPreferences: AppPreferences = mockk(relaxed = true)
    private val savedStateHandle: SavedStateHandle = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        every { savedStateHandle.get<String>("noteId") } returns "new"
        viewModel = NoteDetailViewModel(
            saveNoteUseCase,
            deleteNoteUseCase,
            repository,
            llmEngine,
            appPreferences,
            savedStateHandle
        )
    }

    @Test
    fun `saveNote triggers saveNoteUseCase with correct data`() = runTest {
        // Given
        val title = "Test Title"
        val content = "Test Content"
        viewModel.onTitleChange(title)
        viewModel.onContentChange(content)

        // When
        viewModel.saveNote()

        // Then
        val noteSlot = slot<Note>()
        coVerify { saveNoteUseCase(capture(noteSlot)) }
        
        assertEquals(title, noteSlot.captured.title)
        assertEquals(content, noteSlot.captured.content)
    }
}
