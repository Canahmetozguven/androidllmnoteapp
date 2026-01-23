package com.synapsenotes.ai.core.ai

/**
 * Data class representing hardware acceleration information for the LLM engine.
 */
data class HardwareInfo(
    /**
     * Whether GPU acceleration is currently enabled and available.
     */
    val isGpuAccelerationEnabled: Boolean,
    
    /**
     * Name of the compute backend being used (e.g., "CPU", "Vulkan", "OpenCL").
     */
    val backendName: String,
    
    /**
     * Name of the GPU, if available and GPU acceleration is enabled.
     */
    val gpuName: String? = null
)
