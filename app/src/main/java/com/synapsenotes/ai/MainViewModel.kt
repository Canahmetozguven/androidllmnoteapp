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

@HiltViewModel
class MainViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val llmEngine: LlmEngine,
    private val modelManager: ModelManager,
    private val hardwareCapabilityProvider: HardwareCapabilityProvider
) : ViewModel() {

    val isOnboardingCompleted: Boolean
        get() = appPreferences.onboardingCompleted

    init {
        restoreModels()
    }

    private fun restoreModels() {
        viewModelScope.launch {
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
                    // TODO: Notify user via UI event if needed, for now just log and skip to prevent crash
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
        }
    }
}
