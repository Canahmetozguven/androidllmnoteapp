package com.example.llmnotes.feature.settings

import android.app.ActivityManager
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
import com.example.llmnotes.core.preferences.AppPreferences
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val filename: String,
    val configUrl: String? = null,
    val description: String = "Lightweight",
    val sizeBytes: Long = 0L,
    val type: ModelType = ModelType.CHAT
)

enum class ModelType {
    CHAT, EMBEDDING
}

val AVAILABLE_MODELS = listOf(
    ModelInfo(
        id = "deepseek-r1-distill-qwen-1.5b",
        name = "DeepSeek R1 Distill Qwen 1.5B",
        url = "https://huggingface.co/second-state/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        filename = "deepseek-r1-distill-qwen-1.5b-q4_k_m.gguf",
        configUrl = "https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/tokenizer_config.json",
        description = "Reasoning Model",
        sizeBytes = 1_100_000_000L,
        type = ModelType.CHAT
    ),
    ModelInfo(
        id = "tinyllama", 
        name = "TinyLlama 1.1B (Q4_K_M)", 
        url = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf", 
        filename = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        configUrl = "https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0/resolve/main/tokenizer_config.json",
        description = "Fast & Efficient",
        sizeBytes = 669_000_000L,
        type = ModelType.CHAT
    ),
    ModelInfo(
        id = "phi2",
        name = "Phi-2 (Q4_K_M)",
        url = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
        filename = "phi-2.Q4_K_M.gguf",
        description = "High Accuracy",
        sizeBytes = 1_800_000_000L,
        type = ModelType.CHAT
    ),
    ModelInfo(
        id = "all-minilm-l6-v2",
        name = "All-MiniLM-L6-v2",
        url = "https://huggingface.co/second-state/All-MiniLM-L6-v2-Embedding-GGUF/resolve/main/all-MiniLM-L6-v2-ggml-model-f16.gguf",
        filename = "all-MiniLM-L6-v2-ggml-model-f16.gguf",
        description = "Fast Embedding (384 dim)",
        sizeBytes = 45_000_000L,
        type = ModelType.EMBEDDING
    ),
    ModelInfo(
        id = "bge-small-en-v1.5",
        name = "BGE Small En v1.5",
        url = "https://huggingface.co/second-state/BGE-Small-En-V1.5-GGUF/resolve/main/bge-small-en-v1.5-f16.gguf",
        filename = "bge-small-en-v1.5-f16.gguf",
        description = "High Quality Embedding",
        sizeBytes = 67_000_000L,
        type = ModelType.EMBEDDING
    )
)

data class SettingsUiState(
    val chatModels: List<ModelUiModel> = emptyList(),
    val embeddingModels: List<ModelUiModel> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val llmEngine: LlmEngine,
    private val googleSignInClient: GoogleSignInClient,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    private var activeModelId: String? = appPreferences.activeChatModelId
    private var activeEmbeddingModelId: String? = appPreferences.activeEmbeddingModelId

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    private fun checkRamSufficiency(): Boolean {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        
        val totalRamGb = memInfo.totalMem / (1024 * 1024 * 1024.0)
        return totalRamGb >= 3.5 
    }

    init {
        refreshModelStatus()
        
        viewModelScope.launch {
             workManager.getWorkInfosByTagFlow("model_download")
                 .collect { workInfos ->
                     updateDownloadStatus(workInfos)
                 }
        }
    }

    private fun refreshModelStatus() {
        val chatModels = AVAILABLE_MODELS.filter { it.type == ModelType.CHAT }.map { info ->
            ModelUiModel(
                info = info,
                isDownloaded = modelManager.isModelAvailable(info.filename),
                isDownloading = false,
                progress = 0,
                isLoaded = info.id == activeModelId,
                isLoadInProgress = false
            )
        }
        
        val embeddingModels = AVAILABLE_MODELS.filter { it.type == ModelType.EMBEDDING }.map { info ->
            ModelUiModel(
                info = info,
                isDownloaded = modelManager.isModelAvailable(info.filename),
                isDownloading = false,
                progress = 0,
                isLoaded = info.id == activeEmbeddingModelId,
                isLoadInProgress = false
            )
        }

        _uiState.value = SettingsUiState(chatModels, embeddingModels)
    }

    private fun updateDownloadStatus(workInfos: List<WorkInfo>) {
        val currentChat = _uiState.value.chatModels.toMutableList()
        val currentEmbed = _uiState.value.embeddingModels.toMutableList()
        
        workInfos.forEach { workInfo ->
            val filename = workInfo.tags.firstOrNull { it.endsWith(".gguf") } ?: return@forEach
            val progress = workInfo.progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
            
            val chatIndex = currentChat.indexOfFirst { it.info.filename == filename }
            if (chatIndex != -1) {
                val isRunning = workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
                val oldItem = currentChat[chatIndex]
                currentChat[chatIndex] = oldItem.copy(
                    isDownloading = isRunning,
                    progress = if (workInfo.state == WorkInfo.State.SUCCEEDED) 100 else progress,
                    isDownloaded = if (workInfo.state == WorkInfo.State.SUCCEEDED) true else oldItem.isDownloaded
                )
            }
            
            val embedIndex = currentEmbed.indexOfFirst { it.info.filename == filename }
            if (embedIndex != -1) {
                val isRunning = workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
                val oldItem = currentEmbed[embedIndex]
                currentEmbed[embedIndex] = oldItem.copy(
                    isDownloading = isRunning,
                    progress = if (workInfo.state == WorkInfo.State.SUCCEEDED) 100 else progress,
                    isDownloaded = if (workInfo.state == WorkInfo.State.SUCCEEDED) true else oldItem.isDownloaded
                )
            }
        }
        _uiState.value = SettingsUiState(currentChat, currentEmbed)
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
                // Warning logic...
            }

            val isEmbedding = info.type == ModelType.EMBEDDING
            
            updateLoadingState(info.id, true, isEmbedding)

            val path = modelManager.getModelPath(info.filename)
            
            val result = if (isEmbedding) {
                llmEngine.loadEmbeddingModel(path)
            } else {
                llmEngine.loadModel(path)
            }
            
            if (result.isSuccess) {
                if (isEmbedding) {
                    activeEmbeddingModelId = info.id
                    appPreferences.activeEmbeddingModelId = info.id
                    appPreferences.activeEmbeddingModelFilename = info.filename
                } else {
                    activeModelId = info.id
                    appPreferences.activeChatModelId = info.id
                    appPreferences.activeChatModelFilename = info.filename
                    
                    // Download config if available
                    if (info.configUrl != null) {
                        modelManager.downloadConfig(info.configUrl, info.filename)
                    }
                }
            }
            
            updateLoadingState(info.id, false, isEmbedding, result.isSuccess)
        }
    }
    
    private fun updateLoadingState(id: String, isLoading: Boolean, isEmbedding: Boolean, isSuccess: Boolean = false) {
        val currentChat = _uiState.value.chatModels.toMutableList()
        val currentEmbed = _uiState.value.embeddingModels.toMutableList()

        if (isEmbedding) {
            val list = currentEmbed
            val index = list.indexOfFirst { it.info.id == id }
            if (index != -1) {
                list[index] = list[index].copy(isLoadInProgress = isLoading, isLoaded = if (isLoading) false else isSuccess)
                if (isSuccess && !isLoading) {
                    for (i in list.indices) {
                        if (list[i].info.id != id) list[i] = list[i].copy(isLoaded = false)
                    }
                }
            }
        } else {
             val list = currentChat
            val index = list.indexOfFirst { it.info.id == id }
            if (index != -1) {
                list[index] = list[index].copy(isLoadInProgress = isLoading, isLoaded = if (isLoading) false else isSuccess)
                if (isSuccess && !isLoading) {
                    for (i in list.indices) {
                        if (list[i].info.id != id) list[i] = list[i].copy(isLoaded = false)
                    }
                }
            }
        }
        
        _uiState.value = SettingsUiState(currentChat, currentEmbed)
    }
}
