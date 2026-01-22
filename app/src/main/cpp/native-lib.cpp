#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include <sstream>
#include <cmath>
#include <atomic>
#include "llama.h"

#define TAG "LLM_JNI"

// Global state
llama_model* g_model = nullptr;
llama_context* g_context = nullptr;
llama_model* g_model_embed = nullptr;
llama_context* g_context_embed = nullptr;
bool g_gpu_enabled = false;
std::string g_chat_template;
std::atomic<bool> g_stop_requested(false);

// Logging callback
static void android_log_callback(ggml_log_level level, const char * text, void * user_data) {
    int android_level = ANDROID_LOG_INFO;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: android_level = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  android_level = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_INFO:  android_level = ANDROID_LOG_INFO;  break;
        default:                   android_level = ANDROID_LOG_DEBUG; break;
    }
    std::string msg(text);
    if (!msg.empty() && msg.back() == '\n') {
        msg.pop_back();
    }
    __android_log_print(android_level, "LLAMA_CPP", "%s", msg.c_str());
}

// Helper to add token to batch
static void batch_add(llama_batch & batch, llama_token id, llama_pos pos, int32_t seq_id, bool logits) {
    batch.token[batch.n_tokens] = id;
    batch.pos[batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = 1;
    batch.seq_id[batch.n_tokens][0] = seq_id;
    batch.logits[batch.n_tokens] = logits ? 1 : 0;
    batch.n_tokens++;
}

// Forward declarations
extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_example_llmnotes_core_ai_LlamaContext_loadModelNative(JNIEnv* env, jobject, jstring path, jstring template_str);
    JNIEXPORT jboolean JNICALL Java_com_example_llmnotes_core_ai_LlamaContext_loadEmbeddingModelNative(JNIEnv* env, jobject, jstring path);
    JNIEXPORT jstring JNICALL Java_com_example_llmnotes_core_ai_LlamaContext_completion(JNIEnv* env, jobject, jstring prompt, jobject callback);
    JNIEXPORT void JNICALL Java_com_example_llmnotes_core_ai_LlamaContext_stopCompletion(JNIEnv* env, jobject);
    JNIEXPORT jboolean JNICALL Java_com_example_llmnotes_core_ai_LlamaContext_isGpuEnabled(JNIEnv* env, jobject);
    JNIEXPORT jfloatArray JNICALL Java_com_example_llmnotes_core_ai_LlamaContext_embed(JNIEnv* env, jobject, jstring text);
    JNIEXPORT void JNICALL Java_com_example_llmnotes_core_ai_LlamaContext_unload(JNIEnv* env, jobject);
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "JNI_OnLoad: Initializing llama.cpp backend [Build: 2026-01-21 v5 - Dynamic Config + Stop]");
    
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Register natives explicitly to avoid UnsatisfiedLinkError issues
    jclass clazz = env->FindClass("com/example/llmnotes/core/ai/LlamaContext");
    if (clazz == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to find LlamaContext class");
        return JNI_ERR;
    }

    JNINativeMethod methods[] = {
        {"loadModelNative", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*)Java_com_example_llmnotes_core_ai_LlamaContext_loadModelNative},
        {"loadEmbeddingModelNative", "(Ljava/lang/String;)Z", (void*)Java_com_example_llmnotes_core_ai_LlamaContext_loadEmbeddingModelNative},
        {"completion", "(Ljava/lang/String;Lcom/example/llmnotes/core/ai/LlmCallback;)Ljava/lang/String;", (void*)Java_com_example_llmnotes_core_ai_LlamaContext_completion},
        {"stopCompletion", "()V", (void*)Java_com_example_llmnotes_core_ai_LlamaContext_stopCompletion},
        {"isGpuEnabled", "()Z", (void*)Java_com_example_llmnotes_core_ai_LlamaContext_isGpuEnabled},
        {"embed", "(Ljava/lang/String;)[F", (void*)Java_com_example_llmnotes_core_ai_LlamaContext_embed},
        {"unload", "()V", (void*)Java_com_example_llmnotes_core_ai_LlamaContext_unload}
    };

    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to register native methods");
        return JNI_ERR;
    }

    llama_backend_init();
    llama_log_set(android_log_callback, nullptr);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_llmnotes_core_ai_LlamaContext_loadEmbeddingModelNative(JNIEnv* env, jobject, jstring path) {
    const char* model_path = env->GetStringUTFChars(path, nullptr);

    if (g_context_embed) {
        llama_free(g_context_embed);
        g_context_embed = nullptr;
    }
    if (g_model_embed) {
        llama_model_free(g_model_embed);
        g_model_embed = nullptr;
    }

    struct llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = -1; // Offload all layers to GPU (Vulkan)
    
    __android_log_print(ANDROID_LOG_INFO, TAG, "Attempting to load embedding model from %s...", model_path);
    g_model_embed = llama_model_load_from_file(model_path, model_params);

    if (!g_model_embed) {
        model_params.n_gpu_layers = 0;
        g_model_embed = llama_model_load_from_file(model_path, model_params);
    }

    env->ReleaseStringUTFChars(path, model_path);

    if (!g_model_embed) return JNI_FALSE;

    struct llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = true; // IMPORTANT
    ctx_params.n_batch = 2048; // Can be larger for embeddings
    
    g_context_embed = llama_init_from_model(g_model_embed, ctx_params);
    if (!g_context_embed) {
         llama_model_free(g_model_embed);
         g_model_embed = nullptr;
         return JNI_FALSE;
    }
    
    __android_log_print(ANDROID_LOG_INFO, TAG, "Embedding model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_llmnotes_core_ai_LlamaContext_loadModelNative(JNIEnv* env, jobject, jstring path, jstring template_str) {
    const char* model_path = env->GetStringUTFChars(path, nullptr);
    
    if (template_str != nullptr) {
        const char* tmpl = env->GetStringUTFChars(template_str, nullptr);
        g_chat_template = std::string(tmpl);
        env->ReleaseStringUTFChars(template_str, tmpl);
        __android_log_print(ANDROID_LOG_INFO, TAG, "Loaded custom chat template");
    } else {
        g_chat_template = "";
    }

    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    struct llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = -1; // Try GPU
    
    __android_log_print(ANDROID_LOG_INFO, TAG, "Attempting to load chat model from %s...", model_path);
    g_model = llama_model_load_from_file(model_path, model_params);
    g_gpu_enabled = (g_model != nullptr);

    if (!g_model) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to load chat model with GPU, falling back to CPU...");
        model_params.n_gpu_layers = 0;
        g_model = llama_model_load_from_file(model_path, model_params);
        g_gpu_enabled = false;
    }

    env->ReleaseStringUTFChars(path, model_path);

    if (!g_model) return JNI_FALSE;

    struct llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096; 
    ctx_params.n_batch = 512;
    
    g_context = llama_init_from_model(g_model, ctx_params);
    if (!g_context) {
         llama_model_free(g_model);
         g_model = nullptr;
         return JNI_FALSE;
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_llmnotes_core_ai_LlamaContext_stopCompletion(JNIEnv* env, jobject) {
    g_stop_requested = true;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Stop requested");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_llmnotes_core_ai_LlamaContext_completion(JNIEnv* env, jobject, jstring prompt, jobject callback) {
    if (!g_context) return env->NewStringUTF("Error: Model not loaded");
    
    g_stop_requested = false;
    
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    
    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string user_prompt(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    // Prepare messages for template
    std::vector<llama_chat_message> messages;
    std::string system_content = "You are a helpful AI assistant integrated into a notes app. Use the provided context to answer questions accurately.\n\nIMPORTANT: If the user asks in Turkish, answer in Turkish. You must wrap your internal reasoning and thought process inside <think> and </think> tags. The final answer should be outside these tags.";
    
    messages.push_back({"system", system_content.c_str()});
    messages.push_back({"user", user_prompt.c_str()});

    std::vector<char> formatted_prompt(8192);
    int32_t res = -1;
    
    // 1. Use custom downloaded template if available
    if (!g_chat_template.empty()) {
         res = llama_chat_apply_template(g_chat_template.c_str(), messages.data(), messages.size(), true, formatted_prompt.data(), formatted_prompt.size());
    } 
    // 2. Otherwise try model's built-in template
    else {
         res = llama_chat_apply_template(llama_model_chat_template(g_model, nullptr), messages.data(), messages.size(), true, formatted_prompt.data(), formatted_prompt.size());
    }
    
    std::string final_prompt_str;

    if (res > 0) {
        if (res > formatted_prompt.size()) {
            formatted_prompt.resize(res);
            if (!g_chat_template.empty()) {
                 res = llama_chat_apply_template(g_chat_template.c_str(), messages.data(), messages.size(), true, formatted_prompt.data(), formatted_prompt.size());
            } else {
                 res = llama_chat_apply_template(llama_model_chat_template(g_model, nullptr), messages.data(), messages.size(), true, formatted_prompt.data(), formatted_prompt.size());
            }
        }
        final_prompt_str = std::string(formatted_prompt.data(), res);
        __android_log_print(ANDROID_LOG_INFO, TAG, "Successfully applied chat template.");
    } else {
        // 3. Fallback to manual ChatML
        __android_log_print(ANDROID_LOG_WARN, TAG, "Template application failed. Falling back to manual ChatML.");
        std::stringstream ss;
        ss << "<|im_start|>system\n" << system_content << "<|im_end|>\n"
           << "<|im_start|>user\n" << user_prompt << "<|im_end|>\n"
           << "<|im_start|>assistant\n";
        final_prompt_str = ss.str();
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Final Prompt sent to tokenize: %s", final_prompt_str.substr(0, 500).c_str());

    const char* final_prompt = final_prompt_str.c_str();
    int prompt_length = final_prompt_str.length();

    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);

    std::vector<llama_token> tokens_list;
    tokens_list.resize(prompt_length + 100); 
    int n_tokens = llama_tokenize(vocab, final_prompt, prompt_length, tokens_list.data(), tokens_list.size(), true, true);
    if (n_tokens < 0) {
        tokens_list.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, final_prompt, prompt_length, tokens_list.data(), tokens_list.size(), true, true);
    }
    tokens_list.resize(n_tokens);

    llama_memory_seq_rm(llama_get_memory(g_context), -1, -1, -1);

    llama_batch batch = llama_batch_init(2048, 0, 1);

    for (int i = 0; i < n_tokens; i += 512) {
        int n_chunk = n_tokens - i;
        if (n_chunk > 512) n_chunk = 512;
        
        batch.n_tokens = 0;
        for (int j = 0; j < n_chunk; j++) {
            batch_add(batch, tokens_list[i + j], i + j, 0, false);
        }
        
        if (i + n_chunk == n_tokens) {
            batch.logits[batch.n_tokens - 1] = 1;
        }

        if (llama_decode(g_context, batch) != 0) {
            llama_batch_free(batch);
            return env->NewStringUTF("Error: llama_decode failed during prompt processing");
        }
    }

    struct llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    struct llama_sampler * sampler = llama_sampler_chain_init(sparams);
    
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string result_str = "";
    int n_cur = n_tokens;
    int n_decode = 0;
    const int max_tokens = 2048; 
    
    const std::vector<std::string> stop_sequences = {
        "<｜User｜>", "<｜Assistant｜>", "<｜end▁of▁sentence｜>", 
        "<|im_end|>", "<|im_start|>", 
        "</s>", "<|endoftext|>"
    };

    while (n_decode < max_tokens && n_cur < llama_n_ctx(g_context)) {
        if (g_stop_requested) {
            __android_log_print(ANDROID_LOG_INFO, TAG, "Generation stopped by user.");
            break;
        }

        llama_token new_token_id = llama_sampler_sample(sampler, g_context, -1);

        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            result_str += piece;
            
            jstring jPiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jPiece);
            env->DeleteLocalRef(jPiece);
            
            bool stop = false;
            for (const auto& seq : stop_sequences) {
                if (result_str.length() >= seq.length()) {
                    if (result_str.substr(result_str.length() - seq.length()) == seq) {
                        stop = true;
                        break;
                    }
                    if (piece.find(seq) != std::string::npos) {
                        stop = true;
                        break;
                    }
                }
            }
            if (stop) break;
        }

        batch.n_tokens = 0;
        batch_add(batch, new_token_id, n_cur, 0, true);
        n_cur++;
        n_decode++;

        if (llama_decode(g_context, batch) != 0) break;
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);
    
    return env->NewStringUTF(result_str.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_llmnotes_core_ai_LlamaContext_isGpuEnabled(JNIEnv* env, jobject) {
    return g_gpu_enabled ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_llmnotes_core_ai_LlamaContext_embed(JNIEnv* env, jobject, jstring text) {
    if (!g_context_embed && !g_context) return nullptr;
    
    llama_context* ctx = g_context_embed ? g_context_embed : g_context;
    llama_model* model = g_context_embed ? g_model_embed : g_model;
    
    const char* text_cstr = env->GetStringUTFChars(text, nullptr);
    const struct llama_vocab * vocab = llama_model_get_vocab(model);

    std::vector<llama_token> tokens;
    tokens.resize(strlen(text_cstr) + 100);
    int n_tokens = llama_tokenize(vocab, text_cstr, strlen(text_cstr), tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, text_cstr, strlen(text_cstr), tokens.data(), tokens.size(), true, true);
    }
    tokens.resize(n_tokens);
    env->ReleaseStringUTFChars(text, text_cstr);

    if (n_tokens == 0) return env->NewFloatArray(0);

    // Clear context for embedding
    llama_memory_seq_rm(llama_get_memory(ctx), -1, -1, -1);

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        batch_add(batch, tokens[i], i, 0, (i == n_tokens - 1));
    }

    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        return nullptr;
    }

    int32_t n_embd = llama_n_embd(model);
    float* embeddings = llama_get_embeddings_seq(ctx, 0); // seq_id 0
    
    if (!embeddings) {
        embeddings = llama_get_embeddings(ctx); // fallback
    }

    if (!embeddings) {
        llama_batch_free(batch);
        return nullptr;
    }

    // Normalize
    float norm = 0.0f;
    for (int i = 0; i < n_embd; i++) norm += embeddings[i] * embeddings[i];
    norm = sqrt(norm);
    
    std::vector<float> norm_embd(n_embd);
    for (int i = 0; i < n_embd; i++) norm_embd[i] = embeddings[i] / norm;

    jfloatArray result = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(result, 0, n_embd, norm_embd.data());

    llama_batch_free(batch);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_llmnotes_core_ai_LlamaContext_unload(JNIEnv* env, jobject) {
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    if (g_context_embed) {
        llama_free(g_context_embed);
        g_context_embed = nullptr;
    }
    if (g_model_embed) {
        llama_model_free(g_model_embed);
        g_model_embed = nullptr;
    }
    g_gpu_enabled = false;
}
