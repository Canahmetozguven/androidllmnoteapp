package com.synapsenotes.ai.core.ai

/**
 * Interface for querying hardware capabilities related to AI acceleration.
 * This abstraction allows for easy testing by injecting mock implementations.
 */
interface HardwareCapabilityProvider {
    
    /**
     * Check if Vulkan GPU acceleration is supported on this device.
     * @return true if Vulkan 1.1+ is available, false otherwise.
     */
    fun isVulkanSupported(): Boolean
    
    /**
     * Get the name of the GPU, if available.
     * @return GPU name string (e.g., "Adreno 730") or null if unavailable.
     */
    fun getGpuName(): String?

    /**
     * Get the total system RAM in Gigabytes.
     * @return Total RAM in GB (e.g., 5.8).
     */
    fun getTotalRamGb(): Double
    /**
     * Get the available system RAM in Gigabytes.
     * @return Available RAM in GB (e.g., 2.5).
     */
    fun getAvailableRamGb(): Double
}
