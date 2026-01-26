package com.synapsenotes.ai.core.ai

import javax.inject.Inject

/**
 * Interface for LLM context operations.
 * Abstracts the native LlamaContext for testability.
 */
interface LlmContext {
    fun loadModel(path: String, template: String? = null, nBatch: Int = 512, nCtx: Int = 2048, useMmap: Boolean = true, backendType: BackendType): Boolean
    fun loadEmbeddingModel(path: String): Boolean
    fun completion(prompt: String, callback: LlmCallback? = null): String
    fun stopCompletion()
    fun embed(text: String): FloatArray
    fun unload()
    fun isGpuEnabled(): Boolean
    fun isOpenCLAvailable(): Boolean
}

/**
 * Default implementation backed by native llama.cpp library.
 */
class DefaultLlmContext @Inject constructor() : LlmContext {
    private val nativeContext = LlamaContext()
    
    private fun isLibraryLoaded(): Boolean {
        return LlamaContext.isLibraryLoaded
    }
    
    override fun loadModel(path: String, template: String?, nBatch: Int, nCtx: Int, useMmap: Boolean, backendType: BackendType): Boolean {
        if (!isLibraryLoaded()) return false
        return nativeContext.loadModelNative(path, template, nBatch, nCtx, useMmap, backendType.ordinal)
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

    override fun isOpenCLAvailable(): Boolean {
        if (!isLibraryLoaded()) return false
        return nativeContext.isOpenCLAvailable()
    }
}
