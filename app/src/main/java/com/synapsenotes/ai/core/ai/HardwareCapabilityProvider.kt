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
     * Get recommended batch size for embedding based on hardware, RAM, and model size.
     */
    fun getRecommendedEmbeddingBatchSize(modelSizeBytes: Long): Int

    /**
     * Get recommended context size for a specific backend.
     * Vulkan backend might require smaller context on low RAM devices due to higher VRAM usage.
     */
    fun getRecommendedContextSize(backend: BackendType): Int

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
     * Get the currently preferred embedding backend (persisted).
     */
    fun getPreferredEmbeddingBackend(): BackendType

    /**
     * Set the preferred embedding backend.
     */
    fun setPreferredEmbeddingBackend(backend: BackendType)

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
     * Get recommended backend order based on device specific heuristics.
     * Default should be VULKAN -> OPENCL -> CPU.
     */
    fun getRecommendedBackendOrder(): List<BackendType> = listOf(BackendType.VULKAN, BackendType.OPENCL, BackendType.CPU)

    /**
     * Mark a backend as failed (called after a crash or load failure).
     */
    fun markBackendFailed(backend: BackendType)

    /**
     * Clear failed backend status (for retry).
     */
    fun clearFailedBackends()

    /**
     * Mark a backend as being attempted. If the app crashes, this can be used
     * to identify the culprit on next startup.
     */
    fun markBackendAttempting(backend: BackendType)

    /**
     * Clear the attempting status (called after successful completion of risk-prone operation).
     */
    fun clearBackendAttempting()

    /**
     * Mark an embedding backend as being attempted.
     */
    fun markEmbeddingBackendAttempting(backend: BackendType)

    /**
     * Clear the embedding attempting status.
     */
    fun clearEmbeddingBackendAttempting()

    /**
     * Check if Android Hardware Buffer (AHB) interop is supported.
     * Returns true if both Vulkan and OpenCL backends support AHB.
     * This is required for hybrid Vulkan/OpenCL acceleration with zero-copy memory sharing.
     */
    fun isAHBInteropSupported(): Boolean
}
