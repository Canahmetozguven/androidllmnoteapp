package com.example.llmnotes.feature.chat

import com.example.llmnotes.core.ai.LlmEngine
import com.example.llmnotes.domain.model.ChatMessage
import com.example.llmnotes.domain.model.Note
import com.example.llmnotes.domain.repository.ChatRepository
import com.example.llmnotes.domain.repository.ChatSession
import com.example.llmnotes.domain.repository.NoteRepository
import com.example.llmnotes.domain.usecase.VectorSearchUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var viewModel: ChatViewModel
    private val vectorSearchUseCase: VectorSearchUseCase = mockk()
    private val llmEngine: LlmEngine = mockk(relaxed = true)
    private val chatRepository: ChatRepository = mockk(relaxed = true)
    private val noteRepository: NoteRepository = mockk()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        every { chatRepository.getAllSessions() } returns flowOf(emptyList())
        every { noteRepository.getAllNotes() } returns flowOf(emptyList())
        every { llmEngine.isGpuEnabled() } returns false
        
        viewModel = ChatViewModel(
            vectorSearchUseCase,
            llmEngine,
            chatRepository,
            noteRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendMessage creates new session if none exists`() = runTest {
        // Given
        val message = "Hello"
        coEvery { chatRepository.createSession(any()) } returns "session_1"
        coEvery { vectorSearchUseCase(any()) } returns emptyList()
        coEvery { llmEngine.completion(any()) } returns "Hi there"

        // When
        viewModel.sendMessage(message)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { chatRepository.createSession(any()) }
        coVerify(atLeast = 1) { chatRepository.saveMessage("session_1", any()) }
        assertEquals("session_1", viewModel.currentSessionId.value)
    }

    @Test
    fun `sendMessage uses RAG context`() = runTest {
        // Given
        val message = "Question"
        val relevantNote = Note("1", "Title", "Content", 1000L, 1000L, emptyList(), null)
        coEvery { chatRepository.createSession(any()) } returns "session_1"
        coEvery { vectorSearchUseCase("Question") } returns listOf(relevantNote)
        coEvery { llmEngine.completion(any()) } returns "Answer"

        // When
        viewModel.sendMessage(message)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val slot = slot<String>()
        coVerify { llmEngine.completion(capture(slot)) }
        
        assert(slot.captured.contains("Context:"))
        assert(slot.captured.contains("Title"))
        assert(slot.captured.contains("Content"))
    }
}
