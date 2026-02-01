# Current State of llama.cpp Integration

## Active CMake Flags
- **GGML_VULKAN**: ON (Forced shader compiler path)
- **LLAMA_VULKAN**: ON
- **GGML_BACKEND_DL**: OFF (Static linking enforced)
- **GGML_OPENCL**: ON
- **GGML_OPENCL_USE_ADRENO_KERNELS**: ON
- **GGML_OPENCL_EMBED_KERNELS**: ON
- **Linker Flags**: `-Wl,-z,max-page-size=16384` (Android 15 compatibility)

## JNI Implementation (Model Loading)
- **Entry Point**: `JNI_OnLoad` uses explicit `RegisterNatives` for `LlamaContext` class.
- **Functions**: `loadModelNative` and `loadEmbeddingModelNative` handle both chat and embedding models.
- **Backend Control**: Uses `backend_id` (0: CPU, 1: Vulkan, 2: OpenCL) passed from Java/Kotlin.
- **Environment Sync**: Dynamically sets `GGML_VULKAN_DISABLE` and `GGML_OPENCL_DISABLE` environment variables before loading models to enforce backend selection.

## Hardware Detection & Fallback Logic
- **GPU Vendor Detection**: Identifies Adreno and Mali based on system properties (`ro.hardware.vulkan`, `ro.board.platform`, `ro.hardware`).
- **Problematic Device Check**: Explicitly checks for SoCs like Snapdragon 8 Gen 1/2, Exynos 2200, Tensor G2/G3, and Snapdragon 8 Elite.
- **Automatic Selection**: 
  - Problematic devices are forced to **CPU**.
  - Adreno/Mali/Unknown GPUs default to **OpenCL** in `auto_select_backend()`.
- **Vulkan Safety Flags**: If Vulkan is used on a flagged device, it enables:
  - `GGML_VK_DISABLE_F16`
  - `GGML_VK_DISABLE_ASYNC`
  - `GGML_VK_FORCE_MAX_ALLOCATION_SIZE=536870912` (512MB)

## Build System (WSL)
- **Two-Step Build**: `build_vulkan.sh` builds `vulkan-shaders-gen` for the host first, then invokes Gradle with `-PuseVulkan=true`.
- **Toolchain**: Uses `host-toolchain.cmake` for host tool compilation.

## Identified Issues / Hardcoded Logic
- **Aggressive CPU Fallback**: `auto_select_backend()` forces CPU for problematic devices even if OpenCL could be stable.
- **Hardcoded Memory Limit**: The 512MB Vulkan allocation limit is hardcoded for all flagged devices.
- **Backend ID Fallback**: In `loadModelNative`, if an invalid ID is passed, it falls back to the legacy `auto_select_backend()` logic.
- **Missing OpenCL Check during Load**: While `isOpenCLAvailable()` exists, the loading functions don't explicitly verify the library is present before setting environment variables.
