# OpenCL CMakeLists.txt Configuration Guide

This guide shows how to modify your CMakeLists.txt files to enable OpenCL support for Android LLM inference. **Start with Option A for simplicity, or Option B for flexible dynamic loading.**

---

## Current Configuration (Vulkan Only)

**File:** `app/src/main/cpp/CMakeLists.txt`

```cmake
# Current state - Vulkan enabled
add_subdirectory(llama)
```

**File:** `app/src/main/cpp/llama/CMakeLists.txt` (line 218)

```cmake
option(GGML_VULKAN "ggml: use Vulkan" OFF)  # <- Your build enables this
```

---

## Option A: Static OpenCL (Simplest)

Add OpenCL as a **static library** compiled into the main binary. Best for guaranteed availability.

### Modification 1: Update `app/src/main/cpp/llama/CMakeLists.txt`

**Change line 249 from:**
```cmake
option(GGML_OPENCL "ggml: use OpenCL" OFF)
```

**To:**
```cmake
option(GGML_OPENCL "ggml: use OpenCL" ON)
option(GGML_OPENCL_EMBED_KERNELS "ggml: embed kernels" ON)
option(GGML_OPENCL_USE_ADRENO_KERNELS "ggml: use optimized kernels for Adreno" ON)
```

### Build Command (WSL)

In `build_vulkan.sh`, change the CMake configuration step (around line 32):

**From:**
```bash
cmake .. -DGGML_VULKAN=ON -DCMAKE_BUILD_TYPE=Release
```

**To:**
```bash
cmake .. \
    -DGGML_VULKAN=ON \
    -DGGML_OPENCL=ON \
    -DGGML_OPENCL_EMBED_KERNELS=ON \
    -DGGML_OPENCL_USE_ADRENO_KERNELS=ON \
    -DCMAKE_BUILD_TYPE=Release
```

### Result
- ✅ Single binary with both Vulkan + OpenCL
- ✅ No need for dynamic library loading
- ❌ Larger APK size (~5-10 MB more)
- ❌ OpenCL cannot be disabled at runtime

---

## Option B: Dynamic OpenCL Loading (Flexible)

Load OpenCL as a **separate .so library at runtime**. Allows graceful fallback if loading fails.

### Modification 1: Update `app/src/main/cpp/llama/CMakeLists.txt`

**Change line 83 from:**
```cmake
option(GGML_BACKEND_DL "ggml: build backends as dynamic libraries (requires BUILD_SHARED_LIBS)" OFF)
```

**To:**
```cmake
option(GGML_BACKEND_DL "ggml: build backends as dynamic libraries (requires BUILD_SHARED_LIBS)" ON)
```

**Change line 249-252 from:**
```cmake
option(GGML_OPENCL "ggml: use OpenCL" OFF)
option(GGML_OPENCL_PROFILING "ggml: use OpenCL profiling (increases overhead)" OFF)
option(GGML_OPENCL_EMBED_KERNELS "ggml: embed kernels" ON)
option(GGML_OPENCL_USE_ADRENO_KERNELS "ggml: use optimized kernels for Adreno" ON)
```

**To:**
```cmake
option(GGML_OPENCL "ggml: use OpenCL" ON)
option(GGML_OPENCL_PROFILING "ggml: use OpenCL profiling (increases overhead)" OFF)
option(GGML_OPENCL_EMBED_KERNELS "ggml: embed kernels" ON)
option(GGML_OPENCL_USE_ADRENO_KERNELS "ggml: use optimized kernels for Adreno" ON)
```

### Build Command (WSL)

In `build_vulkan.sh`, change the CMake configuration step (around line 32):

**From:**
```bash
cmake .. -DGGML_VULKAN=ON -DCMAKE_BUILD_TYPE=Release
```

**To:**
```bash
cmake .. \
    -DGGML_VULKAN=ON \
    -DGGML_OPENCL=ON \
    -DGGML_BACKEND_DL=ON \
    -DGGML_OPENCL_EMBED_KERNELS=ON \
    -DGGML_OPENCL_USE_ADRENO_KERNELS=ON \
    -DCMAKE_BUILD_TYPE=Release
```

### Deployment Structure

After building with dynamic loading, your native libs directory will contain:

```
app/src/main/jniLibs/arm64-v8a/
├── libllama.so              # Main library (with Vulkan)
└── libggml-opencl.so        # Optional OpenCL backend (loaded dynamically)
```

### JNI Backend Loading (C++)

In your `native-lib.cpp`, add this to load the OpenCL backend:

```cpp
// Optionally load the OpenCL backend at startup
// This happens automatically when ggml_backend_init_best() is called,
// but you can force it explicitly:

ggml_backend_t opencl_backend = ggml_backend_init_by_name("opencl", nullptr);
if (opencl_backend) {
    __android_log_print(ANDROID_LOG_INFO, "LLM_JNI", "✅ OpenCL backend loaded successfully");
} else {
    __android_log_print(ANDROID_LOG_INFO, "LLM_JNI", "⚠️ OpenCL backend not available, falling back to Vulkan");
}
```

### Result
- ✅ Smaller main binary (OpenCL is separate)
- ✅ Graceful fallback if .so is missing
- ✅ Can disable at runtime (don't load the .so)
- ⚠️ Requires `libOpenCL.so` on device (usually available on Snapdragon)
- ⚠️ Extra deployment step for OpenCL .so

---

## Option C: Adreno-Optimized Only (Android-Specific)

If you ONLY want Adreno GPU support (no generic OpenCL), use this configuration:

### Build Command (WSL)

```bash
cmake .. \
    -DGGML_VULKAN=OFF \
    -DGGML_OPENCL=ON \
    -DGGML_OPENCL_EMBED_KERNELS=ON \
    -DGGML_OPENCL_USE_ADRENO_KERNELS=ON \
    -DCMAKE_BUILD_TYPE=Release
```

### Build Flags Breakdown

| Flag | Meaning | For Android? |
|------|---------|--------------|
| `GGML_OPENCL=ON` | Enable OpenCL backend | Yes |
| `GGML_OPENCL_EMBED_KERNELS=ON` | Embed GPU kernels in binary | Yes (required) |
| `GGML_OPENCL_USE_ADRENO_KERNELS=ON` | Use Snapdragon optimizations | Yes (recommended) |
| `GGML_BACKEND_DL=ON` | Load as dynamic library | Optional |
| `GGML_VULKAN=ON` | Also build Vulkan (your current) | Yes (keep current) |

---

## Important Notes

### 1. Android SDK Requirements

Your `app/build.gradle.kts` must target API 35:

```kotlin
android {
    compileSdk = 35
    targetSdk = 35
    minSdk = 34
}
```

**Reason:** OpenCL support on Android requires modern NDK + SDK compatibility.

### 2. NDK Headers

Verify your NDK has OpenCL headers at:
```
$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/CL/
```

Expected files:
- `cl.h`
- `cl_platform.h`
- `cl_ext.h`

### 3. Runtime Library Availability

Android provides **libOpenCL.so** from the ICD loader. Verify on your device:

```bash
adb shell find /system -name "libOpenCL*"
```

Expected output:
```
/system/lib64/libOpenCL.so
```

If missing, the Snapdragon device doesn't support OpenCL (use Vulkan fallback).

### 4. Binary Size Impact

| Configuration | APK Size Change |
|---|---|
| Static OpenCL | +5-10 MB |
| Dynamic OpenCL | +2-3 MB (main) + 2-3 MB (libggml-opencl.so) |
| Vulkan Only (current) | Baseline |

---

## Testing Your Configuration

### 1. Build with OpenCL

```bash
cd ~/projects/android_note_app
./build_vulkan.sh
```

### 2. Check for libggml-opencl.so

```bash
ls -lh app/build/intermediates/cmake/debug/obj/arm64-v8a/ | grep opencl
```

### 3. Deploy and Test

```powershell
# In Windows
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat | grep -i "opencl\|llm"
```

### 4. Verify Backend in Logs

Expected log output:
```
LLM_JNI: Backend initialized: OpenCL (Adreno)
LLM_JNI: GPU vendor: Qualcomm
```

Or fallback:
```
LLM_JNI: OpenCL backend not available, falling back to Vulkan
```

---

## Reverting to Vulkan Only

If you want to revert to your current Vulkan-only setup:

```cmake
# In app/src/main/cpp/llama/CMakeLists.txt, line 249
option(GGML_OPENCL "ggml: use OpenCL" OFF)  # <- Back to OFF
```

And in `build_vulkan.sh`:
```bash
cmake .. -DGGML_VULKAN=ON -DCMAKE_BUILD_TYPE=Release
```

---

## Summary Comparison

| Aspect | Option A (Static) | Option B (Dynamic) | Current (Vulkan) |
|--------|---|---|---|
| **Complexity** | Low | Medium | ✅ Simplest |
| **APK Size** | +5-10 MB | +2-3 MB | Baseline |
| **Flexibility** | Fixed | ✅ Configurable | N/A |
| **Fallback** | ❌ No | ✅ Yes | N/A |
| **Speed** | ✅ Fastest | Fast | Current |
| **Recommended for** | Production | Development/Testing | Keep current |

---

## Advanced: Custom CMakeLists.txt Template

Save this as `CMakeLists.txt.opencl` for easy switching:

```cmake
# OpenCL Configuration Template
# To use: cp CMakeLists.txt.opencl CMakeLists.txt && ./build_vulkan.sh

cmake_minimum_required(VERSION 3.14)
project(LlamaCpp C CXX)

# === GPU Acceleration ===
set(GGML_VULKAN ON)          # Keep Vulkan (production stable)
set(GGML_OPENCL ON)          # Add OpenCL (Snapdragon support)
set(GGML_BACKEND_DL ON)      # Dynamic loading (flexible)
set(GGML_OPENCL_EMBED_KERNELS ON)
set(GGML_OPENCL_USE_ADRENO_KERNELS ON)

# === Android Native Build ===
set(CMAKE_BUILD_TYPE Release)
set(CMAKE_SYSTEM_NAME Android)
set(CMAKE_SYSTEM_VERSION ${ANDROID_PLATFORM})
set(CMAKE_ANDROID_ARCH_ABI arm64-v8a)
set(CMAKE_ANDROID_NDK ${ANDROID_NDK})
set(CMAKE_ANDROID_STL c++_shared)

# ... rest of your CMakeLists.txt
```

---

## References

- **GGML OpenCL Docs:** `docs/backend/OPENCL.md` in llama.cpp repo
- **Android Build Guide:** `docs/android.md` in llama.cpp repo
- **Adreno GPU Info:** Qualcomm OpenCL Kernels for Adreno  
- **Dynamic Loading:** `app/src/main/cpp/llama/ggml/src/ggml-backend-reg.cpp`
