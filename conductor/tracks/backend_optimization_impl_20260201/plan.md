# Implementation Plan - Backend Selection & Optimization

## Phase 1: GPU Detection & Backend Strategy
- [x] Task: Create `GpuInfoProvider.kt` (or similar utility) to query `GL_RENDERER` using Android EGL APIs (Kotlin-side).
- [x] Task: Refactor `DefaultHardwareCapabilityProvider.kt` to integrate `GpuInfoProvider`.
- [x] Task: Implement `getRecommendedBackendOrder()` logic:
    -   If Adreno: `[OPENCL, VULKAN, CPU]`
    -   If Mali/Xclipse: `[VULKAN, OPENCL, CPU]`
    -   Else (Unknown): `[OPENCL, VULKAN, CPU]`
- [x] Task: Unit Test `DefaultHardwareCapabilityProvider` with mocked GPU strings.
- [x] Task: Conductor - User Manual Verification 'Vendor-Aware Selection' (Protocol in workflow.md)

## Phase 2: Stability Guardrails (RAM & Context)
- [x] Task: Modify `LlmEngine.kt` to check system RAM (`ActivityManager.MemoryInfo`).
- [x] Task: Implement `resolveContextSize(userPref: Int, backend: BackendType)` logic:
    -   If `userPref` is set, use it.
    -   If `userPref` is default AND RAM < 8GB AND Backend == VULKAN: return 2048.
    -   Else: return default (e.g., 4096 or model default).
- [x] Task: Conductor - User Manual Verification 'Stability Guardrails' (Protocol in workflow.md)

## Phase 3: Validation & Build
- [x] Task: Verify WSL Build Environment: Ensure `build_opencl.sh` (or `build_vulkan.sh` if unified) produces libs with BOTH Vulkan and OpenCL enabled (`-DGGML_OPENCL=ON -DGGML_VULKAN=ON`).
- [x] Task: Create a new automated benchmark instrumented test that logs the selected backend and T/s.
- [x] Task: (Manual) Run on available device (S22 - Adreno) to confirm OpenCL is now auto-selected.
- [x] Task: Conductor - User Manual Verification 'Benchmarking' (Protocol in workflow.md)
