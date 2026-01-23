package com.synapsenotes.ai.core.ai

interface LlmCallback {
    fun onToken(token: String)
}
