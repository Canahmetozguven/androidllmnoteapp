# 04 - Native Layer (C++) Review

## Overview
This section covers `app/src/main/cpp`, specifically `native-lib.cpp` (JNI Interface) and `CMakeLists.txt`. This layer bridges the Kotlin application with `llama.cpp` for on-device inference.

## Strengths
*   **Hardware Abstraction**: The code includes advanced logic (`detect_gpu_vendor`, `is_problematic_vulkan_device`) to inspect system properties (`ro.board.platform`) and work around driver bugs on specific SoCs (Snapdragon 8 Gen 1).
*   **Backend Flexibility**: It supports dynamically switching between CPU, Vulkan, and OpenCL backends based on runtime checks.
*   **Safety Flags**: The `loadModelNative` function sets specific environment variables (`GGML_VK_DISABLE_ASYNC`) to prevent crashes on unstable drivers.

## Areas for Improvement

### 1. Global State Management
*   **Issue**: `g_model`, `g_context`, and `g_gpu_enabled` are declared as global static variables.
*   **Impact**: This restricts the app to a single active model instance. While acceptable for a mobile app, it makes it difficult to manage state if the Activity is recreated but the Process remains alive (potential memory leaks or state inconsistency).
*   **Recommendation**: Wrap the state in a C++ class/struct and pass a pointer (Handle) to the Java layer.

### 2. Hardcoded Chat Templates
*   **Issue**: The code falls back to a hardcoded "ChatML" template (`<|im_start|>...`) if logic fails.
*   **Impact**: If a user loads a Llama-2 or Mistral model that doesn't use ChatML, the model will output garbage or hallucinate.
*   **Recommendation**: Remove the hardcoded fallback. Require the Java layer to provide the correct template string to `loadModelNative`.

### 3. OpenCL Library Logic
*   **Issue**: `CMakeLists.txt` searches for `libOpenCL.so` in hardcoded paths (`/system/vendor/lib64`, etc.).
*   **Impact**: While common, this might fail on obscure devices or future Android versions.
*   **Recommendation**: Use `dlopen` at runtime (which `isOpenCLAvailable` already does partially) rather than link-time dependencies if possible, or strictly rely on the NDK stub.

### 4. Logging Overhead
*   **Issue**: `android_log_callback` creates a new `std::string` for every log message.
*   **Impact**: During high-verbosity inference (token generation), this creates unnecessary allocation overhead on the native heap.
*   **Recommendation**: Use `__android_log_print` directly with the C-string buffer.

## Action Plan
- [ ] **Refactor**: Encapsulate global state into a `LlmSession` C++ class.
- [ ] **Fix**: Remove hardcoded ChatML fallback strings; pass all templates from Kotlin.
- [ ] **Performance**: Optimize logging callback to avoid `std::string` allocation.
