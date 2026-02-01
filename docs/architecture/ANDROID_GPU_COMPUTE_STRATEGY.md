# Android GPU Compute Strategy: OpenCL & Vulkan Integration

This document outlines the research, implementation patterns, and strategic recommendations for integrating hybrid GPU compute (OpenCL + Vulkan) into the Android Note App, specifically focusing on the `llama.cpp` engine.

---

## 1. Executive Summary
On Android, GPU compute is fragmented across different System-on-Chips (SoCs). To maximize performance and stability, a **Dual-Backend Strategy** is required:
- **Snapdragon (Adreno):** OpenCL is the primary choice due to mature driver support and optimized kernels.
- **Mali (Exynos/MediaTek):** Vulkan Compute is preferred as Mali's OpenCL support is often less stable or optimized.
- **Google Tensor:** Recently added OpenCL 3.0 conformance (G5+), but currently benefits most from Vulkan.

---

## 2. Technical Implementation: The Hybrid JNI Bridge

### 2.1 Cross-API Interoperability
The most efficient method for sharing data between Vulkan and OpenCL on Android is **Zero-Copy Memory Sharing** via Android Hardware Buffers (AHB).

- **Extension:** `VK_ANDROID_external_memory_android_hardware_buffer`
- **Pattern:** 
    1. Create a `VkImage` or `VkBuffer` with external memory flags.
    2. Export the `AHardwareBuffer*` handle.
    3. Import the handle into OpenCL using `clImportMemoryARM` (on supported ARM/Adreno devices).
- **Synchronization:** Coordinate execution using `VK_KHR_external_semaphore` to prevent race conditions during buffer handoff.

### 2.2 Selection Logic (Hardware-Aware)
The application implements a **Tiered Backend Selection** in `native-lib.cpp` to navigate driver fragmentation:

| GPU Vendor | Detection Pattern | Target Backend | Reasoning |
|------------|-------------------|----------------|-----------|
| **Adreno** | `msm`, `sm`, `sdm`, `qcom` | **OpenCL** | Mature compute drivers; better GEMM performance. |
| **Mali**   | `exynos`, `mt`, `gs`, `zuma` | **Vulkan** | Historically poor CL support; modern Mali prefers SPIR-V. |
| **Unknown**| Fallback | **CPU** | Safety-first approach to prevent driver crashes. |

**Selection Flow:**
1. **Probe:** `GpuProbeService` runs a backend test in an isolated process.
2. **Detection:** JNI reads `ro.board.platform` and `ro.hardware`.
3. **Blacklist:** If an SoC is known-problematic (e.g., `sm8450` Snapdragon 8 Gen 1), it forces a CPU fallback regardless of vendor.

### 2.3 Testability & Validation
The native selection logic is decoupled from system property calls to allow unit testing without a specific hardware device.

- **Mocked Validation:** `NativeBackendSelectionTest.kt` uses the JNI method `testSelectionLogicNative(soc, hw)` to verify that specific hardware strings return the correct backend ID.
- **Verification Command (Run in WSL):**
  ```bash
  # Ensure you are in the project root within WSL
  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.synapsenotes.ai.core.ai.NativeBackendSelectionTest
  ```
- **Note:** Do not run this from Windows PowerShell/CMD, as the native build requires the WSL environment and NDK toolchain.

### 2.4 Build System (WSL/Android NDK)
Building for OpenCL on Android requires handling the absence of a standardized OpenCL header/library in the NDK.
- **Stubbing:** Use an `opencl_stub.c` to provide symbols during link-time in the build environment (WSL).
- **Runtime Linking:** The app dynamically loads `libOpenCL.so` from system paths (`/system/vendor/lib64/`) at runtime.
- **Embedded Kernels:** Use `GGML_OPENCL_EMBED_KERNELS=ON` to compile OpenCL C code into C++ headers, avoiding runtime file-path issues.

---

## 3. Performance & Stability Challenges

### 3.1 Known Issues in llama.cpp
| Issue | Severity | Topic | Fix/Mitigation |
|-------|----------|-------|----------------|
| **#5965** | High | OpenCL Slowdown | Use `CL_MEM_ALLOC_HOST_PTR` + `clEnqueueMapBuffer`. |
| **#11327** | High | Vulkan Crash | Avoid Vulkan on Adreno 7xx; use OpenCL fallback. |
| **#5186** | Medium | Adreno Shader Bug | Tail-duplicate branches in shaders to help Adreno compiler. |

### 3.2 Hardware Probing (GpuProbeService)
Because certain drivers crash during the initialization of Vulkan or OpenCL, the app utilizes an **Isolated Process Service**. 
- The probe process attempts to load the backend.
- If it crashes, the main process is notified and blacklists that backend for the specific device, falling back to CPU.

---

## 4. Local Codebase Assessment

As of **February 1, 2026**, the project state is:
- ✅ **Dual-Backend Ready:** Both Vulkan and OpenCL are enabled in `CMakeLists.txt`.
- ✅ **Vendor-Based Selection:** `native-lib.cpp` correctly prioritizes OpenCL for Adreno and Vulkan for Mali.
- ✅ **Embedded Kernels:** OpenCL kernels are embedded into the binary.
- ❌ **Missing Zero-Copy:** The current `ggml-opencl.cpp` uses `clEnqueueWriteBuffer` instead of zero-copy host mapping (`CL_MEM_ALLOC_HOST_PTR`).
- ❌ **Missing QCOM Extensions:** Proprietary `cl_qcom_ml_ops` are not yet integrated.

---

## 5. Resources & References

### Official Documentation
- **Khronos Vulkan OpenCL Interop Sample:** [README](https://docs.vulkan.org/samples/latest/samples/extensions/open_cl_interop/README.html) | [Source](https://github.com/KhronosGroup/Vulkan-Samples/tree/main/samples/extensions/open_cl_interop)
- **Qualcomm Adreno Best Practices:** [Optimization Guide](https://docs.qualcomm.com/doc/80-78185-2/topic/mobile_best_practices.html)
- **Android Hardware Buffer (AHB) Spec:** [VK_ANDROID_external_memory_android_hardware_buffer](https://registry.khronos.org/vulkan/specs/latest/man/html/VK_ANDROID_external_memory_android_hardware_buffer.html)

### Critical llama.cpp Issues
- **Issue #11327:** [Vulkan Android Cross-Compilation Failures](https://github.com/ggml-org/llama.cpp/issues/11327)
- **Issue #5965:** [OpenCL Slowdown on Adreno/Mali SoCs](https://github.com/ggml-org/llama.cpp/issues/5965)
- **Issue #5186:** [Adreno Shader Compilation Bugs](https://github.com/ggml-org/llama.cpp/issues/5186)

### Tools
- **clspv:** [OpenCL C to Vulkan SPIR-V Compiler](https://github.com/google/clspv)
- **Librarian Search Results:** [Verified Repository List](https://github.com/KhronosGroup/Vulkan-Samples)

---

## 6. Recommended Next Steps
1. **Zero-Copy Optimization:** Patch `ggml-opencl.cpp` to use `CL_MEM_ALLOC_HOST_PTR` for Snapdragon devices to resolve the potential 13x performance penalty.
2. **Unified Build Script:** Merge `build_opencl.sh` logic into `build_vulkan.sh` to ensure every production APK is dual-backend capable.
3. **Advanced Probes:** Update `NativeLib.probeBackendNative` to attempt a small shader execution to catch "immature driver" crashes before they impact the user experience.
