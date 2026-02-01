# llama.cpp Android Optimization Research Findings

## Scope
This report focuses on Android optimization for `llama.cpp`, covering GPU backend performance (Vulkan vs OpenCL) on Adreno/Mali, ARMv8 CPU compiler flags, and memory handling/deployment best practices.

## 1) Vulkan vs OpenCL performance on Adreno/Mali

### Observed community reports
- **OpenCL can underperform vs CPU on mobile GPUs.** A llama.cpp issue reports OpenCL on **Adreno and Mali** being *slower than CPU*, highlighting driver/stack maturity and kernel efficiency concerns on some devices. This suggests OpenCL is not universally faster on mobile GPUs and can regress performance in real deployments. Source: https://github.com/ggerganov/llama.cpp/issues/5965
- **Vulkan backend performance can be poor on Android devices.** A llama.cpp discussion reports **very bad performance** when running the Vulkan backend on Android GPUs, indicating that Vulkan acceleration is sensitive to device/driver combinations and may require tuning or fallback strategies. Source: https://github.com/ggerganov/llama.cpp/discussions/9464

### Current OpenCL progress for Adreno
- **Qualcomm’s OpenCL backend is now upstreamed** and optimized for **Adreno GPUs**, indicating active vendor-led optimization for OpenCL on Snapdragon devices. This is a positive signal for Adreno-specific devices where Vulkan performance might be inconsistent. Source: https://www.qualcomm.com/developer/blog/2024/11/introducing-new-opn-cl-gpu-backend-llama-cpp-for-qualcomm-adreno-gpu

### Implication for deployment
- **Expect variability by GPU family and driver version.** Community reports show both Vulkan and OpenCL can underperform on certain devices. For Adreno devices, Qualcomm’s new OpenCL backend is promising but still device/driver dependent. For Mali, OpenCL performance remains uncertain and may be slower than CPU.
- **Recommendation:** implement backend selection and fallbacks (Vulkan → OpenCL → CPU) at runtime based on device capabilities and empirical benchmarks, and keep a **device-level blocklist** for known regressions.

## 2) CPU compiler flags for ARMv8 (Android)

### Recommended baseline flags from llama.cpp Android docs
The llama.cpp Android cross-compile example explicitly recommends targeting modern ARMv8 with:

```
-DCMAKE_C_FLAGS="-march=armv8.7a"
-DCMAKE_CXX_FLAGS="-march=armv8.7a"
```

This maximizes available ARMv8.7 features on newer devices while still allowing runtime feature checks for older devices. Source: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md

### Runtime feature detection and higher-end ARM extensions
- The Android binding supports **up to SME2** for Arm (and AMX for x86_64) and **auto-detects host CPU features** to select compatible kernels. This means you can compile for a modern target but rely on runtime dispatch to safe kernels on older devices. Source: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- The build documentation highlights that optional Arm-optimized microkernels (KleidiAI) use **dotprod, int8mm, and SME** features with runtime detection. This indicates the codebase is built to benefit from ARMv8.2+ and ARMv9-class features when available. Source: https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md

### Practical takeaway for Android builds
- **Target arm64-v8a** with `-march=armv8.7a` and rely on runtime feature detection to avoid crashes on older devices.
- **Disable OpenMP for NDK builds** per docs (`-DGGML_OPENMP=OFF`) to avoid unsupported dependency issues. Source: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md

## 3) Memory handling and deployment best practices on Android

### Memory pressure and context size
- llama.cpp Android docs recommend **starting with a reasonable context size** (e.g., 4096) because large contexts **spike memory usage** and can kill the process. This is critical on Android where memory pressure is aggressive. Source: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md

### Vulkan can increase RAM usage
- A llama.cpp issue reports **significantly higher RAM usage with Vulkan** compared to CPU-only on Android/Termux, indicating GPU backends can increase memory footprint and should be used with caution on limited-RAM devices. Source: https://github.com/ggerganov/llama.cpp/issues/7351

### Understand model memory footprint
- The project has community discussions documenting how **model memory usage scales** with quantization and context size, reinforcing the need to select quantization levels and context sizes appropriate for device RAM. Source: https://github.com/ggerganov/llama.cpp/discussions/1876

### Deployment steps that avoid Android pitfalls
From official Android docs, these are critical operational steps:
- **Cross-compile with NDK** using `arm64-v8a` and a modern Android platform level (e.g., `android-28`). Source: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- **Set `LD_LIBRARY_PATH`** when running binaries on-device, because Android doesn’t automatically locate your local `lib/` directory. Source: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md

### Practical memory recommendations
- Favor **quantized models** (Q4/Q5/Q6) for constrained RAM devices.
- Keep **context size (n_ctx)** conservative on mid-range devices; measure RAM with `dumpsys meminfo` or Perfetto if needed. (General Android profiling guidance: https://android.googlesource.com/platform/external/perfetto/+/refs/heads/master/docs/case-studies/memory.md)
- On GPU backends, test **RAM usage** separately per backend and device, and adjust `n-gpu-layers` or backend selection to avoid OOM.

## Summary Recommendations (Actionable)
1. **Backend strategy:** Use Vulkan when stable and performant; use OpenCL on Adreno devices where Qualcomm’s backend is strong; fallback to CPU on devices where GPU backends regress. Maintain a **device/driver blocklist**.
2. **CPU flags:** Compile with `-march=armv8.7a` and rely on runtime dispatch; disable OpenMP for NDK builds.
3. **Memory discipline:** Start with `n_ctx` ≈ 4096, use quantized models, and benchmark memory per backend. Expect Vulkan to consume more RAM on some Android devices.

## References
- Vulkan backend discussion (Android performance): https://github.com/ggerganov/llama.cpp/discussions/9464
- OpenCL slower than CPU on Adreno/Mali: https://github.com/ggerganov/llama.cpp/issues/5965
- Qualcomm OpenCL backend for Adreno: https://www.qualcomm.com/developer/blog/2024/11/introducing-new-opn-cl-gpu-backend-llama-cpp-for-qualcomm-adreno-gpu
- llama.cpp Android build docs: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- llama.cpp build docs (KleidiAI/ARM features): https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
- Vulkan RAM usage issue: https://github.com/ggerganov/llama.cpp/issues/7351
- llama.cpp memory usage discussion: https://github.com/ggerganov/llama.cpp/discussions/1876
- Android memory profiling guidance (Perfetto): https://android.googlesource.com/platform/external/perfetto/+/refs/heads/master/docs/case-studies/memory.md
