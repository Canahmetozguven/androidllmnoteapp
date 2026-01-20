#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include <sstream>
#include "llama.h"

#define TAG "LLM_JNI"

// Global state
llama_model* g_model = nullptr;
llama_context* g_context = nullptr;
bool g_gpu_enabled = false;

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

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "JNI_OnLoad: Initializing llama.cpp backend");
    llama_backend_init();
    llama_log_set(android_log_callback, nullptr);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_llmnotes_core_ai_LlamaContext_loadModel(JNIEnv* env, jobject, jstring path) {
    const char* model_path = env->GetStringUTFChars(path, nullptr);

    // Check file existence and size
    FILE* file = fopen(model_path, "rb");
    if (!file) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Model file not found: %s", model_path);
        env->ReleaseStringUTFChars(path, model_path);
        return JNI_FALSE;
    }
    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    fclose(file);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Model file size: %ld bytes", size);

    if (size < 1000000) { // < 1MB
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Model file too small (%ld), definitely corrupt", size);
        env->ReleaseStringUTFChars(path, model_path);
        return JNI_FALSE;
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
    model_params.n_gpu_layers = -1; // Offload all layers to GPU (Vulkan)
    
    __android_log_print(ANDROID_LOG_INFO, TAG, "Attempting to load model from %s with GPU...", model_path);
    g_model = llama_model_load_from_file(model_path, model_params);
    g_gpu_enabled = (g_model != nullptr);

    if (!g_model) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to load model with GPU, falling back to CPU...");
        model_params.n_gpu_layers = 0;
        g_model = llama_model_load_from_file(model_path, model_params);
        g_gpu_enabled = false;
    }

    env->ReleaseStringUTFChars(path, model_path);

    if (!g_model) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to load model even on CPU");
        return JNI_FALSE;
    }

    struct llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; 
    ctx_params.n_batch = 512;
    
    __android_log_print(ANDROID_LOG_INFO, TAG, "Creating context with n_ctx=2048, n_batch=512");
    g_context = llama_init_from_model(g_model, ctx_params);

    if (!g_context) {
         __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to create context");
        llama_model_free(g_model);
         g_model = nullptr;
         return JNI_FALSE;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Model and context loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_llmnotes_core_ai_LlamaContext_completion(JNIEnv* env, jobject, jstring prompt) {
    if (!g_context) return env->NewStringUTF("Error: Model not loaded");
    
    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    
    // Apply chat template with system message
    std::vector<llama_chat_message> messages;
    messages.push_back({"system", "You are a helpful assistant. Provide concise and accurate answers."});
    messages.push_back({"user", prompt_cstr});

    std::vector<char> formatted_prompt;
    int32_t res = llama_chat_apply_template(nullptr, messages.data(), messages.size(), true, nullptr, 0);
    
    if (res > 0) {
        formatted_prompt.resize(res + 1); 
        llama_chat_apply_template(nullptr, messages.data(), messages.size(), true, formatted_prompt.data(), res + 1);
    } else {
        formatted_prompt.resize(strlen(prompt_cstr) + 1);
        strcpy(formatted_prompt.data(), prompt_cstr);
    }

    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    
    const char* final_prompt = formatted_prompt.data();
    int prompt_length = strlen(final_prompt);

    __android_log_print(ANDROID_LOG_INFO, TAG, "Formatted prompt: %s", final_prompt);

    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);

    // Tokenize
    std::vector<llama_token> tokens_list;
    tokens_list.resize(prompt_length + 100); 
    int n_tokens = llama_tokenize(vocab, final_prompt, prompt_length, tokens_list.data(), tokens_list.size(), true, true);
    if (n_tokens < 0) {
        tokens_list.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, final_prompt, prompt_length, tokens_list.data(), tokens_list.size(), true, true);
    }
    tokens_list.resize(n_tokens);

    __android_log_print(ANDROID_LOG_INFO, TAG, "Prompt tokens: %d", n_tokens);

    // Clear KV cache (memory) before each completion
    llama_memory_seq_rm(llama_get_memory(g_context), -1, -1, -1);

    // Prepare batch
    llama_batch batch = llama_batch_init(2048, 0, 1);

    // Decode prompt in chunks
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

    // Sampler
    struct llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    struct llama_sampler * sampler = llama_sampler_chain_init(sparams);
    
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string result_str = "";
    int n_cur = n_tokens;
    int n_decode = 0;
    const int max_tokens = 512; 

    while (n_decode < max_tokens) {
        llama_token new_token_id = llama_sampler_sample(sampler, g_context, -1);

        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result_str += std::string(buf, n);
        }

        batch.n_tokens = 0;
        batch_add(batch, new_token_id, n_cur, 0, true);
        n_cur++;
        n_decode++;

        if (llama_decode(g_context, batch) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "llama_decode failed during generation");
            break;
        }
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Generated %d tokens", n_decode);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Response: %s", result_str.c_str());

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
     return env->NewFloatArray(0);
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
    g_gpu_enabled = false;
}
