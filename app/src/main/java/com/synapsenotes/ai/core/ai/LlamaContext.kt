package com.synapsenotes.ai.core.ai

class LlamaContext {
    companion object {
        var isLibraryLoaded = false
            private set

        init {
            try {
                System.loadLibrary("llm_notes_cpp")
                isLibraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e("LlamaContext", "Failed to load native library: llm_notes_cpp", e)
                isLibraryLoaded = false
            }
        }
    }

    external fun loadModelNative(path: String, template: String?, nBatch: Int, nCtx: Int, useMmap: Boolean, backendId: Int): Boolean
    external fun loadEmbeddingModelNative(path: String): Boolean
    external fun completion(prompt: String, callback: LlmCallback): String
    external fun stopCompletion()
    external fun embed(text: String): FloatArray
    external fun unload()
    external fun isGpuEnabled(): Boolean
    external fun isOpenCLAvailable(): Boolean
}
