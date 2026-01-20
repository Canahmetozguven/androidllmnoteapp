package com.example.llmnotes.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
    
    fun getAvailableModels(): List<File> {
        return modelsDir.listFiles()?.filter { it.name.endsWith(".gguf") }?.toList() ?: emptyList()
    }
}
