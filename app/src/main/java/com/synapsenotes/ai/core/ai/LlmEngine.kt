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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.channels.onFailure

@Singleton
class LlmEngine @Inject constructor(
    private val hardwareCapabilityProvider: HardwareCapabilityProvider,
    private val llmContext: LlmContext
) {
    
    companion object {
        private const val TAG = "LlmEngine"
        private const val LOAD_TIMEOUT_MS = 60_000L // 60 seconds timeout for model loading
        
        private const val DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistant integrated into a notes app. Use the provided context to answer questions accurately.\n\nIMPORTANT: If the user asks in Turkish, answer in Turkish. You must wrap your internal reasoning and thought process inside <think> and </think> tags. The final answer should be outside these tags."
        
        private val DEFAULT_STOP_SEQUENCES = arrayOf(
            "<｜User｜>", "<｜Assistant｜>", "<｜end▁of▁sentence｜>", 
            "<|im_end|>", "<|im_start|>", 
            "</s>", "<|endoftext|>",
            "<|eot_id|>", "<|end_of_text|>", "<|begin_of_text|>"
        )
    }

    private var isChatLoaded = false
    private var isEmbeddingLoaded = false
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
             if (isChatLoaded) {
                 llmContext.unloadChat()
                 isChatLoaded = false
             }

                        // Get available backends (excludes already-failed ones)

                        val availableBackends = hardwareCapabilityProvider.getAvailableBackends()

                        Log.i(TAG, "Available backends: $availableBackends")

            

                        val nBatch = hardwareCapabilityProvider.getRecommendedBatchSize()

                        val useMmap = hardwareCapabilityProvider.isMmapSafe()

            

                        // Determine robust backend order based on device specific recommendations
            val preferred = hardwareCapabilityProvider.getPreferredBackend()
            val recommendedOrder = hardwareCapabilityProvider.getRecommendedBackendOrder()
            
            // Build the list: Preferred first (if valid), then recommended order, then remaining
            val backendsToTry = mutableListOf<BackendType>()
            
            // 1. Add preferred if it's safe/valid (and not failed)
            if (!hardwareCapabilityProvider.getFailedBackends().contains(preferred)) {
                backendsToTry.add(preferred)
            }
            
            // 2. Add recommended order (deduplicating)
            for (backend in recommendedOrder) {
                if (!backendsToTry.contains(backend)) {
                    backendsToTry.add(backend)
                }
            }
            
            // 3. Ensure CPU is always present as final fallback
            if (!backendsToTry.contains(BackendType.CPU)) {
                backendsToTry.add(BackendType.CPU)
            }

            val failed = hardwareCapabilityProvider.getFailedBackends()

            // Try each available backend in order
            for (backend in backendsToTry) {
                if (failed.contains(backend) && backend != BackendType.CPU) {
                    Log.w(TAG, "Skipping backend $backend - previously failed")
                    continue
                }

                val nCtx = hardwareCapabilityProvider.getRecommendedContextSize(backend)
                Log.i(TAG, "Attempting to load model with backend: ${backend.name}, Context: $nCtx")
                
                // Mark this backend as being attempted BEFORE the native call.
                if (backend != BackendType.CPU) {
                    hardwareCapabilityProvider.markBackendAttempting(backend)
                }

                try {
                    val success = withTimeout(LOAD_TIMEOUT_MS) {
                        llmContext.loadModel(path, template, nBatch, nCtx, useMmap, backend)
                    }
                    
                    if (success) {
                        // Success! Clear the attempting flag.
                        if (backend != BackendType.CPU) {
                            hardwareCapabilityProvider.clearBackendAttempting()
                            hardwareCapabilityProvider.setPreferredBackend(backend)
                        }
                        
                        isChatLoaded = true
                         val hwInfo = getHardwareInfo()
                         Log.i(TAG, "Model loaded successfully. Active Backend: ${hwInfo.backendName}. Batch: $nBatch, Ctx: $nCtx, Mmap: $useMmap")
                         return@withContext Result.success(true)
                    } else {
                        Log.w(TAG, "Backend $backend failed to load model (returned false), marking as failed")
                        if (backend != BackendType.CPU) {
                            hardwareCapabilityProvider.markBackendFailed(backend)
                            hardwareCapabilityProvider.clearBackendAttempting()
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Backend $backend timed out after ${LOAD_TIMEOUT_MS}ms", e)
                    if (backend != BackendType.CPU) {
                        hardwareCapabilityProvider.markBackendFailed(backend)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception loading model with backend $backend", e)
                    if (backend != BackendType.CPU) {
                        hardwareCapabilityProvider.markBackendFailed(backend)
                        hardwareCapabilityProvider.clearBackendAttempting()
                    }
                }
            }

            Log.e(TAG, "Failed to load model with all available backends")
            Result.failure(Exception("Failed to load model with all available backends"))
        }
    }

    suspend fun loadEmbeddingModel(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Calculate safe batch size based on hardware + RAM + model size
            val file = java.io.File(path)
            val modelSize = if (file.exists()) file.length() else 0L
            val nBatch = hardwareCapabilityProvider.getRecommendedEmbeddingBatchSize(modelSize)
            val useMmap = hardwareCapabilityProvider.isMmapSafe()

            Log.i(TAG, "Loading embedding model. Path: $path, Batch: $nBatch, Mmap: $useMmap")

            // Determine backend order: Preferred -> Vulkan -> OpenCL -> CPU
            val preferred = hardwareCapabilityProvider.getPreferredEmbeddingBackend()
            val recommendedOrder = hardwareCapabilityProvider.getRecommendedBackendOrder()
            
            val backendsToTry = mutableListOf<BackendType>()
            
            if (!hardwareCapabilityProvider.getFailedBackends().contains(preferred)) {
                backendsToTry.add(preferred)
            }
            
            for (backend in recommendedOrder) {
                if (!backendsToTry.contains(backend)) {
                    backendsToTry.add(backend)
                }
            }
            
            if (!backendsToTry.contains(BackendType.CPU)) {
                backendsToTry.add(BackendType.CPU)
            }

            val failed = hardwareCapabilityProvider.getFailedBackends()

            for (backend in backendsToTry) {
                if (failed.contains(backend) && backend != BackendType.CPU) {
                    Log.w(TAG, "Skipping embedding backend $backend - previously failed")
                    continue
                }

                val nCtx = hardwareCapabilityProvider.getRecommendedContextSize(backend)
                Log.i(TAG, "Attempting to load embedding model with backend: $backend, Context: $nCtx")
                if (backend != BackendType.CPU) {
                    hardwareCapabilityProvider.markEmbeddingBackendAttempting(backend)
                }

                try {
                    val success = withTimeout(LOAD_TIMEOUT_MS) {
                        llmContext.loadEmbeddingModel(path, nBatch, nCtx, useMmap, backend)
                    }
                    
                     if (success) {
                          if (backend != BackendType.CPU) {
                              hardwareCapabilityProvider.clearEmbeddingBackendAttempting()
                              hardwareCapabilityProvider.setPreferredEmbeddingBackend(backend)
                          }
                          isEmbeddingLoaded = true
                          Log.i(TAG, "Embedding model loaded successfully with $backend")
                          return@withContext Result.success(true)
                    } else {
                        Log.w(TAG, "Embedding backend $backend failed (returned false)")
                        if (backend != BackendType.CPU) {
                            hardwareCapabilityProvider.markBackendFailed(backend)
                            hardwareCapabilityProvider.clearEmbeddingBackendAttempting()
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Embedding backend $backend timed out after ${LOAD_TIMEOUT_MS}ms", e)
                    if (backend != BackendType.CPU) {
                        hardwareCapabilityProvider.markBackendFailed(backend)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception loading embedding model with $backend", e)
                    if (backend != BackendType.CPU) {
                        hardwareCapabilityProvider.markBackendFailed(backend)
                        hardwareCapabilityProvider.clearEmbeddingBackendAttempting()
                    }
                }
            }
            
            Log.e(TAG, "Failed to load embedding model with all backends")
            Result.failure(Exception("Failed to load embedding model with all available backends"))
        }
    }

     fun completionFlow(prompt: String): Flow<String> = callbackFlow {
         launch(Dispatchers.IO) {
             mutex.withLock {
                 if (!isChatLoaded) {
                     close(IllegalStateException("Model not loaded"))
                     return@withLock
                 }
                
                try {
                    val callback = object : LlmCallback {
                        override fun onToken(token: String) {
                            trySendBlocking(token)
                                .onFailure { e ->
                                    Log.w(TAG, "Failed to send token: ${e?.message}")
                                }
                        }
                    }
                    llmContext.completion(prompt, DEFAULT_SYSTEM_PROMPT, DEFAULT_STOP_SEQUENCES, callback)
                    close()
                } catch (e: Exception) {
                    close(e)
                }
            }
        }
        awaitClose { 
            llmContext.stopCompletion()
        }
    }
    
    suspend fun stopGeneration() {
        llmContext.stopCompletion()
    }

     suspend fun completion(prompt: String): String = withContext(Dispatchers.IO) {
         mutex.withLock {
             if (!isChatLoaded) throw IllegalStateException("Chat model not loaded")
             llmContext.completion(prompt, DEFAULT_SYSTEM_PROMPT, DEFAULT_STOP_SEQUENCES)
         }
     }

     suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
         mutex.withLock {
             if (!isEmbeddingLoaded) throw IllegalStateException("Embedding model not loaded")
             llmContext.embed(text)
         }
     }

     suspend fun release() {
         mutex.withLock {
             if (isChatLoaded) {
                 llmContext.unloadChat()
                 isChatLoaded = false
             }
             if (isEmbeddingLoaded) {
                 llmContext.unloadEmbedding()
                 isEmbeddingLoaded = false
             }
         }
     }
}