package com.example.llmnotes.core.ai

import javax.inject.Inject

/**
 * Interface for LLM context operations.
 * Abstracts the native LlamaContext for testability.
 */
interface LlmContext {
    fun loadModel(path: String, template: String? = null): Boolean
    fun loadEmbeddingModel(path: String): Boolean
    fun completion(prompt: String, callback: LlmCallback? = null): String
    fun stopCompletion()
    fun embed(text: String): FloatArray
    fun unload()
    fun isGpuEnabled(): Boolean
}

/**
 * Default implementation backed by native llama.cpp library.
 */
class DefaultLlmContext @Inject constructor() : LlmContext {
    private val nativeContext = LlamaContext()
    
    private fun isLibraryLoaded(): Boolean {
        return LlamaContext.isLibraryLoaded
    }
    
    override fun loadModel(path: String, template: String?): Boolean {
        if (!isLibraryLoaded()) return false
        return nativeContext.loadModelNative(path, template)
    }

    override fun loadEmbeddingModel(path: String): Boolean {
        if (!isLibraryLoaded()) return false
        return nativeContext.loadEmbeddingModelNative(path)
    }

    override fun completion(prompt: String, callback: LlmCallback?): String {
        if (!isLibraryLoaded()) return "Error: Native library not loaded"
        return nativeContext.completion(prompt, callback ?: object : LlmCallback { override fun onToken(token: String) {} })
    }

    override fun stopCompletion() {
        if (isLibraryLoaded()) {
            nativeContext.stopCompletion()
        }
    }

    override fun embed(text: String): FloatArray {
        if (!isLibraryLoaded()) return floatArrayOf()
        return nativeContext.embed(text)
    }

    override fun unload() {
        if (isLibraryLoaded()) {
            nativeContext.unload()
        }
    }

    override fun isGpuEnabled(): Boolean {
        if (!isLibraryLoaded()) return false
        return nativeContext.isGpuEnabled()
    }
}

/**
 * Mock implementation for unit testing.
 */
class MockLlmContext : LlmContext {
    var loadModelResult = true
    var completionResult = "Mock completion"
    var embedResult = floatArrayOf()
    var isGpuEnabledResult = true
    
    override fun loadModel(path: String, template: String?): Boolean = loadModelResult
    override fun loadEmbeddingModel(path: String): Boolean = loadModelResult
    override fun completion(prompt: String, callback: LlmCallback?): String {
        callback?.onToken(completionResult)
        return completionResult
    }
    override fun stopCompletion() {}
    override fun embed(text: String): FloatArray = embedResult
    override fun unload() {}
    override fun isGpuEnabled(): Boolean = isGpuEnabledResult
}
