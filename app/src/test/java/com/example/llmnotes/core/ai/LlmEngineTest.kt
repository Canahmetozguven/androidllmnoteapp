package com.example.llmnotes.core.ai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LlmEngine's hardware info functionality.
 * Tests are written first (TDD) - the implementation will be added after.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LlmEngineTest {

    private lateinit var mockHardwareProvider: MockHardwareCapabilityProvider
    private lateinit var mockLlmContext: MockLlmContext

    @Before
    fun setUp() {
        mockHardwareProvider = MockHardwareCapabilityProvider()
        mockLlmContext = MockLlmContext()
    }

    @Test
    fun `getHardwareInfo returns GPU enabled when Vulkan is supported`() = runTest {
        mockHardwareProvider.vulkanSupported = true
        mockHardwareProvider.mockGpuName = "Adreno 730"
        
        val engine = LlmEngine(mockHardwareProvider, mockLlmContext)
        val info = engine.getHardwareInfo()
        
        assertTrue(info.isGpuAccelerationEnabled)
        assertEquals("Adreno 730", info.gpuName)
    }

    @Test
    fun `getHardwareInfo returns GPU disabled when Vulkan is not supported`() = runTest {
        mockHardwareProvider.vulkanSupported = false
        mockHardwareProvider.mockGpuName = null
        
        val engine = LlmEngine(mockHardwareProvider, mockLlmContext)
        val info = engine.getHardwareInfo()
        
        assertFalse(info.isGpuAccelerationEnabled)
        assertNull(info.gpuName)
    }

    @Test
    fun `getHardwareInfo returns correct backend name for Vulkan`() = runTest {
        mockHardwareProvider.vulkanSupported = true
        
        val engine = LlmEngine(mockHardwareProvider, mockLlmContext)
        val info = engine.getHardwareInfo()
        
        assertEquals("Vulkan", info.backendName)
    }

    @Test
    fun `getHardwareInfo returns CPU backend when Vulkan not supported`() = runTest {
        mockHardwareProvider.vulkanSupported = false
        
        val engine = LlmEngine(mockHardwareProvider, mockLlmContext)
        val info = engine.getHardwareInfo()
        
        assertEquals("CPU", info.backendName)
    }
}
