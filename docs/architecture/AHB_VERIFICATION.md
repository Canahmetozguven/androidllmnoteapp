# AHB Interop Verification Guide

## Overview

This document provides agent-executable verification steps for Android Hardware Buffer (AHB) interoperability between Vulkan and OpenCL. All steps use `adb` command-line tools and logcat parsing—no manual inspection required.

---

## Quick Verification Workflow

### 1. Deploy App and Check Initialization Logs

```bash
# Build and install the app (if not already installed)
adb install -r app/build/outputs/apk/release/app-release.apk

# Clear logcat and start logging
adb logcat -c
adb logcat -v threadtime > logcat.txt &

# Launch the app
adb shell am start -n com.synapsenotes.ai/.ui.MainActivity

# Wait 3 seconds for JNI initialization
sleep 3

# Capture logs (Ctrl+C the background logcat to stop)
```

### 2. Verify AHB Capability Detection

```bash
# Extract AHB capability logs
adb logcat -d | grep -i "AHB"

# Expected output (varies by device):
# I LLM_JNI: === AHB Capability Detection ===
# I LLM_JNI: Checking Vulkan AHB support: VK_ANDROID_external_memory_android_hardware_buffer
# I LLM_JNI: Vulkan AHB extension: AVAILABLE (assumed for API 28+)
# I LLM_JNI: Checking OpenCL AHB support
# I LLM_JNI: OpenCL Platform Extensions: cl_khr_icd cl_arm_import_memory ...
# I LLM_JNI: cl_arm_import_memory: YES
# I LLM_JNI: cl_arm_import_memory_android_hardware_buffer: YES
# I LLM_JNI: AHB Summary: Vulkan=YES, OpenCL=YES
```

### 3. Check Backend Selection Logs

```bash
# Verify AHB interop path selection
adb logcat -d | grep -i "AhbManager\|interop"

# Expected patterns:
# I AHB_MANAGER: Selected interop path: ARM Mali
# I AHB_MANAGER: Selected interop path: Qualcomm Adreno
# I AHB_MANAGER: Selected interop path: None (fallback)
```

---

## Comprehensive Verification Commands

### A. Extension Detection Verification

#### Vulkan Extension Check
```bash
adb logcat -d | grep "Vulkan AHB extension"
```

**Expected Outputs**:
- ✅ **API 28+**: `Vulkan AHB extension: AVAILABLE (assumed for API 28+)`
- ❌ **API < 28**: Not applicable (app requires API 28+)

#### OpenCL Extension Check (ARM Path)
```bash
adb logcat -d | grep -E "cl_arm_import_memory|cl_arm_import_memory_android_hardware_buffer"
```

**Expected Outputs**:
- ✅ **ARM Mali**: Both extensions YES
  ```
  cl_arm_import_memory: YES
  cl_arm_import_memory_android_hardware_buffer: YES
  ```
- ❌ **Non-ARM**: Both extensions NO
  ```
  cl_arm_import_memory: NO
  cl_arm_import_memory_android_hardware_buffer: NO
  ```

#### OpenCL Extension Check (Qualcomm Path)
```bash
adb logcat -d | grep "cl_qcom_android_hardware_buffer_interop"
```

**Expected Outputs**:
- ✅ **Qualcomm Adreno**: Extension YES
  ```
  cl_qcom_android_hardware_buffer_interop: YES
  ```
- ❌ **Non-Qualcomm**: Extension NO
  ```
  cl_qcom_android_hardware_buffer_interop: NO
  ```

### B. Vendor Detection Verification

#### Detect GPU Vendor
```bash
adb logcat -d | grep -E "GPU vendor|SoC|ro.board.platform"
```

**Sample Output**:
```
I LLM_JNI: Detected GPU vendor: MALI (Exynos, ro.soc.model=Exynos2200)
I LLM_JNI: Detected GPU vendor: ADRENO (Snapdragon, ro.board.platform=msm8998)
```

#### Verify via System Properties
```bash
# Check board platform
adb shell getprop ro.board.platform

# Check hardware
adb shell getprop ro.hardware

# Check SoC model
adb shell getprop ro.soc.model
```

**Vendor Mapping**:
| `ro.board.platform` | `ro.hardware` | `ro.soc.model` | Vendor |
|---|---|---|---|
| `msm*`, `sdm*` | `qcom` | `SM*` | Qualcomm Adreno |
| `exynos*` | `exynos*` | `Exynos*` | ARM Mali |
| `mt*`, `helio*` | `mediatek` | `MT*`, `Dimensity*` | ARM Mali |
| `google*` | `tensor*` | `Tensor*` | ARM Mali |

### C. AHB Interop Path Validation

#### Detect Selected Interop Path
```bash
adb logcat -d | grep -i "Selected interop path\|interop type"
```

**Expected Patterns**:
- ✅ **ARM Mali**: `Selected interop path: ARM Mali (AHB_PATH_ARM = 1)`
- ✅ **Qualcomm Adreno**: `Selected interop path: Qualcomm Adreno (AHB_PATH_QCOM = 2)`
- ⚠️ **Fallback**: `Selected interop path: None (AHB_PATH_NONE = 0)`

#### Check Backend Selection Logs
```bash
adb logcat -d | grep -E "Backend selected|OpenCL|Vulkan" | head -20
```

---

## Device Verification Matrix

### Test Cases by Device Type

#### 1. ARM Mali Devices (Samsung Exynos, Google Tensor, MediaTek)

**Test: Samsung S22 (Exynos 2200)**

```bash
# Device info
adb shell getprop ro.board.platform      # Expected: exynos*
adb shell getprop ro.soc.model           # Expected: Exynos2200

# Verification steps
adb logcat -c
adb shell am start -n com.synapsenotes.ai/.ui.MainActivity
sleep 3

# Check AHB support
adb logcat -d | grep -E "AHB|Exynos"

# Expected: All ARM extensions should be YES
adb logcat -d | grep "cl_arm_import_memory: YES"
adb logcat -d | grep "cl_arm_import_memory_android_hardware_buffer: YES"

# Expected: Qualcomm extension should be NO
adb logcat -d | grep "cl_qcom_android_hardware_buffer_interop: NO"
```

**Expected Log Output**:
```
I LLM_JNI: Detected GPU vendor: MALI (Exynos, ro.soc.model=Exynos2200)
I LLM_JNI: cl_arm_import_memory: YES
I LLM_JNI: cl_arm_import_memory_android_hardware_buffer: YES
I LLM_JNI: cl_qcom_android_hardware_buffer_interop: NO
I AHB_MANAGER: Selected interop path: ARM Mali (AHB_PATH_ARM = 1)
```

**Success Criteria**:
- [ ] ARM extensions detected as YES
- [ ] Qualcomm extension detected as NO
- [ ] Interop path selected as ARM Mali

---

**Test: Google Pixel 9 (Tensor G4)**

```bash
# Device info
adb shell getprop ro.board.platform      # Expected: google*
adb shell getprop ro.soc.model           # Expected: Tensor*

# Verification steps (same as S22)
adb logcat -c
adb shell am start -n com.synapsenotes.ai/.ui.MainActivity
sleep 3

# Check AHB support
adb logcat -d | grep -E "Tensor|cl_arm"
```

**Expected Log Output**:
```
I LLM_JNI: Detected GPU vendor: MALI (Tensor, ro.soc.model=TensorG4)
I LLM_JNI: cl_arm_import_memory: YES
I LLM_JNI: cl_arm_import_memory_android_hardware_buffer: YES
I AHB_MANAGER: Selected interop path: ARM Mali (AHB_PATH_ARM = 1)
```

**Success Criteria**:
- [ ] ARM extensions detected as YES
- [ ] Interop path selected as ARM Mali

---

**Test: MediaTek Device (Dimensity)**

```bash
# Device info
adb shell getprop ro.board.platform      # Expected: mt*
adb shell getprop ro.soc.model           # Expected: Dimensity*

# Verification steps
adb logcat -c
adb shell am start -n com.synapsenotes.ai/.ui.MainActivity
sleep 3

# Check AHB support
adb logcat -d | grep -E "MediaTek|Dimensity|cl_arm"
```

**Expected Log Output**:
```
I LLM_JNI: Detected GPU vendor: MALI (MediaTek, ro.soc.model=Dimensity9400)
I LLM_JNI: cl_arm_import_memory: YES
I LLM_JNI: cl_arm_import_memory_android_hardware_buffer: YES
I AHB_MANAGER: Selected interop path: ARM Mali (AHB_PATH_ARM = 1)
```

---

#### 2. Qualcomm Adreno Devices (Snapdragon)

**Test: Snapdragon 8 Gen 2 Device**

```bash
# Device info
adb shell getprop ro.board.platform      # Expected: msm* or sdm*
adb shell getprop ro.soc.model           # Expected: SM8*

# Verification steps
adb logcat -c
adb shell am start -n com.synapsenotes.ai/.ui.MainActivity
sleep 3

# Check AHB support
adb logcat -d | grep -E "Snapdragon|Adreno|cl_qcom"

# Expected: QCOM extension should be YES
adb logcat -d | grep "cl_qcom_android_hardware_buffer_interop: YES"

# Expected: ARM extensions should be NO
adb logcat -d | grep "cl_arm_import_memory: NO"
adb logcat -d | grep "cl_arm_import_memory_android_hardware_buffer: NO"
```

**Expected Log Output**:
```
I LLM_JNI: Detected GPU vendor: ADRENO (Snapdragon, ro.board.platform=msm8998)
I LLM_JNI: cl_arm_import_memory: NO
I LLM_JNI: cl_arm_import_memory_android_hardware_buffer: NO
I LLM_JNI: cl_qcom_android_hardware_buffer_interop: YES
I AHB_MANAGER: Selected interop path: Qualcomm Adreno (AHB_PATH_QCOM = 2)
```

**Success Criteria**:
- [ ] Qualcomm extension detected as YES
- [ ] ARM extensions detected as NO
- [ ] Interop path selected as Qualcomm Adreno

---

#### 3. Emulator (CPU-Only, No OpenCL)

**Test: Android Emulator (x86_64, API 36)**

```bash
# Device info
adb shell getprop ro.kernel.qemu        # Should be 1 (emulator)
adb shell getprop ro.hardware           # Expected: ranchu (QEMU ARM)

# Verification steps
adb logcat -c
adb shell am start -n com.synapsenotes.ai/.ui.MainActivity
sleep 3

# Check AHB support
adb logcat -d | grep -E "OpenCL|emulator|library not"

# Expected: OpenCL library not available
adb logcat -d | grep "OpenCL library not available"
```

**Expected Log Output**:
```
I LLM_JNI: Checking OpenCL AHB support
I LLM_JNI: OpenCL library not available
I LLM_JNI: OpenCL AHB extensions: UNAVAILABLE
I AHB_MANAGER: Selected interop path: None (AHB_PATH_NONE = 0) - fallback to standard backend
```

**Success Criteria**:
- [ ] OpenCL library unavailable (expected for emulator)
- [ ] Interop path selected as None (fallback mode)
- [ ] Vulkan still available (CPU can use Vulkan)

---

### Summary Test Matrix

| Device | Platform | GPU | Expected Vendor | Expected Extensions | Expected Path | Fallback |
|---|---|---|---|---|---|---|
| **S22** | Exynos 2200 | Mali | MALI | ARM: YES, QCOM: NO | ARM Mali | CPU/Standard |
| **Pixel 9** | Tensor G4 | Mali | MALI | ARM: YES, QCOM: NO | ARM Mali | CPU/Standard |
| **Dimensity** | Dimensity 9400 | Mali | MALI | ARM: YES, QCOM: NO | ARM Mali | CPU/Standard |
| **Snapdragon 8** | SM8**** | Adreno | ADRENO | ARM: NO, QCOM: YES | QCOM Adreno | CPU/Standard |
| **Emulator** | x86_64 | CPU | UNKNOWN | ARM: NO, QCOM: NO | None | CPU |

---

## Scripted Verification Suite

### All-in-One Test Script

Save as `verify_ahb.sh`:

```bash
#!/bin/bash

set -e

echo "=== AHB Verification Suite ==="
echo

# 1. Get device info
echo "1. Device Information:"
PLATFORM=$(adb shell getprop ro.board.platform)
HARDWARE=$(adb shell getprop ro.hardware)
SOC=$(adb shell getprop ro.soc.model)
echo "  Platform: $PLATFORM"
echo "  Hardware: $HARDWARE"
echo "  SoC: $SOC"
echo

# 2. Deploy and launch
echo "2. Launching app..."
adb logcat -c
adb shell am start -n com.synapsenotes.ai/.ui.MainActivity
sleep 3
echo "  App launched."
echo

# 3. Check Vulkan support
echo "3. Vulkan AHB Support:"
if adb logcat -d | grep -q "Vulkan AHB extension: AVAILABLE"; then
    echo "  ✅ AVAILABLE"
else
    echo "  ⚠️  Not found in logs"
fi
echo

# 4. Check ARM extensions
echo "4. ARM Extensions (cl_arm_import_memory*):"
if adb logcat -d | grep -q "cl_arm_import_memory: YES"; then
    echo "  ✅ cl_arm_import_memory: YES"
else
    echo "  ❌ cl_arm_import_memory: NO"
fi
if adb logcat -d | grep -q "cl_arm_import_memory_android_hardware_buffer: YES"; then
    echo "  ✅ cl_arm_import_memory_android_hardware_buffer: YES"
else
    echo "  ❌ cl_arm_import_memory_android_hardware_buffer: NO"
fi
echo

# 5. Check QCOM extension
echo "5. Qualcomm Extension (cl_qcom_android_hardware_buffer_interop):"
if adb logcat -d | grep -q "cl_qcom_android_hardware_buffer_interop: YES"; then
    echo "  ✅ AVAILABLE"
else
    echo "  ❌ Not available"
fi
echo

# 6. Check selected path
echo "6. Selected Interop Path:"
if adb logcat -d | grep -q "ARM Mali"; then
    echo "  ✅ ARM Mali selected"
elif adb logcat -d | grep -q "Qualcomm Adreno"; then
    echo "  ✅ Qualcomm Adreno selected"
elif adb logcat -d | grep -q "Selected interop path: None"; then
    echo "  ⚠️  Fallback (no AHB support)"
else
    echo "  ❓ Path not found in logs"
fi
echo

# 7. Output raw logs for inspection
echo "=== Raw AHB Logs ==="
adb logcat -d | grep -i "ahb\|interop" | head -20 || echo "  (No logs found)"
echo

echo "=== Test Complete ==="
```

Run the script:
```bash
chmod +x verify_ahb.sh
./verify_ahb.sh
```

---

## Expected Log Patterns Reference

### Success Patterns

#### ARM Mali Device
```regex
Detected GPU vendor: MALI
cl_arm_import_memory: YES
cl_arm_import_memory_android_hardware_buffer: YES
cl_qcom_android_hardware_buffer_interop: NO
Selected interop path: ARM Mali
```

#### Qualcomm Adreno Device
```regex
Detected GPU vendor: ADRENO
cl_arm_import_memory: NO
cl_arm_import_memory_android_hardware_buffer: NO
cl_qcom_android_hardware_buffer_interop: YES
Selected interop path: Qualcomm Adreno
```

#### Emulator / CPU-Only
```regex
OpenCL library not available
OpenCL AHB extensions: UNAVAILABLE
Selected interop path: None
```

### Error Patterns (Investigate if Found)

```regex
# OpenCL library load error
ERROR: Failed to load OpenCL library

# Extension query failure
ERROR: Failed to query platform extensions

# Vendor detection failure
GPU vendor: UNKNOWN

# Interop initialization failure
ERROR: Failed to initialize AhbManager
```

---

## Troubleshooting Guide

### Issue: "OpenCL library not available"

**Cause**: OpenCL not installed or not in standard library path  
**Expected On**: Emulator, some budget devices  
**Action**: Check if device has GPU support with:
```bash
adb logcat -d | grep -i "opencl\|gpu"
```

### Issue: "cl_arm_import_memory: NO" on ARM Device

**Cause**: OpenCL extensions not exposed by driver  
**Expected On**: Rare, usually indicates driver issue  
**Action**: 
1. Verify device is ARM Mali with `adb shell getprop ro.soc.model`
2. Check for driver version issues
3. Try another ARM device to isolate issue

### Issue: "AHB Summary: Vulkan=NO"

**Cause**: API < 28 (assumed not available)  
**Expected On**: Devices with Android API < 28  
**Action**: Upgrade to API 28+ device for testing

### Issue: No "AHB Capability Detection" logs at all

**Cause**: App not properly deployed, JNI not initialized, or old APK version  
**Action**:
```bash
# Clean and rebuild
./build.sh static

# Verify APK has latest native-lib.so
adb install -r app/build/outputs/apk/release/app-release.apk

# Re-run verification
adb logcat -c
adb shell am start -n com.synapsenotes.ai/.ui.MainActivity
sleep 3
adb logcat -d | grep "LLM_JNI.*AHB"
```

---

## Automation & CI/CD Integration

### GitHub Actions Example

```yaml
name: AHB Verification

on: [push]

jobs:
  verify-ahb:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Android SDK
        uses: android-actions/setup-android@v2
      
      - name: Build APK
        run: ./build.sh static
      
      - name: Start Emulator
        run: |
          echo "no" | avdmanager create avd -n test_emulator -k "system-images;android;36;google_apis_playstore;x86_64" || true
          emulator -avd test_emulator -no-audio -no-window &
          adb wait-for-device
      
      - name: Install APK
        run: adb install -r app/build/outputs/apk/release/app-release.apk
      
      - name: Verify AHB
        run: |
          adb logcat -c
          adb shell am start -n com.synapsenotes.ai/.ui.MainActivity
          sleep 3
          adb logcat -d | grep "AHB Summary"
```

---

## References

### OpenCL Extensions
- `cl_arm_import_memory`: [ARM Mali OpenCL Extension](https://github.com/ARM-software/openvx-samples)
- `cl_qcom_android_hardware_buffer_interop`: Qualcomm Adreno (vendor-specific)

### Vulkan Extensions
- `VK_ANDROID_external_memory_android_hardware_buffer`: [Khronos Registry](https://www.khronos.org/registry/vulkan/specs/1.3-extensions/man/html/VK_ANDROID_external_memory_android_hardware_buffer.html)

### AHB Documentation
- `android/hardware_buffer.h`: AOSP NDK documentation

### Related Documentation
- `IMPLEMENTATION_SUMMARY.md`: Architecture overview
- `VULKAN_VS_OPENCL_PERFORMANCE.md`: Backend selection logic
- `ANDROID_GPU_COMPUTE_STRATEGY.md`: GPU vendor detection strategy
