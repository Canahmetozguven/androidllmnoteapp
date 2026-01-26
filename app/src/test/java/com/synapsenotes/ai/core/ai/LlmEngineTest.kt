package com.synapsenotes.ai.core.ai

import com.synapsenotes.ai.test.CoroutineTestExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
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
        whenever(hardwareCapabilityProvider.getRecommendedContextSize()).thenReturn(2048)
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
        }.whenever(llmContext).completion(anyString(), any())
        
        val tokens = llmEngine.completionFlow("Hi").toList()
        
        assertEquals(listOf("Hello", " World"), tokens)
    }
}
