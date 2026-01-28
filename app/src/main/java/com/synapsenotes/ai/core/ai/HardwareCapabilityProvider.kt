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

    /**
     * Get recommended batch size for inference based on device capabilities.
     * S22/Exynos devices need lower batch size (256) for stability.
     */
    fun getRecommendedBatchSize(): Int

    /**
     * Get recommended context size.
     */
    fun getRecommendedContextSize(): Int

    /**
     * Check if memory mapping (mmap) is safe to use on this device.
     * Samsung S22/S23 (Gen 1) have kernel bugs with mmap + Vulkan.
     */
    fun isMmapSafe(): Boolean

    /**
     * Get list of available backends on this device.
     */
    fun getAvailableBackends(): List<BackendType>

    /**
     * Get the currently preferred backend (persisted).
     */
    fun getPreferredBackend(): BackendType

    /**
     * Set the preferred backend.
     */
    fun setPreferredBackend(backend: BackendType)

    /**
     * Safely probe a backend to see if it works without crashing.
     * Returns true if the backend is usable, false otherwise.
     * This checks if the backend was previously marked as failed.
     */
    fun probeBackend(backend: BackendType): Boolean

    /**
     * Get backends that have been marked as failed (crashed during probe or load).
     */
    fun getFailedBackends(): Set<BackendType>

    /**
     * Mark a backend as failed (called after a crash or load failure).
     */
    fun markBackendFailed(backend: BackendType)

    /**
     * Clear failed backend status (for retry).
     */
    fun clearFailedBackends()
}
