package com.example.llmnotes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llmnotes.core.ai.LlmEngine
import com.example.llmnotes.core.ai.ModelManager
import com.example.llmnotes.core.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val llmEngine: LlmEngine,
    private val modelManager: ModelManager
) : ViewModel() {

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
                try {
                    llmEngine.loadModel(path)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to auto-load chat model", e)
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
