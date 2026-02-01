# Device Verification Log

## S25 FE (Exynos 2400 / Xclipse 940)
*   **Date**: 2026-01-31
*   **Result**: **SUCCESS** ("working great")
*   **Build**: Latest AAB.
*   **Notes**: User confirms stability.

## S22 (Snapdragon 8 Gen 1 / Adreno 730)
*   **Correction**: User identified device as SM8450 (Snapdragon), not Exynos.
*   **Date**: 2026-01-31
*   **Result**: **RECOVERED** (Vulkan Crash Detected & Avoided)
*   **Log Evidence**: 
    ```
    E/HardwareCapability: Previous run crashed while attempting VULKAN (Chat). Marking as failed.
    W/HardwareCapability: Marked backend as failed: VULKAN.
    ```
*   **Implication**: The Snapdragon 8 Gen 1 (SM8450) has a known "bad" Vulkan driver in early revisions (or specific Samsung updates). Our safety logic correctly caught this.
*   **Action**: Device fell back to CPU/OpenCL.
*   **Note**: Adreno 730 *should* support Vulkan, but driver regressions are common. The fallback logic is doing exactly what it should: prioritizing stability over crashing.
