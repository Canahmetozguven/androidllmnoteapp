package com.example.llmnotes.core.ai

class LlamaContext {
    companion object {
        init {
            System.loadLibrary("llm_notes_cpp")
        }
    }

    external fun loadModelNative(path: String, template: String?): Boolean
    external fun loadEmbeddingModelNative(path: String): Boolean
    external fun completion(prompt: String, callback: LlmCallback): String
    external fun stopCompletion()
    external fun embed(text: String): FloatArray
    external fun unload()
    external fun isGpuEnabled(): Boolean
}
