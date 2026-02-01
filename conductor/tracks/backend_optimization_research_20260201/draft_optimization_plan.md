# Implementation Plan - Backend Selection & Optimization

## Phase 1: Vendor-Aware Backend Selection
- [ ] Task: Extend JNI interface in `native-lib.cpp` to expose GPU vendor/renderer string to Kotlin.
    - [ ] Sub-task: Add `getGpuVendorNative()` method.
    - [ ] Sub-task: Implement `glGetString(GL_RENDERER)` or equivalent detection logic.
- [ ] Task: Refactor `DefaultHardwareCapabilityProvider.kt` to implement vendor-specific logic.
    - [ ] Sub-task: Implement `Adreno -> OpenCL`, `Mali -> Vulkan` priority.
    - [ ] Sub-task: Add unit tests for `getRecommendedBackendOrder` with mock GPU strings.
- [ ] Task: Update `LlmEngine.kt` to respect the new strictly ordered preferences.
- [ ] Task: Conductor - User Manual Verification 'Vendor-Aware Selection' (Protocol in workflow.md)

## Phase 2: Driver Stability Guardrails
- [ ] Task: Implement "Vulkan RAM Spike" mitigation.
    - [ ] Sub-task: In `LlmEngine.kt`, check `ActivityManager.MemoryInfo`.
    - [ ] Sub-task: If `totalMem < 8GB` AND backend is Vulkan, cap `n_ctx` to 2048 (override user pref if needed, or warn).
- [ ] Task: Implement Adreno Watchdog mitigation.
    - [ ] Sub-task: Add a `batch_size` limit override for OpenCL backend on Adreno (e.g., max 512).
- [ ] Task: Conductor - User Manual Verification 'Stability Guardrails' (Protocol in workflow.md)

## Phase 3: Benchmarking & Validation
- [ ] Task: Create a new automated benchmark instrumented test.
    - [ ] Sub-task: Run prompt processing (PP) and token generation (TG) tests on available backends.
    - [ ] Sub-task: Log results to a file for analysis.
- [ ] Task: (Manual) Run benchmarks on available test devices (S22/Adreno, Pixel/Mali if available).
- [ ] Task: Conductor - User Manual Verification 'Benchmarking' (Protocol in workflow.md)
