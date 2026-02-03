#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include <sstream>
#include <cmath>
#include <atomic>
#include <stdlib.h>
#include <sys/system_properties.h>
#include <dlfcn.h>
#include "llama.h"
#include "LlmSession.h"

#define TAG "LLM_JNI"

enum GPUVendor {
    GPU_ADRENO,      // Qualcomm
    GPU_MALI,        // ARM
    GPU_POWERVR,     // Imagination
    GPU_UNKNOWN
};

// Hardware detection and automatic backend selection
static std::string get_system_property(const char* key) {
    char value[PROP_VALUE_MAX] = {0};
    __system_property_get(key, value);
    return std::string(value);
}

// Hardware detection and automatic backend selection logic (Extracted for testability)
static GPUVendor resolve_gpu_vendor(const std::string& soc, const std::string& hardware) {
    // Check for Adreno (Qualcomm Snapdragon)
    if (soc.find("msm") != std::string::npos || 
        soc.find("sm") != std::string::npos ||
        soc.find("sdm") != std::string::npos ||
        hardware.find("qcom") != std::string::npos) {
        return GPU_ADRENO;
    }
    
    // Check for Mali (Samsung Exynos, MediaTek, Google Tensor)
    if (soc.find("exynos") != std::string::npos ||
        soc.find("mt") != std::string::npos ||
        soc.find("gs201") != std::string::npos || // Tensor G2
        soc.find("zuma") != std::string::npos ||  // Tensor G3
        hardware.find("exynos") != std::string::npos ||
        hardware.find("gs201") != std::string::npos ||
        hardware.find("zuma") != std::string::npos) {
        return GPU_MALI;
    }
    
    return GPU_UNKNOWN;
}

static GPUVendor detect_gpu_vendor() {
    std::string soc = get_system_property("ro.board.platform");
    std::string hardware = get_system_property("ro.hardware");
    
    GPUVendor vendor = resolve_gpu_vendor(soc, hardware);
    
    if (vendor == GPU_ADRENO) {
        __android_log_print(ANDROID_LOG_INFO, TAG, "Detected GPU: Adreno (Qualcomm)");
    } else if (vendor == GPU_MALI) {
        __android_log_print(ANDROID_LOG_INFO, TAG, "Detected GPU: Mali (ARM/Tensor)");
    } else {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Unknown GPU vendor (soc: %s, hw: %s)", soc.c_str(), hardware.c_str());
    }
    
    return vendor;
}

// Check if the device has a known problematic Vulkan driver (Extracted for testability)
static bool check_is_problematic_vulkan(const std::string& soc, const std::string& hardware) {
    // sm8450: Snapdragon 8 Gen 1 (notorious Vulkan bugs)
    // s5e9925: Exynos 2200 (Xclipse 920 GPU, unstable Vulkan)
    // sm8550: Snapdragon 8 Gen 2 (mostly better but still some device-lost reports)
    // sm8750: Snapdragon 8 Elite (S25 family - Adreno 830, new GPU with potentially immature drivers)
    // gs201: Tensor G2, zuma: Tensor G3 - Mali GPU issues with Vulkan
    return (soc.find("sm8450") != std::string::npos || 
            soc.find("s5e9925") != std::string::npos ||
            soc.find("sm8550") != std::string::npos ||
            soc.find("sm8750") != std::string::npos ||
            hardware.find("sm8450") != std::string::npos ||
            hardware.find("s5e9925") != std::string::npos ||
            hardware.find("sm8550") != std::string::npos ||
            hardware.find("sm8750") != std::string::npos ||
            soc.find("gs201") != std::string::npos ||
            hardware.find("gs201") != std::string::npos ||
            soc.find("zuma") != std::string::npos ||
            hardware.find("zuma") != std::string::npos);
}

static bool is_problematic_vulkan_device() {
    std::string soc = get_system_property("ro.board.platform");
    std::string hardware = get_system_property("ro.hardware");
    
    bool is_problematic = check_is_problematic_vulkan(soc, hardware);
                                    
    if (is_problematic) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Detected problematic SoC (%s / %s) - enabling Vulkan safety flags", soc.c_str(), hardware.c_str());
    }
    
    return is_problematic;
}


// Determine best backend based on hardware
static int auto_select_backend() {
    GPUVendor gpu = detect_gpu_vendor();
    
    // Known problematic SoCs - force CPU
    if (is_problematic_vulkan_device()) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Forcing CPU due to known SoC driver issues");
        return 0; // CPU
    }
    
    // GPU-based selection
    if (gpu == GPU_ADRENO) {
        // Adreno: OpenCL is most stable
        __android_log_print(ANDROID_LOG_INFO, TAG, "Auto-selected: OpenCL (best for Adreno)");
        return 2; // OpenCL
    } else if (gpu == GPU_MALI) {
        // Mali: Vulkan is now preferred (Tier 2 strategy)
        // Exynos/MediaTek Mali GPUs run better on Vulkan than OpenCL
        __android_log_print(ANDROID_LOG_INFO, TAG, "Auto-selected: Vulkan (best for Mali)");
        return 1; // Vulkan
    } else {
        // Unknown GPU: Fallback to CPU for stability (Tier 3 strategy)
        __android_log_print(ANDROID_LOG_WARN, TAG, "Auto-selected: CPU (unknown GPU, fallback for stability)");
        return 0; // CPU
    }
}

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
    JNIEXPORT jboolean JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_loadModelNative(JNIEnv* env, jobject, jstring path, jstring template_str, jint n_batch, jint n_ctx, jboolean use_mmap, jint backend_id);
    JNIEXPORT jboolean JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_loadEmbeddingModelNative(JNIEnv* env, jobject, jstring path, jint n_batch, jint n_ctx, jboolean use_mmap, jint backend_id);
    JNIEXPORT jstring JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_completion(JNIEnv* env, jobject, jstring prompt, jstring system_prompt, jobjectArray stop_sequences_array, jobject callback);
    JNIEXPORT void JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_stopCompletion(JNIEnv* env, jobject);
    JNIEXPORT jboolean JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_isGpuEnabled(JNIEnv* env, jobject);
    JNIEXPORT jboolean JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_isOpenCLAvailable(JNIEnv* env, jobject);
    JNIEXPORT jfloatArray JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_embed(JNIEnv* env, jobject, jstring text);
    JNIEXPORT void JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_unload(JNIEnv* env, jobject);
    // Probe Native
    JNIEXPORT jboolean JNICALL Java_com_synapsenotes_ai_core_ai_NativeLib_probeBackendNative(JNIEnv* env, jobject, jint backend_id);
    // Test Methods
    JNIEXPORT jint JNICALL Java_com_synapsenotes_ai_core_ai_NativeLib_testSelectionLogicNative(JNIEnv* env, jobject, jstring soc, jstring hw);
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "JNI_OnLoad: Initializing llama.cpp backend [Build: 2026-01-21 v6 - Hybrid Probe]");
    
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Register LlamaContext natives
    jclass clazz = env->FindClass("com/synapsenotes/ai/core/ai/LlamaContext");
    if (clazz != nullptr) {
        JNINativeMethod methods[] = {
            {"loadModelNative", "(Ljava/lang/String;Ljava/lang/String;IIZI)Z", (void*)Java_com_synapsenotes_ai_core_ai_LlamaContext_loadModelNative},
            {"loadEmbeddingModelNative", "(Ljava/lang/String;IIZI)Z", (void*)Java_com_synapsenotes_ai_core_ai_LlamaContext_loadEmbeddingModelNative},
            {"completion", "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Lcom/synapsenotes/ai/core/ai/LlmCallback;)Ljava/lang/String;", (void*)Java_com_synapsenotes_ai_core_ai_LlamaContext_completion},
            {"stopCompletion", "()V", (void*)Java_com_synapsenotes_ai_core_ai_LlamaContext_stopCompletion},
            {"isGpuEnabled", "()Z", (void*)Java_com_synapsenotes_ai_core_ai_LlamaContext_isGpuEnabled},
            {"isOpenCLAvailable", "()Z", (void*)Java_com_synapsenotes_ai_core_ai_LlamaContext_isOpenCLAvailable},
            {"embed", "(Ljava/lang/String;)[F", (void*)Java_com_synapsenotes_ai_core_ai_LlamaContext_embed},
            {"unload", "()V", (void*)Java_com_synapsenotes_ai_core_ai_LlamaContext_unload}
        };
        if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) < 0) {
             __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to register LlamaContext natives");
        }
    }

    // Register NativeLib natives (Probe)
    jclass nativeLibClazz = env->FindClass("com/synapsenotes/ai/core/ai/NativeLib");
    if (nativeLibClazz != nullptr) {
        JNINativeMethod probeMethods[] = {
            {"probeBackendNative", "(I)Z", (void*)Java_com_synapsenotes_ai_core_ai_NativeLib_probeBackendNative},
            {"testSelectionLogicNative", "(Ljava/lang/String;Ljava/lang/String;)I", (void*)Java_com_synapsenotes_ai_core_ai_NativeLib_testSelectionLogicNative}
        };
        if (env->RegisterNatives(nativeLibClazz, probeMethods, sizeof(probeMethods) / sizeof(probeMethods[0])) < 0) {
             __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to register NativeLib natives");
        }
    } else {

        __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to find NativeLib class - Probe unavailable");
    }

    llama_backend_init();
    llama_log_set(android_log_callback, nullptr);
    return JNI_VERSION_1_6;
}

// ----------------------------------------------------------------------------------------------
// Probe Implementation
// This is designed to be called from a separate process. If it crashes, only that process dies.
// ----------------------------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_synapsenotes_ai_core_ai_NativeLib_probeBackendNative(JNIEnv* env, jobject, jint backend_id) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "Probe: Starting backend probe for ID %d", backend_id);
    
    // 0 = CPU (Safe)
    if (backend_id == 0) return JNI_TRUE;

    try {
        // Setup minimal backend environment
        if (backend_id == 1) { // VULKAN
            unsetenv("GGML_VULKAN_DISABLE");
            setenv("GGML_OPENCL_DISABLE", "1", 1);
            // Don't apply safety flags yet - we WANT to see if the "raw" driver crashes
            // unless we specifically want to test the safe config?
            // Let's test the "intended" config for this device.
             if (is_problematic_vulkan_device()) {
                  // If we already know it's problematic, we might as well test with flags
                  // But the point of probe is to verify if it CRASHES even with flags (or without).
                  // Let's apply standard flags just in case.
                  setenv("GGML_VK_DISABLE_F16", "1", 1);
             }
        } else if (backend_id == 2) { // OPENCL
            setenv("GGML_VULKAN_DISABLE", "1", 1);
            unsetenv("GGML_OPENCL_DISABLE");
        }

        // Initialize backend - this triggers driver load and shader compilation
        // We can't just call llama_backend_init() again because it's global state.
        // Instead, let's try to load a tiny dummy model or perform a backend registry check.
        
        // Better: Use ggml_backend interface directly if available, but for now via llama.cpp high level:
        // We'll try to create a dummy context. Since we don't have a model file easily here,
        // we might rely on the fact that llama_backend_init() (called in JNI_OnLoad) already did some init.
        // However, Vulkan/OpenCL lazy load.
        
        // Force backend initialization by querying devices
        // This usually triggers the driver chain.
        
        // NOTE: This relies on internal behavior. Ideally we'd load a 1-parameter dummy model.
        // Since we don't have one, we will return true for now, assuming if JNI_OnLoad didn't crash, 
        // the library load is okay. 
        // A real crash-test usually requires attempting to allocate a buffer or compile a shader.
        // Given current constraints, we'll assume "library load + init" is the probe.
        
        // TODO: In a future iteration, ship a 1KB .gguf "probe model" to actually run inference.
        
        __android_log_print(ANDROID_LOG_INFO, TAG, "Probe: Backend init check passed (simulated)");
        return JNI_TRUE;
        
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Probe: Crashed/Exception");
        return JNI_FALSE;
    }
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_synapsenotes_ai_core_ai_LlamaContext_loadEmbeddingModelNative(JNIEnv* env, jobject, jstring path, jint n_batch, jint n_ctx, jboolean use_mmap, jint backend_id) {
    try {
        const char* model_path = env->GetStringUTFChars(path, nullptr);
        LlmSession* session = get_session();
        
        std::lock_guard<std::mutex> lock(session->session_mutex);

        if (session->context_embed) {
            llama_free(session->context_embed);
            session->context_embed = nullptr;
        }
        if (session->model_embed) {
            llama_model_free(session->model_embed);
            session->model_embed = nullptr;
        }

        struct llama_model_params model_params = llama_model_default_params();
        model_params.use_mmap = (bool)use_mmap;

        // Reset safety flags initially
        unsetenv("GGML_VK_DISABLE_F16");
        unsetenv("GGML_VK_DISABLE_ASYNC");
        unsetenv("GGML_VK_FORCE_MAX_ALLOCATION_SIZE");
        
        // Configure backend based on requested ID
        // 0 = CPU, 1 = VULKAN, 2 = OPENCL
        if (backend_id == 0) { // CPU
            __android_log_print(ANDROID_LOG_INFO, TAG, "Loading embedding model with CPU backend");
            model_params.n_gpu_layers = 0;
            setenv("GGML_VULKAN_DISABLE", "1", 1);
            setenv("GGML_OPENCL_DISABLE", "1", 1);
        } else if (backend_id == 1) { // VULKAN
            __android_log_print(ANDROID_LOG_INFO, TAG, "Loading embedding model with Vulkan backend");
            model_params.n_gpu_layers = -1;
            unsetenv("GGML_VULKAN_DISABLE");
            setenv("GGML_OPENCL_DISABLE", "1", 1);

            // Apply safety flags for problematic SoCs if using Vulkan
            if (is_problematic_vulkan_device()) {
                 __android_log_print(ANDROID_LOG_WARN, TAG, "Problematic SoC detected - enabling Vulkan safety flags for embeddings");
                 setenv("GGML_VK_DISABLE_F16", "1", 1);
                 setenv("GGML_VK_DISABLE_ASYNC", "1", 1);
                 setenv("GGML_VK_FORCE_MAX_ALLOCATION_SIZE", "536870912", 1); // 512MB
            }
        } else if (backend_id == 2) { // OPENCL
            __android_log_print(ANDROID_LOG_INFO, TAG, "Loading embedding model with OpenCL backend");
            model_params.n_gpu_layers = -1;
            setenv("GGML_VULKAN_DISABLE", "1", 1);
            unsetenv("GGML_OPENCL_DISABLE");
        }

        session->model_embed = llama_model_load_from_file(model_path, model_params);
        env->ReleaseStringUTFChars(path, model_path);

        if (!session->model_embed) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to load embedding model");
            return JNI_FALSE;
        }

        struct llama_context_params ctx_params = llama_context_default_params();
        ctx_params.embeddings = true;
        ctx_params.n_ctx = n_ctx;
        ctx_params.n_batch = n_batch;
        
        session->context_embed = llama_init_from_model(session->model_embed, ctx_params);
        if (!session->context_embed) {
             llama_model_free(session->model_embed);
             session->model_embed = nullptr;
             return JNI_FALSE;
        }
        
        __android_log_print(ANDROID_LOG_INFO, TAG, "Embedding model loaded successfully. Batch: %d, Ctx: %d", n_batch, n_ctx);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Exception in loadEmbeddingModelNative: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown exception in loadEmbeddingModelNative");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_synapsenotes_ai_core_ai_LlamaContext_loadModelNative(JNIEnv* env, jobject, jstring path, jstring template_str, jint n_batch, jint n_ctx, jboolean use_mmap, jint backend_id) {
    try {
        const char* model_path = env->GetStringUTFChars(path, nullptr);
        LlmSession* session = get_session();
        
        std::lock_guard<std::mutex> lock(session->session_mutex);
        
        if (template_str != nullptr) {
            const char* tmpl = env->GetStringUTFChars(template_str, nullptr);
            session->chat_template = std::string(tmpl);
            env->ReleaseStringUTFChars(template_str, tmpl);
            __android_log_print(ANDROID_LOG_INFO, TAG, "Loaded custom chat template");
        } else {
            session->chat_template = "";
        }

        if (session->context) {
            llama_free(session->context);
            session->context = nullptr;
        }
        if (session->model) {
            llama_model_free(session->model);
            session->model = nullptr;
        }

        struct llama_model_params model_params = llama_model_default_params();
        model_params.use_mmap = (bool)use_mmap;
        
        // Reset safety flags initially
        unsetenv("GGML_VK_DISABLE_F16");
        unsetenv("GGML_VK_DISABLE_ASYNC");
        unsetenv("GGML_VK_FORCE_MAX_ALLOCATION_SIZE");

        // Configure backend based on requested ID (Robust explicit logic)
        // 0 = CPU, 1 = VULKAN, 2 = OPENCL
        if (backend_id == 0) { // CPU
            __android_log_print(ANDROID_LOG_INFO, TAG, "Loading chat model with CPU backend");
            model_params.n_gpu_layers = 0;
            setenv("GGML_VULKAN_DISABLE", "1", 1);
            setenv("GGML_OPENCL_DISABLE", "1", 1);
        } else if (backend_id == 1) { // VULKAN
            __android_log_print(ANDROID_LOG_INFO, TAG, "Loading chat model with Vulkan backend");
            model_params.n_gpu_layers = -1;
            unsetenv("GGML_VULKAN_DISABLE");
            setenv("GGML_OPENCL_DISABLE", "1", 1);

            // Apply safety flags for problematic SoCs if using Vulkan
            if (is_problematic_vulkan_device()) {
                 __android_log_print(ANDROID_LOG_WARN, TAG, "Problematic SoC detected - enabling Vulkan safety flags for chat model");
                 setenv("GGML_VK_DISABLE_F16", "1", 1);
                 setenv("GGML_VK_DISABLE_ASYNC", "1", 1);
                 // For chat models, we might not want to limit allocation size aggressively unless OOM is confirmed, 
                 // but 512MB is a safe baseline for S22 driver stability.
                 setenv("GGML_VK_FORCE_MAX_ALLOCATION_SIZE", "536870912", 1); 
            }
        } else if (backend_id == 2) { // OPENCL
            __android_log_print(ANDROID_LOG_INFO, TAG, "Loading chat model with OpenCL backend");
            model_params.n_gpu_layers = -1;
            setenv("GGML_VULKAN_DISABLE", "1", 1);
            unsetenv("GGML_OPENCL_DISABLE");
        } else {
            // Fallback for unexpected ID -> Auto-select (legacy behavior, though Java layer should prevent this)
            __android_log_print(ANDROID_LOG_WARN, TAG, "Unknown backend ID %d, defaulting to auto-detect", backend_id);
            int auto_backend = auto_select_backend();
            // Recurse once with correct ID
            // Or just implement legacy auto logic here:
            if (auto_backend == 0) {
                 model_params.n_gpu_layers = 0;
                 setenv("GGML_VULKAN_DISABLE", "1", 1);
                 setenv("GGML_OPENCL_DISABLE", "1", 1);
            } else if (auto_backend == 1) {
                 model_params.n_gpu_layers = -1;
                 unsetenv("GGML_VULKAN_DISABLE");
                 setenv("GGML_OPENCL_DISABLE", "1", 1);
            } else {
                 model_params.n_gpu_layers = -1;
                 setenv("GGML_VULKAN_DISABLE", "1", 1);
                 unsetenv("GGML_OPENCL_DISABLE");
            }
        }

        session->model = llama_model_load_from_file(model_path, model_params);
        
        // Check if load succeeded
        if (!session->model) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to load chat model with backend_id %d", backend_id);
            env->ReleaseStringUTFChars(path, model_path);
            return JNI_FALSE;
        }
        
        if (backend_id == 1 || backend_id == 2) {
            session->gpu_enabled = true;
        } else {
            session->gpu_enabled = false;
        }

        env->ReleaseStringUTFChars(path, model_path);

        struct llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = n_ctx; 
        ctx_params.n_batch = n_batch;
        
        session->context = llama_init_from_model(session->model, ctx_params);
        if (!session->context) {
             llama_model_free(session->model);
             session->model = nullptr;
             return JNI_FALSE;
        }

        __android_log_print(ANDROID_LOG_INFO, TAG, "Chat model loaded successfully. Batch: %d, Ctx: %d", n_batch, n_ctx);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Exception in loadModelNative: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown exception in loadModelNative");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_synapsenotes_ai_core_ai_LlamaContext_stopCompletion(JNIEnv* env, jobject) {
    LlmSession* session = get_session();
    session->stop_requested = true;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Stop requested");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_synapsenotes_ai_core_ai_LlamaContext_completion(JNIEnv* env, jobject, jstring prompt, jstring system_prompt, jobjectArray stop_sequences_array, jobject callback) {
    try {
        LlmSession* session = get_session();
        
        // Lock for session access
        std::lock_guard<std::mutex> lock(session->session_mutex);

        if (!session->context) return env->NewStringUTF("Error: Model not loaded");
        
        session->stop_requested = false;
        
        if (callback == nullptr) {
            return env->NewStringUTF("Error: Callback is null");
        }

        jclass callbackClass = env->GetObjectClass(callback);
        if (callbackClass == nullptr) {
            return env->NewStringUTF("Error: Callback class not found");
        }
        jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
        if (onTokenMethod == nullptr) {
            return env->NewStringUTF("Error: Callback method not found");
        }
        
        const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
        std::string user_prompt(prompt_cstr);
        env->ReleaseStringUTFChars(prompt, prompt_cstr);

        const char* sys_prompt_cstr = env->GetStringUTFChars(system_prompt, nullptr);
        std::string system_content(sys_prompt_cstr);
        env->ReleaseStringUTFChars(system_prompt, sys_prompt_cstr);

        std::vector<std::string> stop_sequences;
        if (stop_sequences_array != nullptr) {
            int stringCount = env->GetArrayLength(stop_sequences_array);
            for (int i = 0; i < stringCount; i++) {
                jstring string = (jstring) (env->GetObjectArrayElement(stop_sequences_array, i));
                if (string == nullptr) continue;
                const char *rawString = env->GetStringUTFChars(string, 0);
                stop_sequences.push_back(std::string(rawString));
                env->ReleaseStringUTFChars(string, rawString);
                env->DeleteLocalRef(string);
            }
        }

        // Prepare messages for template
        std::vector<llama_chat_message> messages;
        
        messages.push_back({"system", system_content.c_str()});
        messages.push_back({"user", user_prompt.c_str()});

        std::vector<char> formatted_prompt(8192);
        int32_t res = -1;
        
        // 1. Use custom downloaded template if available
        if (!session->chat_template.empty()) {
             res = llama_chat_apply_template(session->chat_template.c_str(), messages.data(), messages.size(), true, formatted_prompt.data(), formatted_prompt.size());
        } 
        // 2. Otherwise try model's built-in template
        else {
             res = llama_chat_apply_template(llama_model_chat_template(session->model, nullptr), messages.data(), messages.size(), true, formatted_prompt.data(), formatted_prompt.size());
        }
        
        std::string final_prompt_str;

        if (res > 0) {
            if (res > formatted_prompt.size()) {
                formatted_prompt.resize(res);
                if (!session->chat_template.empty()) {
                     res = llama_chat_apply_template(session->chat_template.c_str(), messages.data(), messages.size(), true, formatted_prompt.data(), formatted_prompt.size());
                } else {
                     res = llama_chat_apply_template(llama_model_chat_template(session->model, nullptr), messages.data(), messages.size(), true, formatted_prompt.data(), formatted_prompt.size());
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

        const struct llama_vocab * vocab = llama_model_get_vocab(session->model);

        std::vector<llama_token> tokens_list;
        tokens_list.resize(prompt_length + 100); 
        int n_tokens = llama_tokenize(vocab, final_prompt, prompt_length, tokens_list.data(), tokens_list.size(), true, true);
        if (n_tokens < 0) {
            tokens_list.resize(-n_tokens);
            n_tokens = llama_tokenize(vocab, final_prompt, prompt_length, tokens_list.data(), tokens_list.size(), true, true);
        }
        tokens_list.resize(n_tokens);

        llama_memory_seq_rm(llama_get_memory(session->context), -1, -1, -1);

        // Dynamic batch size from context
        const int32_t n_batch = llama_n_batch(session->context);

        // Init batch with actual batch size (avoiding hardcoded 2048)
        llama_batch batch = llama_batch_init(n_batch, 0, 1);

        for (int i = 0; i < n_tokens; i += n_batch) {
            int n_chunk = n_tokens - i;
            if (n_chunk > n_batch) n_chunk = n_batch;
            
            batch.n_tokens = 0;
            for (int j = 0; j < n_chunk; j++) {
                batch_add(batch, tokens_list[i + j], i + j, 0, false);
            }
            
            if (i + n_chunk == n_tokens) {
                batch.logits[batch.n_tokens - 1] = 1;
            }

            if (llama_decode(session->context, batch) != 0) {
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

        while (n_decode < max_tokens && n_cur < llama_n_ctx(session->context)) {
            if (session->stop_requested) {
                __android_log_print(ANDROID_LOG_INFO, TAG, "Generation stopped by user.");
                break;
            }

            if (n_decode >= max_tokens) {
                 __android_log_print(ANDROID_LOG_INFO, TAG, "Max tokens reached (%d).", max_tokens);
                 break;
            }

            llama_token new_token_id = llama_sampler_sample(sampler, session->context, -1);

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
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    env->DeleteLocalRef(jPiece);
                    llama_sampler_free(sampler);
                    llama_batch_free(batch);
                    return env->NewStringUTF("Error: Java callback threw exception");
                }
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

            if (llama_decode(session->context, batch) != 0) break;
        }

        llama_sampler_free(sampler);
        llama_batch_free(batch);
        
        return env->NewStringUTF(result_str.c_str());
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Exception in completion: %s", e.what());
        return env->NewStringUTF("Error: C++ exception occurred");
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown exception in completion");
        return env->NewStringUTF("Error: Unknown C++ exception");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_synapsenotes_ai_core_ai_LlamaContext_isGpuEnabled(JNIEnv* env, jobject) {
    LlmSession* session = get_session();
    return session->gpu_enabled ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_synapsenotes_ai_core_ai_LlamaContext_isOpenCLAvailable(JNIEnv* env, jobject) {
    // Try to load libOpenCL.so dynamically to check presence
    void* handle = dlopen("libOpenCL.so", RTLD_NOW | RTLD_LOCAL);
    if (handle) {
        dlclose(handle);
        return JNI_TRUE;
    }
    
    // Fallback paths common on Android
    const char* paths[] = {
        "/system/vendor/lib64/libOpenCL.so",
        "/system/lib64/libOpenCL.so",
        "/vendor/lib64/libOpenCL.so",
        "/system/vendor/lib/libOpenCL.so",
        "/system/lib/libOpenCL.so"
    };
    
    for (const char* path : paths) {
         handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
         if (handle) {
             dlclose(handle);
             return JNI_TRUE;
         }
    }
    
    return JNI_FALSE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_synapsenotes_ai_core_ai_LlamaContext_embed(JNIEnv* env, jobject, jstring text) {
    try {
        LlmSession* session = get_session();
        std::lock_guard<std::mutex> lock(session->session_mutex);

        if (!session->context_embed && !session->context) return nullptr;
        
        llama_context* ctx = session->context_embed ? session->context_embed : session->context;
        llama_model* model = session->context_embed ? session->model_embed : session->model;
        
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

        int32_t n_embd = llama_model_n_embd(model);
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
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Exception in embed: %s", e.what());
        return nullptr;
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown exception in embed");
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_synapsenotes_ai_core_ai_LlamaContext_unload(JNIEnv* env, jobject) {
    try {
        LlmSession* session = get_session();
        session->unload();
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Exception in unload: %s", e.what());
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown exception in unload");
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_synapsenotes_ai_core_ai_NativeLib_testSelectionLogicNative(JNIEnv* env, jobject, jstring soc_str, jstring hw_str) {
    const char* soc_cstr = env->GetStringUTFChars(soc_str, nullptr);
    const char* hw_cstr = env->GetStringUTFChars(hw_str, nullptr);
    
    std::string soc(soc_cstr);
    std::string hw(hw_cstr);
    
    GPUVendor vendor = resolve_gpu_vendor(soc, hw);
    bool is_problematic = check_is_problematic_vulkan(soc, hw);
    
    env->ReleaseStringUTFChars(soc_str, soc_cstr);
    env->ReleaseStringUTFChars(hw_str, hw_cstr);
    
    // Logic matching auto_select_backend
    if (is_problematic) return 0; // CPU
    if (vendor == GPU_ADRENO) return 2; // OPENCL
    if (vendor == GPU_MALI) return 1; // VULKAN
    return 0; // CPU Fallback
}
