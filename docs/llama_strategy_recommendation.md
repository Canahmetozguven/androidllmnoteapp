# llama.cpp Android Optimization Strategy (2026 Edition)

## 1. Executive Summary
As of **January 2026**, the hardware landscape for Android LLM inference is dominated by the **Snapdragon 8 Elite** and **ARMv9** architectures. The strategy has shifted from "generic GPU acceleration" to "vendor-specific micro-kernels." 

- **Snapdragon (Adreno)**: The dedicated **Qualcomm OpenCL Backend** is the gold standard, outperforming Vulkan in stability and prompt processing speed.
- **Mali/Others**: **Vulkan** remains the primary path, but with strict memory guards.
- **CPU**: **ARM KleidiAI** micro-kernels are mandatory for high-performance fallbacks, leveraging `i8mm` and `dotprod` on modern cores.

## 2. Definitive Backend Selection (Zero-Risk Logic)

### A. Adreno (Snapdragon 8 Gen 1/2/3/Elite)
*   **Primary**: **Qualcomm OpenCL (`GGML_OPENCL`)**.
    *   *Evidence*: Qualcomm's official 2025 optimizations for `llama.cpp` provide up to 100+ t/s (pp512) on flagship chips. Vulkan on Adreno is currently flagged for "gibberish" output and driver hangs in Android 15/16.
    *   *Implementation*: Prioritize `GGML_OPENCL` if `ro.hardware.egl` contains "adreno".
*   **Secondary**: CPU with **KleidiAI**.

### B. Mali / PowerVR / Generic
*   **Primary**: **Vulkan (`GGML_VULKAN`)**.
    *   *Constraint*: Must use `GGML_VK_DISABLE_F16` on mid-range chips to prevent OOM.
*   **Fallback**: CPU with **KleidiAI**.

## 3. 2026 CPU Optimization (The "KleidiAI" Mandate)
For devices in 2026 (Pixel 9/10, S25/S26), CPU inference is no longer "slow" if optimized.
*   **Mandatory Flags**:
    *   `-DGGML_CPU_KLEIDIAI=ON`: Enables ARM's high-performance micro-kernels.
    *   `-march=armv9-a+i8mm+dotprod`: Targets the `smmla` instructions for 20% faster INT8 matrix math.
*   **Runtime Dispatch**: `llama.cpp` handles the feature detection, but the build *must* include these headers via the NDK 26+ toolchain.

## 4. Stability & Memory Guards (Android 15/16)
*   **The "Vulkan RAM Spike"**: Vulkan backends can spike RAM by **+1GB** compared to CPU/OpenCL. 
    *   *Mitigation*: If `System.Memory < 10GB` AND `Backend == Vulkan`, force `n_ctx = 2048`.
*   **Driver Blacklist**: Maintain an active list for Snapdragon 8 Gen 1/2 specifically for Vulkan, as driver regressions are common. OpenCL is the "Safe Mode" for these devices.

## 5. Implementation Roadmap
1.  **Refactor `native-lib.cpp`**: Use `__system_property_get` for zero-risk vendor detection.
2.  **Update `CMakeLists.txt`**: Add `GGML_CPU_KLEIDIAI` and ARMv9 targeting.
3.  **JNI Backend Switch**:
    - `isAdreno() -> OpenCL`
    - `isMali() -> Vulkan`
    - `isFlagship() -> Enable Advanced Kernels`
4.  **Verification**: Confirm "Green Dot" status in UI only when the optimized vendor path is active.

## 6. Research Evidence Summary
- **Qualcomm Dev Blog (2024/2025)**: Confirms OpenCL is the primary optimization path for Adreno mobile GPUs.
- **ARM Community (2025/2026)**: Confirms KleidiAI as the standard for ARMv9 LLM acceleration.
- **LocalLLaMA Community**: Benchmarks consistently show Adreno OpenCL stability over Vulkan on Snapdragon 8 Gen 2/3.
