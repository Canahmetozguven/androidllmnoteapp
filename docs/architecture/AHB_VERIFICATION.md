# AHB Interop Verification Guide

## Overview
This document details the verification steps for the newly implemented Vulkan/OpenCL Android Hardware Buffer (AHB) interoperability.

## 1. Device Matrix
The following devices are targeted for verification:

| Device | SoC | GPU | Expected Behavior |
|--------|-----|-----|-------------------|
| **Samsung S24 / S25** | Exynos 2400 / SD 8 Gen 3 | Mali-G720 / Adreno 750 | **AHB Supported** (Vulkan + OpenCL) |
| **Pixel 8** | Tensor G3 | Mali-G715 | **AHB Supported** (Mali path) |
| **Pixel 7 / 7a** | Tensor G2 | Mali-G710 | **AHB Supported** (Mali path) |
| **OnePlus 11/12** | SD 8 Gen 2/3 | Adreno 740/750 | **AHB Supported** (Qualcomm path - cl_qcom) |
| **Old Device (Pixel 3/4)** | SD 845 / 855 | Adreno 630/640 | **Fallback** (Likely missing extension or API < 28) |
| **Emulator** | x86_64 | SwiftShader | **Fallback** (No OpenCL, No AHB) |

## 2. Verification Commands (ADB)

### A. Capability Check
Run the app and grep for the new capability logs:

```bash
# 1. Clear logs
adb logcat -c

# 2. Run App (Wait for initialization)

# 3. Check for AHB Capability Summary
adb logcat -d -s LLM_JNI | grep "AHB Capability Summary"
```

**Expected Output (Supported):**
> `LLM_JNI: AHB Capability Summary - Vendor: Mali (or Adreno), Vulkan AHB: 1, ARM Import: 1, ARM AHB: 1, QCOM AHB: 0, Overall: AVAILABLE`

**Expected Output (Unsupported/Fallback):**
> `LLM_JNI: AHB Capability Summary - ... Overall: NOT AVAILABLE`

### B. Detailed Extension Checks
Verify specific extensions were found:

```bash
adb logcat -d -s LLM_JNI | grep "OpenCL:"
```

**Expected (Mali):**
> `OpenCL: cl_arm_import_memory available`
> `OpenCL: cl_arm_import_memory_android_hardware_buffer available`

**Expected (Qualcomm):**
> `OpenCL: cl_qcom_android_hardware_buffer_interop available`

### C. Kotlin Integration
Verify the Kotlin layer received the correct status:

```bash
adb logcat -d -s HardwareCapability | grep "AHB Interop Support"
```

**Expected:**
> `HardwareCapability: AHB Interop Support - Vulkan: true, OpenCL: true, Overall: true`

## 3. Crash Safety Verification
If the device does not support AHB (or crashes during probe):
1. The app should **NOT** crash on launch.
2. It should log `Overall: NOT AVAILABLE`.
3. It should proceed to load the model using the standard fallback path (CPU or non-interop GPU).

## 4. Build Verification
To verify the native build locally (if you have NDK):
```bash
# Check symbols in libllm_notes_cpp.so
nm -D app/build/intermediates/cmake/release/obj/arm64-v8a/libllm_notes_cpp.so | grep Java_com_synapsenotes_ai_core_ai_LlamaContext_isAHBSupported
```
