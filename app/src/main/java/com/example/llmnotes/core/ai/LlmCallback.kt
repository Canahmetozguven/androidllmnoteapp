package com.example.llmnotes.core.ai

interface LlmCallback {
    fun onToken(token: String)
}
