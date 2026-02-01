# Plan: Native Backend Selection Testability Refactor

## 1. Goal
Improve the testability of the hardware-aware backend selection logic in `native-lib.cpp` and implement a validation test suite.

## 2. Changes
### Native Layer (`native-lib.cpp`)
- Extract logic from `detect_gpu_vendor()` and `is_problematic_vulkan_device()` into "Pure" C++ functions:
    - `GPUVendor resolve_gpu_vendor(const std::string& soc, const std::string& hardware)`
    - `bool check_problematic_vulkan(const std::string& soc, const std::string& hardware)`
- Expose a new JNI method `testSelectionLogic(String soc, String hardware)` for testing purposes.

### Testing Layer (`androidTest`)
- Create `NativeBackendSelectionTest.kt`.
- Use the new JNI entry point to verify that:
    - `"sm8450"` (S21/S22) correctly triggers CPU fallback.
    - `"qcom"` correctly selects OpenCL.
    - `"exynos"` correctly selects Vulkan.

## 3. Verification
- Run `./gradlew connectedDebugAndroidTest` on an emulator.
- Verify the test suite passes by simulating various SoC signatures.

## 4. Documentation
- Update `ANDROID_GPU_COMPUTE_STRATEGY.md` with a section on how to add and verify new problematic SoCs.
