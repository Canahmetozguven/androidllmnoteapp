# Implementation Plan - OpenCL Backend Support

## Phase 1: Build Configuration & Native Setup
- [ ] Task: Update `app/build.gradle.kts` to support OpenCL build flags.
    - [ ] Sub-task: Add `GGML_OPENCL=ON` and related flags (CLBLAST if needed) to the CMake arguments.
    - [ ] Sub-task: Ensure the NDK configuration allows for OpenCL headers/libraries linkage.
- [ ] Task: Update `app/src/main/cpp/CMakeLists.txt` to link OpenCL libraries.
    - [ ] Sub-task: Locate or bundle OpenCL headers/stubs if not present in the NDK (usually requires `libOpenCL.so` stub).
    - [ ] Sub-task: Configure the `llama` target to link against OpenCL.
- [ ] Task: Verify Native Build.
    - [ ] Sub-task: Run `./build_vulkan.sh` (or create a new `build_all.sh`) to confirm the project compiles with OpenCL enabled.
- [ ] Task: Conductor - User Manual Verification 'Build Configuration & Native Setup' (Protocol in workflow.md)

## Phase 2: Runtime Detection & Backend Selection
- [ ] Task: Implement OpenCL availability check in JNI.
    - [ ] Sub-task: Create a native method `isOpenCLAvailable()` in `native-lib.cpp` that attempts to load `libOpenCL.so` or query platforms.
    - [ ] Sub-task: Expose this method to Kotlin via `LlamaContext` or `HardwareCapabilityProvider`.
- [ ] Task: Update `HardwareCapabilityProvider` logic.
    - [ ] Sub-task: Modify `getBestBackend()` to prioritize Vulkan -> OpenCL -> CPU.
    - [ ] Sub-task: Add logic to respect any user overrides if implemented (checking `AppPreferences`).
- [ ] Task: Unit Test Backend Selection.
    - [ ] Sub-task: Write tests for `HardwareCapabilityProvider` to verify correct selection order based on mocked availability.
- [ ] Task: Conductor - User Manual Verification 'Runtime Detection & Backend Selection' (Protocol in workflow.md)

## Phase 3: UI & Verification
- [ ] Task: Update Hardware Dashboard.
    - [ ] Sub-task: Ensure the UI can display "OpenCL" as the active backend.
    - [ ] Sub-task: Verify T/s reporting works correctly with the OpenCL backend.
- [ ] Task: Integration Test (Manual).
    - [ ] Sub-task: Force OpenCL backend (if possible via dev settings or code) and run a chat session.
    - [ ] Sub-task: Verify no crashes on model load or generation.
- [ ] Task: Conductor - User Manual Verification 'UI & Verification' (Protocol in workflow.md)
