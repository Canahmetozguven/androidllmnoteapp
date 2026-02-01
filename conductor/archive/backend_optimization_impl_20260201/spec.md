# Track Specification: Backend Selection & Optimization Implementation

## Overview
This track implements the findings from the "Backend Optimization Research" initiative. The primary goal is to replace the generic "Vulkan-first" strategy with a vendor-aware logic: prioritizing **OpenCL for Adreno** (Qualcomm) GPUs and **Vulkan for Mali** (ARM) GPUs. It also introduces stability guardrails for memory-constrained devices.

## Objectives
-   **Vendor-Awareness:** Detect GPU vendor (Adreno vs. Mali) using standard Android APIs (Kotlin-side).
-   **Optimized Selection:**
    -   **Adreno:** Default to **OpenCL**.
    -   **Mali/Xclipse:** Default to **Vulkan**.
    -   **Unknown/Ambiguous:** Fallback to current behavior (Try OpenCL -> Vulkan -> CPU).
-   **Stability Guardrails:**
    -   Detect system RAM.
    -   If RAM < 8GB AND Backend is Vulkan: Soft-cap default `n_ctx` to 2048 to prevent OOM crashes (user custom settings override this).

## Functional Requirements
1.  **GPU Detection:** Implement Kotlin logic using `EGL14` or `SurfaceView` context to query `GL_RENDERER` string.
2.  **Backend Priority Logic:** Refactor `DefaultHardwareCapabilityProvider.kt` to use the detected vendor string to determine the `recommendedBackendOrder`.
3.  **RAM Mitigation:** In `LlmEngine.kt` (or similar config setup), check `ActivityManager.MemoryInfo`. If low RAM (<8GB) and Vulkan is selected, default `n_ctx` to 2048.

## Non-Functional Requirements
-   **No Regression:** Existing devices must not lose functionality.
-   **Zero-Overhead Probing:** GPU detection should be fast and not freeze the UI.

## Out of Scope
-   Implementation of new OpenCL kernels (we are using the existing ones).
-   Modifying `llama.cpp` source code directly (we are modifying the Android wrapper logic).
