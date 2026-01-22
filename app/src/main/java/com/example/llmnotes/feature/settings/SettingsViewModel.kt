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
import com.example.llmnotes.core.ai.HardwareCapabilityProvider
import com.example.llmnotes.core.network.DownloadWorker
import com.example.llmnotes.core.preferences.AppPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.example.llmnotes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.first
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
        id = "qwen2.5-3b-instruct",
        name = "Qwen 2.5 3B Instruct",
        url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
        filename = "qwen2.5-3b-instruct-q4_k_m.gguf",
        configUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct/resolve/main/tokenizer_config.json",
        description = "Best for Bilingual RAG",
        sizeBytes = 2_000_000_000L,
        type = ModelType.CHAT
    ),
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
        id = "kumru-2b",
        name = "Kumru 2B (Turkish)",
        url = "https://huggingface.co/Koc-Lab/Kumru-2B-GGUF/resolve/main/kumru-2b-q4_k_m.gguf",
        filename = "kumru-2b-q4_k_m.gguf",
        description = "Native Turkish Specialist",
        sizeBytes = 1_500_000_000L,
        type = ModelType.CHAT
    ),
    ModelInfo(
        id = "qwen2.5-1.5b-instruct",
        name = "Qwen 2.5 1.5B Instruct",
        url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        description = "Low RAM Fallback",
        sizeBytes = 980_000_000L,
        type = ModelType.CHAT
    ),
    ModelInfo(
        id = "nomic-embed-text-v1.5",
        name = "Nomic Embed v1.5",
        url = "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/nomic-embed-text-v1.5.Q8_0.gguf",
        filename = "nomic-embed-text-v1.5.Q8_0.gguf",
        description = "Matryoshka / High Accuracy",
        sizeBytes = 137_000_000L,
        type = ModelType.EMBEDDING
    ),
    ModelInfo(
        id = "multilingual-e5-small",
        name = "Multilingual E5 Small",
        url = "https://huggingface.co/second-state/Multilingual-E5-Small-GGUF/resolve/main/multilingual-e5-small-q8_0.gguf",
        filename = "multilingual-e5-small-q8_0.gguf",
        description = "Lightweight Fallback",
        sizeBytes = 120_000_000L,
        type = ModelType.EMBEDDING
    )
)

data class SettingsUiState(
    val chatModels: List<ModelUiModel> = emptyList(),
    val embeddingModels: List<ModelUiModel> = emptyList(),
    val isGoogleDriveConnected: Boolean = false,
    val userEmail: String? = null,
    val isSyncing: Boolean = false,
    val syncStatusMessage: String? = null,
    val lastSyncedTimestamp: Long = 0L
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val llmEngine: LlmEngine,
    private val hardwareCapabilityProvider: HardwareCapabilityProvider,
    private val googleSignInClient: GoogleSignInClient,
    private val appPreferences: AppPreferences,
    private val driveRepository: com.example.llmnotes.core.data.repository.GoogleDriveRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    private var activeModelId: String? = appPreferences.activeChatModelId
    private var activeEmbeddingModelId: String? = appPreferences.activeEmbeddingModelId

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(intent: Intent?) {
        try {
            android.util.Log.d("SettingsViewModel", "Handling sign in result intent: $intent")
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)
            android.util.Log.d("SettingsViewModel", "Sign in successful: ${account.email}")
            onGoogleSignInSuccess(account)
        } catch (e: ApiException) {
            android.util.Log.e("SettingsViewModel", "Sign in failed code: ${e.statusCode}", e)
            e.printStackTrace()
        }
    }

    private fun checkGoogleSignInStatus() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            onGoogleSignInSuccess(account)
        }
    }

    private fun onGoogleSignInSuccess(account: GoogleSignInAccount) {
        _uiState.value = _uiState.value.copy(
            isGoogleDriveConnected = true,
            userEmail = account.email,
            lastSyncedTimestamp = appPreferences.lastSyncTimestamp
        )
    }

    fun syncNotes() {
        if (_uiState.value.isSyncing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncStatusMessage = null)
            
            try {
                val notes = noteRepository.getAllNotes().first()
                if (notes.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isSyncing = false, syncStatusMessage = "No notes to sync")
                    return@launch
                }

                val lastSync = appPreferences.lastSyncTimestamp
                val notesToSync = notes.filter { it.updatedAt > lastSync }

                if (notesToSync.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isSyncing = false, syncStatusMessage = "Already synced")
                    return@launch
                }

                var successCount = 0
                var failCount = 0
                
                notesToSync.forEach { note ->
                    val fileName = "${note.title.ifEmpty { "Untitled" }}.txt"
                    val content = "Title: ${note.title}\n\n${note.content}"
                    
                    val result = driveRepository.uploadFile(fileName, content)
                    if (result != null) {
                        successCount++
                    } else {
                        failCount++
                    }
                }
                
                if (failCount == 0) {
                    val now = System.currentTimeMillis()
                    appPreferences.lastSyncTimestamp = now
                    _uiState.value = _uiState.value.copy(lastSyncedTimestamp = now)
                }

                val message = if (failCount == 0) "Synced $successCount notes" else "Synced $successCount, Failed $failCount"
                android.util.Log.d("SettingsViewModel", message)
                
                _uiState.value = _uiState.value.copy(isSyncing = false, syncStatusMessage = message)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Sync failed", e)
                _uiState.value = _uiState.value.copy(isSyncing = false, syncStatusMessage = "Sync failed")
            }
        }
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncStatusMessage = null)
    }

    init {
        checkGoogleSignInStatus()
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
            // Hardware Checks
            val totalRam = hardwareCapabilityProvider.getTotalRamGb()
            val modelSizeGb = info.sizeBytes / (1024.0 * 1024.0 * 1024.0)
            // Heuristic: Model size + 1.5GB for OS/App overhead
            val requiredRam = modelSizeGb + 1.5 

            if (totalRam < requiredRam) {
                val msg = "Warning: Low RAM. Need %.1fGB, found %.1fGB. App may crash.".format(requiredRam, totalRam)
                _uiState.value = _uiState.value.copy(syncStatusMessage = msg)
                // If severely constrained, block (e.g. less than model size itself + minimal overhead)
                if (totalRam < modelSizeGb + 0.5) {
                    return@launch
                }
            }

            if (!hardwareCapabilityProvider.isVulkanSupported()) {
                 _uiState.value = _uiState.value.copy(syncStatusMessage = "Vulkan not supported. Using CPU (Slow).")
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
            } else {
                _uiState.value = _uiState.value.copy(syncStatusMessage = "Failed to load model: ${result.exceptionOrNull()?.message}")
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
