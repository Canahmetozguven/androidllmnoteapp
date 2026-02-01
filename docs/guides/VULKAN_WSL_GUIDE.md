# Android LLM Note App: Vulkan Acceleration & WSL Build Guide

This document summarizes the technical changes made to enable **Vulkan GPU acceleration** for the `llama.cpp` integration and explains how the hybrid **WSL-Windows** build environment operates.

---

## 1. Accomplishments & Fixes

### Core LLM & Inference
- **Native Inference Loop**: Implemented a complete generation loop in `native-lib.cpp`. The app now performs real-time token-by-token generation.
- **Chat Templates**: Integrated `llama_chat_apply_template` to auto-format prompts based on the model's metadata (supporting Gemma, Llama 3, Phi-2, etc.).
- **Creative Sampling**: Added a sampling chain including **Temperature (0.7)**, **Top-P (0.9)**, and **Top-K (40)** for natural responses.
- **Context Management**: Implemented KV cache clearing (`llama_memory_seq_rm`) between turns to maintain conversation integrity.

### Vulkan GPU Acceleration
- **Extension Filtering**: Resolved crashes in `createDevice` by dynamically filtering requested Vulkan extensions against those actually supported by the hardware (e.g., handling the absence of `VK_KHR_cooperative_matrix` on emulators).
- **API 26+ Compatibility**: Patched `ggml-vulkan.cpp` to use the C++ dynamic dispatcher for `getFeatures2`, fixing "undefined symbol" errors on Android versions using older Vulkan stubs.
- **CPU Fallback**: Added a safety mechanism in JNI `loadModel` that automatically retries on CPU if GPU initialization fails.

### UI & UX
- **GPU Status Indicator**: Added a **light green dot** next to "On-device AI" in the Chat Screen header that illuminates only when GPU acceleration is actively being used.
- **Build Robustness**: Increased Gradle heap size to **6GB** to resolve OOM issues during heavy native library packaging.

---

## 2. How the WSL Build Part Works

Due to complex dependencies (like `vulkan-shaders-gen`) and the need for a Linux-based cross-compilation environment, we use **WSL (Windows Subsystem for Linux)** as the primary build engine.

### The Hybrid Workflow
1.  **Code Editing**: Performed on the Windows filesystem (`C:\Users\...`).
2.  **Synchronization**: The `build_vulkan.sh` script (or manual commands) synchronizes the `app/src` directory from Windows to the WSL project folder (`~/projects/android_note_app`).
3.  **Two-Step Build Process (`build_vulkan.sh`)**:
    -   **Step 1 (Host)**: Builds the `vulkan-shaders-gen` tool for the Linux host. This tool is required to compile GLSL shaders into SPIR-V headers for the Android library.
    -   **Step 2 (Cross-Compile)**: Runs the Android Gradle build. CMake identifies the cross-compilation context and uses the previously built host tool to generate the necessary Vulkan shaders.
4.  **Deployment**: The resulting APK is copied back to the Windows filesystem and deployed to the emulator using the Windows `adb.exe`.

### Key Environment Variables (WSL)
- `ANDROID_HOME`: Points to the Linux-side Android SDK.
- `GGML_VULKAN=ON`: Enables the Vulkan backend code.
- `GGML_BACKEND_DL=OFF`: Statically links backends to ensure predictable behavior on Android.

---

## 3. Dependency Management
To resolve NDK version conflicts and missing headers, we maintain a local `external` directory:
- `app/src/main/cpp/external/Vulkan-Headers`: Official Khronos C headers.
- `app/src/main/cpp/external/Vulkan-Hpp`: Official Khronos C++ bindings.

This ensures that `ggml-vulkan.cpp` always has access to modern Vulkan definitions regardless of the specific NDK version installed.

---

## 4. Troubleshooting
- **Missing Dot**: If the green dot is missing, check `adb logcat | grep LLAMA_CPP` for "ErrorExtensionNotPresent" or backend initialization failures.
- **Slow Chat**: Verify if the model fell back to CPU. The logs will state: "Failed to load model with GPU, falling back to CPU...".
- **Crash on Start**: Ensure the app is fully uninstalled before a fresh build to clear stale native library caches.
