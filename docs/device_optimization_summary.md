# Device Optimization Strategy

## 1. Samsung Galaxy S22 (Snapdragon 8 Gen 1 - SM8450)
*   **Processor**: Qualcomm Snapdragon 8 Gen 1
*   **GPU**: Adreno 730
*   **Challenge**: Vulkan drivers on this specific chip are notoriously unstable with large compute shaders (LLM inference), often leading to crashes or hangs.
*   **Configuration Applied**:
    *   **Backend Priority**: **OpenCL** -> Vulkan -> CPU.
    *   **Reasoning**: Adreno GPUs have excellent OpenCL support. By prioritizing OpenCL, we avoid the initial crash on Vulkan completely.
    *   **Safety Net**: If OpenCL fails, it falls back to Vulkan with "Safe Mode" flags (FP16 disabled, small allocations), then CPU.
    *   **Batch Size**: Limited to **32** to prevent thermal throttling and driver timeouts.

## 2. Samsung Galaxy S25 / S24 FE (Exynos 2400/2500)
*   **Processor**: Exynos 2400 / 2500
*   **GPU**: Xclipse 940 (AMD RDNA3 architecture)
*   **Challenge**: New architecture, but AMD GPUs prefer Vulkan over OpenCL (which is often emulated or slow on Exynos).
*   **Configuration Applied**:
    *   **Backend Priority**: **Vulkan** -> OpenCL -> CPU.
    *   **Reasoning**: The Xclipse GPU is a "Vulkan-first" design. Recent driver updates for the S24/S25 series have stabilized Vulkan significantly.
    *   **Safety Net**: Standard crash detection will switch to CPU if Vulkan fails.
    *   **Batch Size**: Dynamic (up to 256) based on available RAM.

## Verification
*   **S22**: Should now launch without a "Safe Mode" warning and use OpenCL (Green/Yellow dot indicator depending on UI).
*   **S25 FE**: Should continue using Vulkan (Green dot) for maximum performance.
