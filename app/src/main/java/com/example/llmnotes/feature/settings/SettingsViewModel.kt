package com.example.llmnotes.feature.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.llmnotes.core.ai.LlmEngine
import com.example.llmnotes.core.ai.ModelManager
import com.example.llmnotes.core.network.DownloadWorker
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import android.app.ActivityManager

data class ModelUiModel(
    val info: ModelInfo,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val progress: Int,
    val isLoaded: Boolean = false,
    val isLoadInProgress: Boolean = false
)

data class ModelInfo(
    val id: String,
    val name: String,
    val url: String,
    val filename: String
)

val AVAILABLE_MODELS = listOf(
    ModelInfo(
        id = "tinyllama", 
        name = "TinyLlama 1.1B (Q4_K_M)", 
        url = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf", 
        filename = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
    ),
    ModelInfo(
        id = "phi2",
        name = "Phi-2 (Q4_K_M)",
        url = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
        filename = "phi-2.Q4_K_M.gguf"
    )
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val llmEngine: LlmEngine,
    private val googleSignInClient: GoogleSignInClient
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    // In a real app, this would be a more complex flow combining DB/File status + WorkManager
    private val _uiState = MutableStateFlow<List<ModelUiModel>>(emptyList())
    val uiState: StateFlow<List<ModelUiModel>> = _uiState

    private var activeModelId: String? = null

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    private fun checkRamSufficiency(): Boolean {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        
        val totalRamGb = memInfo.totalMem / (1024 * 1024 * 1024.0)
        // Heuristic: Warn if less than 4GB RAM
        return totalRamGb >= 3.5 // Allow ~4GB devices
    }

    init {
        refreshModelStatus()
        
        // Observe WorkManager for active downloads
        // This is a simplified observation for the sake of the prototype
        viewModelScope.launch {
             workManager.getWorkInfosByTagFlow("model_download")
                 .collect { workInfos ->
                     updateDownloadStatus(workInfos)
                 }
        }
    }

    private fun refreshModelStatus() {
        val currentList = AVAILABLE_MODELS.map { info ->
            ModelUiModel(
                info = info,
                isDownloaded = modelManager.isModelAvailable(info.filename),
                isDownloading = false, // Will be updated by WorkManager
                progress = 0,
                isLoaded = info.id == activeModelId,
                isLoadInProgress = false
            )
        }
        _uiState.value = currentList
    }

    private fun updateDownloadStatus(workInfos: List<WorkInfo>) {
        val currentList = _uiState.value.toMutableList()
        
        workInfos.forEach { workInfo ->
            val filename = workInfo.tags.firstOrNull { it.endsWith(".gguf") } ?: return@forEach
            val progress = workInfo.progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
            
            val index = currentList.indexOfFirst { it.info.filename == filename }
            if (index != -1) {
                val isRunning = workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
                val oldItem = currentList[index]
                currentList[index] = oldItem.copy(
                    isDownloading = isRunning,
                    progress = if (workInfo.state == WorkInfo.State.SUCCEEDED) 100 else progress,
                    isDownloaded = if (workInfo.state == WorkInfo.State.SUCCEEDED) true else oldItem.isDownloaded
                )
            }
        }
        _uiState.value = currentList
    }


    fun downloadModel(info: ModelInfo) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(
                DownloadWorker.KEY_URL to info.url,
                DownloadWorker.KEY_FILENAME to info.filename
            ))
            .addTag("model_download")
            .addTag(info.filename)
            .build()

        workManager.enqueue(request)
    }

    fun loadModel(info: ModelInfo) {
        viewModelScope.launch {
            if (!checkRamSufficiency()) {
                // In a real app, emit a one-time event for UI toast/snackbar
                // For now, we proceed but log warning or could block
                // return@launch
            }

            // Set loading state
            _uiState.value = _uiState.value.map { 
                if (it.info.id == info.id) it.copy(isLoadInProgress = true) else it 
            }

            val path = modelManager.getModelPath(info.filename)
            val result = llmEngine.loadModel(path)
            
            if (result.isSuccess) {
                activeModelId = info.id
            }
            
            // Update UI with final state
            _uiState.value = _uiState.value.map {
                if (it.info.id == info.id) {
                    it.copy(
                        isLoadInProgress = false,
                        isLoaded = result.isSuccess
                    )
                } else {
                    // Unload others if successful
                    if (result.isSuccess) it.copy(isLoaded = false) else it
                }
            }
        }
    }
}
