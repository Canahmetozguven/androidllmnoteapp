package com.synapsenotes.ai.core.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch

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
        val preferred = hardwareCapabilityProvider.getPreferredBackend()
        val gpuEnabled = isGpuEnabled()
        
        return HardwareInfo(
            isGpuAccelerationEnabled = gpuEnabled,
            backendName = if (gpuEnabled) preferred.name else "CPU",
            gpuName = if (vulkanSupported && gpuEnabled) hardwareCapabilityProvider.getGpuName() else null
        )
    }

    fun isGpuEnabled(): Boolean = llmContext.isGpuEnabled()

    suspend fun loadModel(path: String, template: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isLoaded) {
                llmContext.unload()
                isLoaded = false
            }

            val preferredBackend = hardwareCapabilityProvider.getPreferredBackend()
            Log.i(TAG, "Loading model with preferred backend: ${preferredBackend.name}")

            val nBatch = hardwareCapabilityProvider.getRecommendedBatchSize()
            val nCtx = hardwareCapabilityProvider.getRecommendedContextSize()
            val useMmap = hardwareCapabilityProvider.isMmapSafe()

            val success = llmContext.loadModel(path, template, nBatch, nCtx, useMmap, preferredBackend)
            if (success) {
                isLoaded = true
                val hwInfo = getHardwareInfo()
                Log.i(TAG, "Model loaded successfully. Active Backend: ${hwInfo.backendName}. Batch: $nBatch, Ctx: $nCtx, Mmap: $useMmap")
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to load model at $path"))
            }
        }
    }

    suspend fun loadEmbeddingModel(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val success = llmContext.loadEmbeddingModel(path)
            if (success) {
                Log.i(TAG, "Embedding model loaded successfully")
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to load embedding model at $path"))
            }
        }
    }

    fun completionFlow(prompt: String): Flow<String> = callbackFlow {
        // Launch a coroutine to run the blocking native call
        launch(Dispatchers.IO) {
            mutex.withLock {
                if (!isLoaded) {
                    close(IllegalStateException("Model not loaded"))
                    return@withLock
                }
                
                try {
                    val callback = object : LlmCallback {
                        override fun onToken(token: String) {
                            trySend(token)
                        }
                    }
                    // This call blocks until completion finishes
                    llmContext.completion(prompt, callback)
                    close()
                } catch (e: Exception) {
                    close(e)
                }
            }
        }
        awaitClose { 
            // Trigger native stop
            llmContext.stopCompletion()
        }
    }
    
    suspend fun stopGeneration() {
        llmContext.stopCompletion()
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
