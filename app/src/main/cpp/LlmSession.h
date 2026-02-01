#ifndef LLM_SESSION_H
#define LLM_SESSION_H

#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include <memory>
#include <android/log.h>
#include "llama.h"

#define TAG "LLM_JNI"

class LlmSession {
public:
    LlmSession() : model(nullptr), context(nullptr), model_embed(nullptr), context_embed(nullptr), gpu_enabled(false), stop_requested(false) {}
    
    ~LlmSession() {
        unload();
    }

    void unload() {
        std::lock_guard<std::mutex> lock(session_mutex);
        if (context) {
            llama_free(context);
            context = nullptr;
        }
        if (model) {
            llama_model_free(model);
            model = nullptr;
        }
        if (context_embed) {
            llama_free(context_embed);
            context_embed = nullptr;
        }
        if (model_embed) {
            llama_model_free(model_embed);
            model_embed = nullptr;
        }
        gpu_enabled = false;
        chat_template.clear();
    }

    // Chat Model State
    llama_model* model;
    llama_context* context;
    std::string chat_template;
    bool gpu_enabled;
    std::atomic<bool> stop_requested;

    // Embedding Model State
    llama_model* model_embed;
    llama_context* context_embed;

    // Concurrency Control
    std::mutex session_mutex;
};

// Global session pointer
static std::unique_ptr<LlmSession> g_session;

// Helper to get or create the global session
static LlmSession* get_session() {
    if (!g_session) {
        g_session = std::make_unique<LlmSession>();
    }
    return g_session.get();
}

#endif // LLM_SESSION_H
