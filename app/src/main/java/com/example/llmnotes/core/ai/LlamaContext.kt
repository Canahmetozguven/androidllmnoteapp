package com.example.llmnotes.core.ai

class LlamaContext {
    companion object {
        init {
            System.loadLibrary("llm_notes_cpp")
        }
    }

    external fun loadModel(path: String): Boolean
    external fun completion(prompt: String): String
    external fun embed(text: String): FloatArray
    external fun unload()
    external fun isGpuEnabled(): Boolean
}
