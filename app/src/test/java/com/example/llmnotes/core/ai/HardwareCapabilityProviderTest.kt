package com.example.llmnotes.core.ai

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for HardwareCapabilityProvider implementations.
 * Tests are written first (TDD) to define expected behavior.
 */
class HardwareCapabilityProviderTest {

    private lateinit var mockProvider: MockHardwareCapabilityProvider

    @Before
    fun setUp() {
        mockProvider = MockHardwareCapabilityProvider()
    }

    @Test
    fun `isVulkanSupported returns true when Vulkan is available`() {
        mockProvider.vulkanSupported = true
        
        assertTrue(mockProvider.isVulkanSupported())
    }

    @Test
    fun `isVulkanSupported returns false when Vulkan is not available`() {
        mockProvider.vulkanSupported = false
        
        assertFalse(mockProvider.isVulkanSupported())
    }

    @Test
    fun `getGpuName returns GPU name when available`() {
        mockProvider.mockGpuName = "Adreno 730"
        
        assertEquals("Adreno 730", mockProvider.getGpuName())
    }

    @Test
    fun `getGpuName returns null when GPU info unavailable`() {
        mockProvider.mockGpuName = null
        
        assertNull(mockProvider.getGpuName())
    }
}

/**
 * Mock implementation for testing purposes.
 * This is defined here for test-only use; actual code will use the interface.
 */
class MockHardwareCapabilityProvider : HardwareCapabilityProvider {
    var vulkanSupported: Boolean = false
    var mockGpuName: String? = null

    override fun isVulkanSupported(): Boolean = vulkanSupported
    override fun getGpuName(): String? = mockGpuName
}
