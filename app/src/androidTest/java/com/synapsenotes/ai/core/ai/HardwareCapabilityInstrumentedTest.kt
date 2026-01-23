package com.synapsenotes.ai.core.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardwareCapabilityInstrumentedTest {

    @Test
    fun testRealHardwareCapabilities() {
        // Use the real implementation with the test application context
        val provider = DefaultHardwareCapabilityProvider(
            ApplicationProvider.getApplicationContext()
        )

        // Check RAM
        val ram = provider.getTotalRamGb()
        assertTrue("RAM should be greater than 0", ram > 0)
        println("Device RAM: $ram GB")

        // Check Vulkan
        // We can't assert true/false universally (depends on emulator/device), 
        // but we can ensure it doesn't crash and returns a boolean.
        val isVulkan = provider.isVulkanSupported()
        println("Vulkan Supported: $isVulkan")

        // Check GPU Name
        val gpuName = provider.getGpuName()
        println("GPU Name: $gpuName")
        
        // If Vulkan is supported, GPU name might still be placeholder "Vulkan GPU" 
        // in current implementation, or null if not supported.
        if (isVulkan) {
             assertNotNull(gpuName)
        }
    }
}
