# Performance & Stability Matrix: Android LLM Backends

## Overview
This matrix maps specific Android GPU architectures (Adreno, Mali) to the recommended `llama.cpp` backend (OpenCL, Vulkan, CPU) based on performance, stability, and known driver quirks.

| GPU Architecture | Chipset Examples | Recommended Backend | Stability | Performance | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Adreno 7xx/8xx** | Snapdragon 8 Gen 2/3, 8 Elite | **OpenCL** | High | **Best** | Requires Qualcomm's optimized OpenCL backend (upstreamed 2025). |
| **Adreno 6xx** | Snapdragon 865, 870, 888 | **OpenCL** | Medium | Good | May require driver updates. Vulkan is a stable fallback. |
| **Mali G710/G715** | Dimensity 9000/9200, Pixel 7/8 | **Vulkan** | High | Good | OpenCL often slower than CPU due to lack of optimization. |
| **Mali G77/G78** | Dimensity 1200, Exynos 2100 | **Vulkan** | Medium | Moderate | Driver bugs common. CPU fallback recommended for small models. |
| **Xclipse (AMD)** | Exynos 2200, 2400 | **Vulkan** | Low/Med | High | RDNA architecture prefers Vulkan but drivers can be unstable. |

## Known Issues & Workarounds

### 1. The "Vulkan RAM Spike"
*   **Issue:** Vulkan backend consumes ~1GB more VRAM/RAM than OpenCL/CPU during model loading.
*   **Impact:** Crashes on devices with <8GB RAM or when loading large models (e.g., 7B Q4).
*   **Workaround:** Force `n_ctx` to 2048 or lower on constrained devices when Vulkan is active.

### 2. OpenCL "Device Lost" on Older Adreno
*   **Issue:** Long-running kernels (prompt processing) trigger Android's watchdog timer, killing the driver.
*   **Impact:** App crash during large prompt ingestion.
*   **Workaround:** Split prompt processing into smaller batches (`n_batch < 512`) or use `GGML_OPENCL_EMBED_KERNELS` to reduce init time.

### 3. Mali OpenCL Performance
*   **Issue:** Generic OpenCL kernels in `llama.cpp` are not optimized for Mali's VLIW/warp architecture.
*   **Impact:** Inference speed is often 20-50% slower than CPU SIMD (NEON/DotProd).
*   **Recommendation:** **Disable OpenCL** for Mali devices unless testing specific experimental kernels.

## Backend Selection Logic (Proposed)

```kotlin
fun getOptimalBackend(gpuName: String, ramGb: Int): BackendType {
    return when {
        gpuName.contains("Adreno") -> BackendType.OPENCL
        gpuName.contains("Mali") -> BackendType.VULKAN
        gpuName.contains("Xclipse") -> BackendType.VULKAN
        else -> BackendType.CPU // Fallback
    }
}
```
