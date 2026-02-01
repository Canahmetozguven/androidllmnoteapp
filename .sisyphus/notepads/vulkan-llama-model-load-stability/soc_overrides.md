# SoC Backend Override Matrix

## 1. Problematic SoC Identification

| Device | SoC Name | ID String | Issues |
|--------|----------|-----------|--------|
| **Pixel 7 / 7a** | Tensor G2 | `gs201` | Mali GPU. Vulkan hangs reported. OpenCL unsupported. |
| **Pixel 8 / 8a** | Tensor G3 | `zuma` | Mali GPU. Newer, but likely same constraints. |
| **Galaxy S22** | Exynos 2200 | `s5e9925` | Xclipse 920 (AMD). Vulkan unstable with mmap. |
| **Galaxy S25** | Snapdragon 8 Elite | `sm8750` | Adreno 830. New driver, potentially immature Vulkan. |

## 2. Override Logic

### Backend Order Strategy
We will introduce `getRecommendedBackendOrder()` in `HardwareCapabilityProvider`.

| SoC | Strategy | Order | Rationale |
|-----|----------|-------|-----------|
| **Tensor (gs201, zuma)** | **CPU Only** | `CPU` | No OpenCL. Vulkan unstable. CPU is fast enough (NPU not accessible). |
| **Exynos 2200 (s5e9925)** | **OpenCL First** | `OPENCL`, `CPU` | Xclipse Vulkan is crash-prone. Mali/AMD OpenCL often safer. |
| **SD 8 Elite (sm8750)** | **OpenCL First** | `OPENCL`, `VULKAN`, `CPU` | Adreno OpenCL is rock solid. Vulkan is fast but risky on new chip. |
| **Default** | **Standard** | `VULKAN`, `OPENCL`, `CPU` | Prefer Vulkan for speed on stable devices. |

### Configuration Safety
- **Mmap**: Disabled for all above SoCs (`isMmapSafe` returns false).
- **Batch Size**: Capped at 32 for all above SoCs.

## 3. Implementation Plan

### A. Kotlin Changes (`DefaultHardwareCapabilityProvider.kt`)
1.  Update `isKnownProblematicDevice()` to include `gs201`, `zuma`.
    ```kotlin
    val isProblematic = ... || hardware.contains("gs201") || hardware.contains("zuma")
    ```
2.  Implement `getRecommendedBackendOrder()`:
    ```kotlin
    fun getRecommendedBackendOrder(): List<BackendType> {
        if (hardware.contains("gs201") || hardware.contains("zuma")) return listOf(CPU)
        if (hardware.contains("s5e9925")) return listOf(OPENCL, CPU)
        if (hardware.contains("sm8750")) return listOf(OPENCL, VULKAN, CPU)
        return listOf(VULKAN, OPENCL, CPU)
    }
    ```

### B. Native Changes (`native-lib.cpp`)
1.  Update `is_problematic_vulkan_device()` to include `gs201`, `zuma` (matches `ro.board.platform`).
    -   This ensures `GGML_VK_...` safety flags are applied if Vulkan *is* forced.

### C. Engine Changes (`LlmEngine.kt`)
1.  Replace hardcoded list construction with:
    ```kotlin
    val backendsToTry = hardwareCapabilityProvider.getRecommendedBackendOrder().toMutableSet()
    // Add preferred backend to front if set
    // Add others as fallback
    ```
