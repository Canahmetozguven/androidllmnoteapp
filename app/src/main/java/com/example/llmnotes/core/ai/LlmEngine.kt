package com.example.llmnotes.core.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmEngine @Inject constructor(
    private val hardwareCapabilityProvider: HardwareCapabilityProvider,
    private val llmContext: LlmContext
) {
    
    companion object {
        private const val TAG = "LlmEngine"
    }

    private var isLoaded = false
    private val mutex = Mutex()

    /**
     * Get information about the hardware acceleration status.
     */
    fun getHardwareInfo(): HardwareInfo {
        val vulkanSupported = hardwareCapabilityProvider.isVulkanSupported()
        return HardwareInfo(
            isGpuAccelerationEnabled = isGpuEnabled(),
            backendName = if (isGpuEnabled()) "Vulkan" else "CPU",
            gpuName = if (vulkanSupported) hardwareCapabilityProvider.getGpuName() else null
        )
    }

    fun isGpuEnabled(): Boolean = llmContext.isGpuEnabled()

    suspend fun loadModel(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isLoaded) {
                llmContext.unload()
                isLoaded = false
            }

            // Log hardware info when loading model
            val hwInfo = getHardwareInfo()
            Log.i(TAG, "Loading model with backend: ${hwInfo.backendName}, GPU: ${hwInfo.gpuName ?: "N/A"}")

            val success = llmContext.loadModel(path)
            if (success) {
                isLoaded = true
                Log.i(TAG, "Model loaded successfully with ${hwInfo.backendName} acceleration")
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to load model at $path"))
            }
        }
    }

    suspend fun completion(prompt: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isLoaded) throw IllegalStateException("Model not loaded")
            llmContext.completion(prompt)
        }
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isLoaded) throw IllegalStateException("Model not loaded")
            llmContext.embed(text)
        }
    }

    suspend fun release() {
        mutex.withLock {
            if (isLoaded) {
                llmContext.unload()
                isLoaded = false
            }
        }
    }
}

