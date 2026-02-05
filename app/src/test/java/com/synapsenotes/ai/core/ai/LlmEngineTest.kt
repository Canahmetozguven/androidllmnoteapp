package com.synapsenotes.ai.core.ai

import com.synapsenotes.ai.test.CoroutineTestExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LlmEngineTest {

    @JvmField
    @RegisterExtension
    val coroutineExtension = CoroutineTestExtension()

    private lateinit var llmEngine: LlmEngine
    private val llmContext: LlmContext = mock()
    private val hardwareCapabilityProvider: HardwareCapabilityProvider = mock()

    @BeforeEach
    fun setup() {
        whenever(hardwareCapabilityProvider.getRecommendedBatchSize()).thenReturn(512)
        whenever(hardwareCapabilityProvider.getRecommendedContextSize(any())).thenReturn(2048)
        whenever(hardwareCapabilityProvider.isVulkanSupported()).thenReturn(true)
        whenever(hardwareCapabilityProvider.getGpuName()).thenReturn("Test GPU")
        
        llmEngine = LlmEngine(hardwareCapabilityProvider, llmContext)
    }

    @Test
    fun `loadModel success calls context load`() = runTest {
        whenever(hardwareCapabilityProvider.getPreferredBackend()).thenReturn(BackendType.VULKAN)
        whenever(hardwareCapabilityProvider.isMmapSafe()).thenReturn(true)
        
        // Match 6 arguments: path, template, nBatch, nCtx, useMmap, backendType
        whenever(llmContext.loadModel(anyString(), anyOrNull(), anyInt(), anyInt(), any(), any())).thenReturn(true)
        whenever(llmContext.isGpuEnabled()).thenReturn(true)
        
        val result = llmEngine.loadModel("/path/to/model")
        
        assertTrue(result.isSuccess)
        verify(llmContext).loadModel(anyString(), anyOrNull(), anyInt(), anyInt(), any(), any())
    }
    
     @Test
     fun `completionFlow emits tokens`() = runTest {
         whenever(hardwareCapabilityProvider.getPreferredBackend()).thenReturn(BackendType.VULKAN)
         whenever(hardwareCapabilityProvider.isMmapSafe()).thenReturn(true)
         whenever(llmContext.loadModel(anyString(), anyOrNull(), anyInt(), anyInt(), any(), any())).thenReturn(true)
         llmEngine.loadModel("path")
         
         doAnswer { invocation ->
             val callback = invocation.getArgument<LlmCallback>(1)
             callback.onToken("Hello")
             callback.onToken(" World")
             "Hello World"
         }.whenever(llmContext).completion(anyString(), any(), any(), any())
         
         val tokens = llmEngine.completionFlow("Hi").toList()
         
         assertEquals(listOf("Hello", " World"), tokens)
     }

    @Test
    fun `loading chat model B after chat model A does not unload embedding`() = runTest {
        // Setup hardware capabilities  
        whenever(hardwareCapabilityProvider.getPreferredBackend()).thenReturn(BackendType.CPU)
        whenever(hardwareCapabilityProvider.getRecommendedBackendOrder()).thenReturn(listOf(BackendType.CPU))
        whenever(hardwareCapabilityProvider.getFailedBackends()).thenReturn(emptySet())
        whenever(hardwareCapabilityProvider.getRecommendedBatchSize()).thenReturn(512)
        whenever(hardwareCapabilityProvider.getRecommendedContextSize(any())).thenReturn(2048)
        whenever(hardwareCapabilityProvider.isMmapSafe()).thenReturn(true)
        whenever(hardwareCapabilityProvider.getAvailableBackends()).thenReturn(listOf(BackendType.CPU))
        whenever(hardwareCapabilityProvider.getRecommendedEmbeddingBatchSize(any())).thenReturn(512)
        whenever(hardwareCapabilityProvider.getPreferredEmbeddingBackend()).thenReturn(BackendType.CPU)
        
        // Setup llmContext mocks - use matching signatures like the existing test
        whenever(llmContext.loadModel(anyString(), anyOrNull(), anyInt(), anyInt(), any(), any())).thenReturn(true)
        whenever(llmContext.loadEmbeddingModel(anyString(), anyInt(), anyInt(), any(), any())).thenReturn(true)
        
        // Load chat model A
        llmEngine.loadModel("modelA.gguf")
        llmEngine.loadEmbeddingModel("embed.gguf")
        
        // Load chat model B - should unload previous chat model but NOT embedding
        llmEngine.loadModel("modelB.gguf")
        
        // Verify unloadChat was called but unloadEmbedding was NOT
        verify(llmContext).unloadChat()
        verify(llmContext, never()).unloadEmbedding()
    }

    @Test
    fun `embed works when only embedding model loaded`() = runTest {
        whenever(hardwareCapabilityProvider.getPreferredEmbeddingBackend()).thenReturn(BackendType.CPU)
        whenever(hardwareCapabilityProvider.getRecommendedBackendOrder()).thenReturn(listOf(BackendType.CPU))
        whenever(hardwareCapabilityProvider.getFailedBackends()).thenReturn(emptySet())
        whenever(hardwareCapabilityProvider.isMmapSafe()).thenReturn(true)
        whenever(hardwareCapabilityProvider.getRecommendedEmbeddingBatchSize(any())).thenReturn(512)
        whenever(hardwareCapabilityProvider.getRecommendedContextSize(any())).thenReturn(2048)
        
     // Only embedding model loaded
         whenever(llmContext.loadEmbeddingModel(anyString(), anyInt(), anyInt(), any(), any())).thenReturn(true)
         whenever(llmContext.embed(anyString())).thenReturn(floatArrayOf(0.1f, 0.2f))
        
        llmEngine.loadEmbeddingModel("embed.gguf")
        
        // embed() should work without throwing
        val result = llmEngine.embed("test text")
        assertEquals(2, result.size)
    }

    @Test
    fun `embed throws when embedding model not loaded`() = runTest {
        // Setup: No embedding model, only chat
        whenever(hardwareCapabilityProvider.getPreferredBackend()).thenReturn(BackendType.CPU)
        whenever(hardwareCapabilityProvider.getRecommendedBackendOrder()).thenReturn(listOf(BackendType.CPU))
        whenever(hardwareCapabilityProvider.getFailedBackends()).thenReturn(emptySet())
        whenever(hardwareCapabilityProvider.isMmapSafe()).thenReturn(true)
        whenever(hardwareCapabilityProvider.getRecommendedContextSize(any())).thenReturn(2048)
        whenever(hardwareCapabilityProvider.getAvailableBackends()).thenReturn(listOf(BackendType.CPU))
        whenever(llmContext.loadModel(anyString(), anyOrNull(), anyInt(), anyInt(), any(), any())).thenReturn(true)
        llmEngine.loadModel("chat.gguf")
        
        // embed() should throw IllegalStateException
        assertThrows<IllegalStateException> {
            llmEngine.embed("test text")
        }
    }

    @Test
    fun `release unloads both models`() = runTest {
        // Setup hardware capabilities
        whenever(hardwareCapabilityProvider.getPreferredBackend()).thenReturn(BackendType.CPU)
        whenever(hardwareCapabilityProvider.getPreferredEmbeddingBackend()).thenReturn(BackendType.CPU)
        whenever(hardwareCapabilityProvider.getRecommendedBackendOrder()).thenReturn(listOf(BackendType.CPU))
        whenever(hardwareCapabilityProvider.getFailedBackends()).thenReturn(emptySet())
        whenever(hardwareCapabilityProvider.isMmapSafe()).thenReturn(true)
        whenever(hardwareCapabilityProvider.getRecommendedContextSize(any())).thenReturn(2048)
        whenever(hardwareCapabilityProvider.getRecommendedEmbeddingBatchSize(any())).thenReturn(512)
        whenever(hardwareCapabilityProvider.getAvailableBackends()).thenReturn(listOf(BackendType.CPU))
        whenever(llmContext.loadModel(anyString(), anyOrNull(), anyInt(), anyInt(), any(), any())).thenReturn(true)
        whenever(llmContext.loadEmbeddingModel(anyString(), anyInt(), anyInt(), any(), any())).thenReturn(true)
        
        // Load both models
        llmEngine.loadModel("chat.gguf")
        llmEngine.loadEmbeddingModel("embed.gguf")
        
        // Release should unload both
        llmEngine.release()
        
        // Verify both unload methods were called
        verify(llmContext).unloadChat()
        verify(llmContext).unloadEmbedding()
    }
}
