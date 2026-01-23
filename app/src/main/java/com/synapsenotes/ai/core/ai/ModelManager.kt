package com.synapsenotes.ai.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelsDir: File
        get() = File(context.filesDir, "models")

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    fun isModelAvailable(modelName: String): Boolean {
        val file = File(modelsDir, modelName)
        return file.exists() && file.length() > 0
    }

    fun getModelPath(modelName: String): String {
        return File(modelsDir, modelName).absolutePath
    }
    
    fun getConfigPath(modelFilename: String): String {
        return File(modelsDir, "${modelFilename}.json").absolutePath
    }
    
    fun downloadConfig(url: String, modelFilename: String) {
        // Run in background thread
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val destFile = File(modelsDir, "${modelFilename}.json")
                if (destFile.exists()) return@launch // Already downloaded
                
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connect()
                
                if (connection.responseCode == 200) {
                    connection.inputStream.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun getAvailableModels(): List<File> {
        return modelsDir.listFiles()?.filter { it.name.endsWith(".gguf") }?.toList() ?: emptyList()
    }
}
