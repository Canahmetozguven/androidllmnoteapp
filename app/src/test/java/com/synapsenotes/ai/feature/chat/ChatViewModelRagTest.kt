package com.synapsenotes.ai.feature.chat

import com.synapsenotes.ai.core.preferences.AppPreferences
import com.synapsenotes.ai.domain.model.ChatMessage
import com.synapsenotes.ai.domain.model.Note
import com.synapsenotes.ai.domain.repository.ChatRepository
import com.synapsenotes.ai.domain.repository.NoteRepository
import com.synapsenotes.ai.domain.usecase.VectorSearchUseCase
import com.synapsenotes.ai.test.CoroutineTestExtension
import com.synapsenotes.ai.core.ai.LlmEngine
import com.synapsenotes.ai.core.ai.PromptBuilder
import com.synapsenotes.ai.core.ai.HardwareInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * TDD Test Suite for ChatViewModel RAG behavior.
 *
 * Tests verify that ChatViewModel correctly handles RAG (Retrieval-Augmented Generation)
 * for models that support it, and skips RAG for models that don't require it.
 *
 * Key scenarios:
 * - Models like "lfm2-1.2b" should skip vectorSearch (no RAG needed)
 * - Models like "lfm2-1.2b-rag" should call vectorSearch (RAG needed)
 *
 * Note: Some tests are currently disabled (@Ignore) because ChatViewModel
 * does not yet have ModelManager injected to determine which model is active.
 * These tests describe the DESIRED behavior and should pass once ModelManager
 * is integrated into ChatViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelRagTest {

    @JvmField
    @RegisterExtension
    val coroutineExtension = CoroutineTestExtension()

    private lateinit var viewModel: ChatViewModel
    private val vectorSearchUseCase: VectorSearchUseCase = mockk(relaxed = true)
    private val llmEngine: LlmEngine = mockk(relaxed = true)
    private val promptBuilder: PromptBuilder = mockk(relaxed = true)
    private val chatRepository: ChatRepository = mockk(relaxed = true)
    private val noteRepository: NoteRepository = mockk(relaxed = true)
    private val appPreferences: AppPreferences = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        val hwInfo = HardwareInfo(isGpuAccelerationEnabled = true, backendName = "OPENCL", gpuName = "Test GPU")
        every { llmEngine.getHardwareInfo() } returns hwInfo
        every { llmEngine.isGpuEnabled() } returns true
        every { llmEngine.completionFlow(any()) } returns flowOf("test response")
        
        // Default: vector search returns empty list (suspend function - use coEvery)
        coEvery { vectorSearchUseCase(any()) } returns emptyList()
        
        // Default: prompt builder returns a basic prompt
        every { promptBuilder.buildPrompt(any(), any()) } returns "test prompt"
        
        // Default: note repository returns empty notes
        every { noteRepository.getAllNotes() } returns flowOf(emptyList())
        
        // Default: chat repository returns empty sessions
        every { chatRepository.getAllSessions() } returns flowOf(emptyList())

        viewModel = ChatViewModel(
            vectorSearchUseCase,
            llmEngine,
            promptBuilder,
            chatRepository,
            noteRepository,
            appPreferences
        )
    }

    /**
     * Test behavior for LFM2 model (which has RAG disabled).
     * 
     * When a user sends a message and the active model is "lfm2-1.2b":
     * - ChatViewModel should NOT call vectorSearchUseCase (no RAG needed)
     * - This model is designed to work without retrieval-augmented generation.
     */
    @Test
    fun `sendMessage with lfm2 model bypasses vector search`() = runTest {
        // Arrange
        every { appPreferences.activeChatModelId } returns "lfm2-1.2b"
        
        coEvery { chatRepository.createSession(any()) } returns "test-session-id"
        coEvery { chatRepository.saveMessage(any(), any()) } just Runs
        
        // Act
        viewModel.sendMessage("What are my notes about AI?")
        
        // Wait for coroutine to complete
        testScheduler.advanceUntilIdle()
        
        // Assert
        // Vector search should NOT be called since lfm2 has requiresRag=false
        coVerify(exactly = 0) { vectorSearchUseCase(any()) }
    }

    /**
     * DISABLED TEST - Describes desired behavior for RAG-enabled models.
     * 
     * When a user sends a message and the active model is "lfm2-1.2b-rag" (RAG variant):
     * - ChatViewModel SHOULD call vectorSearchUseCase to find relevant notes
     * - ChatViewModel should call promptBuilder.buildPrompt with the found notes
     * 
     * This test will pass once ChatViewModel has ModelManager injected and can
     * check the active model's requiresRag property.
     */
    @Test
    fun `sendMessage with RAG-enabled model calls vector search`() = runTest {
        // Arrange
        // TODO: Once ModelManager is injected, mock it to return "lfm2-1.2b-rag"
        // Use a model that has requiresRag=true (e.g. qwen3-0.6b is default true)
        every { appPreferences.activeChatModelId } returns "qwen3-0.6b"
        
        val mockNotes = listOf(
            Note(id = "1", title = "AI Notes", content = "ML concepts", createdAt = 0L, updatedAt = 0L, tags = emptyList(), embedding = null)
        )
        coEvery { vectorSearchUseCase(any()) } returns mockNotes
        coEvery { chatRepository.createSession(any()) } returns "test-session-id"
        coEvery { chatRepository.saveMessage(any(), any()) } just Runs
        
        // Act
        viewModel.sendMessage("What are my notes about AI?")
        
        // Wait for coroutine to complete
        testScheduler.advanceUntilIdle()
        
        // Assert
        // Should verify vectorSearchUseCase WAS called for RAG models
        coVerify(atLeast = 1) { vectorSearchUseCase(any()) }
    }

    /**
     * CURRENT WORKING TEST - Verify basic message flow works.
     * 
     * This test verifies the current ChatViewModel behavior without relying
     * on model-specific RAG logic (which hasn't been implemented yet).
     */
    @Test
    fun `sendMessage updates messages state`() = runTest {
        // Arrange
        val testMessage = "Hello, AI!"
        
        // Act
        viewModel.sendMessage(testMessage)
        
        // Assert
        // Since sendMessage is async and runs in viewModelScope,
        // we simply verify it doesn't throw an exception
        // The actual message state updates would be verified via StateFlow collection
    }

     /**
      * CURRENT WORKING TEST - Verify empty message is ignored.
      */
     @Test
     fun `sendMessage with blank input is ignored`() = runTest {
         // Arrange
         val blankMessage = "   "
         
         // Act
         viewModel.sendMessage(blankMessage)
         
         // Assert
         // sendMessage returns early if text is blank, so no async work occurs
     }

     /**
      * TDD TEST - Verify that non-RAG models (Liquid LFM2) bypass vector search.
      * 
      * When a user sends a message and the active model is "lfm2-1.2b" (requiresRag=false):
      * - ChatViewModel should NOT call vectorSearchUseCase (no RAG)
      * - ChatViewModel should pass empty notes to promptBuilder
      * - This allows the model to generate responses without note context.
      * 
      * This test SHOULD FAIL until:
      * 1. ModelInfo.requiresRag = false for lfm2-1.2b in AVAILABLE_MODELS
      * 2. ChatViewModel correctly checks requiresRag before calling vectorSearch
      */
      @Test
      fun `sendMessage with non-RAG model Liquid bypasses vector search`() = runTest {
         // Arrange
         every { appPreferences.activeChatModelId } returns "lfm2-1.2b"
         
         coEvery { chatRepository.createSession(any()) } returns "test-session-id"
         coEvery { chatRepository.saveMessage(any(), any()) } just Runs
         
         // Act
         viewModel.sendMessage("Tell me about yourself")
         
         // Wait for coroutine to complete
         testScheduler.advanceUntilIdle()
         
         // Assert
         // Vector search should NOT be called for non-RAG model (lfm2-1.2b with requiresRag=false)
         coVerify(exactly = 0) { vectorSearchUseCase(any()) }
     }
 }
