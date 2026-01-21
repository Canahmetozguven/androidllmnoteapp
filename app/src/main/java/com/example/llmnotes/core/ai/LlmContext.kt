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
    
    override fun loadModel(path: String, template: String?): Boolean = nativeContext.loadModelNative(path, template)
    override fun loadEmbeddingModel(path: String): Boolean = nativeContext.loadEmbeddingModelNative(path)
    override fun completion(prompt: String, callback: LlmCallback?): String = 
        nativeContext.completion(prompt, callback ?: object : LlmCallback { override fun onToken(token: String) {} })
    override fun stopCompletion() = nativeContext.stopCompletion()
    override fun embed(text: String): FloatArray = nativeContext.embed(text)
    override fun unload() = nativeContext.unload()
    override fun isGpuEnabled(): Boolean = nativeContext.isGpuEnabled()
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
