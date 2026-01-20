# Technical Paper: High-Performance On-Device LLM Inference on Android via Vulkan and WSL

## Abstract
This document details the architecture and implementation of a high-performance Large Language Model (LLM) inference engine for Android devices using the Vulkan API. A critical component of this research is the cross-platform development environment leveraging Windows Subsystem for Linux (WSL) to orchestrate complex native builds and shader generation.

## 1. System Architecture
The application integrates `llama.cpp` as the core inference engine, encapsulated within an Android application. The architecture is divided into three primary layers:
1.  **UI Layer (Kotlin/Compose)**: Manages user interaction and state.
2.  **JNI Wrapper (C++)**: Bridges the JVM and the native C++ engine, handling chat templates and sampling logic.
3.  **Inference Engine (llama.cpp/GGML)**: Optimized C++ library utilizing Vulkan for hardware acceleration.

## 2. Hybrid Build Environment (WSL + Windows)
To overcome the limitations of Windows-based native compilation for complex C++ projects, a hybrid WSL-Windows workflow was established.

### 2.1 WSL Orchestration
WSL serves as the primary build host for native components.
-   **Environment Synchronization**: Source code is developed on the Windows filesystem and synchronized to the WSL filesystem (`~/projects/android_note_app`) to ensure Linux-compatible file permissions and paths.
-   **Native SDKs**: The Linux version of the Android NDK and SDK is installed within WSL to manage the cross-compilation toolchain.

### 2.2 Two-Step Shader Generation
Vulkan acceleration requires the compilation of GLSL shaders into SPIR-V format. This is handled via a specialized two-step build process:
1.  **Host Step**: A Linux-native tool (`vulkan-shaders-gen`) is compiled for the WSL host environment.
2.  **Cross-Compile Step**: The Android NDK uses the previously built host tool to generate SPIR-V headers during the compilation of the `ggml-vulkan` library.

## 3. Implementation Challenges & Solutions

### 3.1 Vulkan Compatibility & Stabilization
The Android environment presents unique challenges for Vulkan initialization:
-   **Extension Filtering**: Many emulators and devices do not support high-end extensions like `VK_KHR_cooperative_matrix`. We implemented a dynamic filtering system in `ggml-vulkan.cpp` that queries `enumerateDeviceExtensionProperties` and removes unsupported extensions before `createDevice`.
-   **Dynamic Dispatching**: Android's Vulkan loader (`libvulkan.so`) often lacks direct exports for Vulkan 1.1+ functions. We patched the engine to use the `vulkan.hpp` dynamic dispatcher, routing all calls through `vkGetInstanceProcAddr`.
-   **Header Mismatches**: Conflict between NDK provided headers and `llama.cpp` expectations were resolved by bundling official Khronos `Vulkan-Headers` and `Vulkan-Hpp` within the project's `external/` directory.

### 3.2 Native Inference Logic
-   **Chat Templates**: Automated prompt formatting using `llama_chat_apply_template` to ensure model-specific special tokens (Gemma, Phi, etc.) are correctly inserted.
-   **Sampling Chain**: Implementation of a creative sampler (Temp 0.7, Top-P 0.9, Top-K 40) to improve response quality over standard greedy decoding.
-   **Memory Efficiency**: Active management of the KV cache using `llama_memory_seq_rm` to prevent context drift and memory exhaustion in multi-turn conversations.

## 4. Operational Workflow Guide

### Step 1: WSL Preparation
```bash
# Clone and setup within WSL
mkdir -p ~/projects/android_note_app
cp -r /mnt/c/Users/<user>/Path/to/Project/* ~/projects/android_note_app/
```

### Step 2: Native Compilation (`build_vulkan.sh`)
This script automates the two-step build:
1.  Exports `ANDROID_HOME` and `ANDROID_NDK_HOME`.
2.  Sets `GGML_VULKAN=ON` and `GGML_BACKEND_DL=OFF`.
3.  Builds host shader generator.
4.  Invokes `./gradlew :app:assembleDebug`.

### Step 3: Deployment (Windows side)
```powershell
# Copy APK from WSL to Windows
wsl cp ~/projects/android_note_app/app/build/outputs/apk/debug/app-debug.apk C:/temp/
# Deploy
adb install -r C:/temp/app-debug.apk
```

## 5. Verification Metrics
-   **GPU Indicator**: A real-time UI indicator (green dot) connected to the `isGpuEnabled` JNI method confirms hardware acceleration.
-   **Inference Performance**: Significant latency reduction observed when offloading 32+ layers to the Vulkan backend (`Vulkan0`).
