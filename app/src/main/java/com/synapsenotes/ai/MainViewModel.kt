package com.synapsenotes.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapsenotes.ai.core.ai.LlmEngine
import com.synapsenotes.ai.core.ai.ModelManager
import com.synapsenotes.ai.core.preferences.AppPreferences
import com.synapsenotes.ai.core.ai.HardwareCapabilityProvider
import com.synapsenotes.ai.feature.settings.AVAILABLE_MODELS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.File

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

@HiltViewModel
class MainViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val llmEngine: LlmEngine,
    private val modelManager: ModelManager,
    private val hardwareCapabilityProvider: HardwareCapabilityProvider
) : ViewModel() {

    val isOnboardingCompleted: Boolean
        get() = appPreferences.onboardingCompleted

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            restoreModels()
        }

        override fun onStop(owner: LifecycleOwner) {
            viewModelScope.launch {
                Log.i("MainViewModel", "App backgrounded: Releasing LLM engine resources")
                llmEngine.release()
            }
        }
    }

    init {
        // Register for process lifecycle events to handle memory management
        // Use the main thread for lifecycle registration
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        
        // Initial load (if not triggered by onStart already)
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
             restoreModels()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
    }

    private fun restoreModels() {
        if (appPreferences.safeMode) {
            Log.w("MainViewModel", "Safe Mode detected: Skipping auto-restore of models to prevent crash loop.")
            return
        }

        viewModelScope.launch {
            // Set Safe Mode ON before attempting load
            appPreferences.safeMode = true
            
            try {
                val chatFilename = appPreferences.activeChatModelFilename
                val embeddingFilename = appPreferences.activeEmbeddingModelFilename

                if (chatFilename != null && modelManager.isModelAvailable(chatFilename)) {
                    Log.i("MainViewModel", "Restoring chat model: $chatFilename")
                    val path = modelManager.getModelPath(chatFilename)
                    
                    // RAM Check
                    val modelInfo = AVAILABLE_MODELS.find { it.filename == chatFilename }
                    val modelSizeGb = if (modelInfo != null) {
                        modelInfo.sizeBytes / (1024.0 * 1024.0 * 1024.0)
                    } else {
                        File(path).length() / (1024.0 * 1024.0 * 1024.0)
                    }

                    val availableRam = hardwareCapabilityProvider.getAvailableRamGb()
                    val requiredAvailableRam = modelSizeGb + 0.5

                    if (availableRam < requiredAvailableRam) {
                        Log.w("MainViewModel", "Skipping auto-load: Low RAM. Available: %.1fGB, Need: %.1fGB".format(availableRam, requiredAvailableRam))
                    } else {
                        try {
                            llmEngine.loadModel(path)
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Failed to auto-load chat model", e)
                        }
                    }
                }

                if (embeddingFilename != null && modelManager.isModelAvailable(embeddingFilename)) {
                    Log.i("MainViewModel", "Restoring embedding model: $embeddingFilename")
                    val path = modelManager.getModelPath(embeddingFilename)
                    try {
                        llmEngine.loadEmbeddingModel(path)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to auto-load embedding model", e)
                    }
                }
            } finally {
                // Clear Safe Mode only if we survived the loading process (no native crash)
                appPreferences.safeMode = false
            }
        }
    }
}
