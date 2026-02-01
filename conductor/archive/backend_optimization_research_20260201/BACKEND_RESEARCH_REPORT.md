# Comprehensive Research Report: Android LLM Backend Optimization

## Executive Summary
This report synthesizes findings from a codebase audit and external research into optimizing `llama.cpp` for Android. The key conclusion is that a "one-size-fits-all" backend strategy is suboptimal. **Adreno GPUs should exclusively use the new Qualcomm-optimized OpenCL backend**, while **Mali and other GPUs should default to Vulkan**, with CPU as a robust fallback.

## 1. Architectural Findings

### 1.1 Adreno (Qualcomm)
*   **Status:** First-class citizen in `llama.cpp` as of late 2024.
*   **Optimization:** Qualcomm has upstreamed a dedicated OpenCL backend (`ggml-opencl.cpp`) with Adreno-specific kernel tuning (e.g., `GGML_OPENCL_USE_ADRENO_KERNELS`).
*   **Performance:** Benchmarks show OpenCL outperforming Vulkan by significant margins on Snapdragon 8 Gen 2 and newer, primarily due to better memory management (AHB) and driver maturity.
*   **Recommendation:** **Force OpenCL** for all detected Adreno GPUs.

### 1.2 Mali (ARM) & Others
*   **Status:** Second-class citizen for OpenCL.
*   **Issue:** The generic OpenCL kernels often fail to saturate Mali shader cores, leading to performance worse than CPU execution.
*   **Vulkan:** The Vulkan backend (`ggml-vulkan.cpp`) is more actively maintained for cross-vendor compatibility and offers better stability on Mali devices (Pixel, older Samsung).
*   **Recommendation:** **Default to Vulkan**, but implement strict RAM checks due to higher overhead.

## 2. Codebase Audit

### 2.1 Current Implementation
*   **Files:** `native-lib.cpp`, `LlmEngine.kt`, `HardwareCapabilityProvider.kt`.
*   **Logic:** The app currently attempts `Vulkan -> OpenCL -> CPU`.
*   **Deficiency:** It does not differentiate between GPU vendors. It tries Vulkan first for *everyone*, which is suboptimal for Adreno (misses OpenCL perf) and risky for Mali (Vulkan RAM spikes).

### 2.2 Build Configuration
*   **Gradle:** `arguments += listOf("-DGGML_VULKAN=ON", "-DGGML_OPENCL=ON")`.
*   **CMake:** Both backends are compiled in.
*   **Missing:** Dynamic loading (`GGML_BACKEND_DL=ON`) is enabled in scripts but `build.gradle.kts` excludes the libs, effectively forcing static linking or system driver usage. This is acceptable but limits flexibility.

## 3. Recommended Code Changes

### 3.1 Update Backend Selection Logic (Kotlin)
Modify `detectBestBackend()` in `DefaultHardwareCapabilityProvider.kt` to inspect `gpuName`:

```kotlin
private fun detectBestBackend(): BackendType {
    val gpuInfo = getGpuInfo() // Need to implement JNI call to get GL_RENDERER string
    return when {
        gpuInfo.contains("Adreno", ignoreCase = true) -> {
            if (probeBackend(BackendType.OPENCL)) BackendType.OPENCL else BackendType.CPU
        }
        gpuInfo.contains("Mali", ignoreCase = true) -> {
            if (probeBackend(BackendType.VULKAN)) BackendType.VULKAN else BackendType.CPU
        }
        else -> BackendType.CPU
    }
}
```

### 3.2 Native Probing (C++)
Enhance `probeBackendNative` in `native-lib.cpp` to return the GPU vendor string (GL_RENDERER) to Kotlin, allowing the decision logic to live in the easier-to-modify Kotlin layer.

### 3.3 AHB Interop
Ensure `VK_IMAGE_TILING_LINEAR` is used for any texture creation if we proceed with AHB-based image processing (though for pure LLM text inference, this is less critical than buffer management).

## 4. Upstream References

*   **Qualcomm OpenCL Backend:** [Introducing the new OpenCL GPU Backend](https://www.qualcomm.com/developer/blog/2024/11/introducing-new-opn-cl-gpu-backend-llama-cpp-for-qualcomm-adreno-gpu)
*   **Issue #5965:** [Using OpenCL on Adreno & Mali GPUs is slower than CPU](https://github.com/ggerganov/llama.cpp/issues/5965) - Confirms Mali OpenCL issues.
*   **Issue #2052:** [Pool Android performance...](https://github.com/ggerganov/llama.cpp/issues/2052) - Early reports of driver quirks.

## 5. Next Steps
Proceed with the **Optimization Implementation** track to refactor `HardwareCapabilityProvider` and `native-lib.cpp` to implement vendor-aware backend selection.
