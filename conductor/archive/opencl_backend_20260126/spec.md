# Track Specification: OpenCL Backend Support

## Goal
To implement OpenCL as a secondary hardware acceleration backend for `llama.cpp` on Android, enabling GPU inference on devices where Vulkan is unavailable or less performant (e.g., older Adreno or Mali GPUs).

## Requirements
- **Integration**: Enable OpenCL support in the native build configuration (`CMakeLists.txt` and `build.gradle.kts`).
- **Detection**: Implement logic to detect OpenCL availability at runtime.
- **Selection**: Update the `HardwareCapabilityProvider` to choose between Vulkan, OpenCL, and CPU based on device capabilities and user preference.
- **Verification**: Ensure the OpenCL backend correctly loads models and performs inference without crashing.
- **Performance**: Benchmark OpenCL performance against CPU fallback to verify acceleration benefits.

## Out of Scope
- Optimizing OpenCL kernels for specific GPU architectures (using default `llama.cpp` kernels).
- Advanced UI controls for manual kernel tuning.

## Success Criteria
- The app builds successfully with both Vulkan and OpenCL support enabled.
- The "Hardware Dashboard" correctly identifies when the OpenCL backend is active.
- Inference runs successfully (generating text) using the OpenCL backend on a supported device/emulator.
