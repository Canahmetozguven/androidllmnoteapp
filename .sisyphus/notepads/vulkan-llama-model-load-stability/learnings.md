# Learnings - Vulkan Llama Stability

## Architecture
- **Hybrid Build**: Gradle (UI) + WSL (Native C++/Vulkan).
- **Backend Chain**: Preferred -> Vulkan -> OpenCL -> CPU.
- **Hardware Detection**: Native `detect_gpu_vendor` + Kotlin `HardwareCapabilityProvider`.
- **Safety**:
    - **Safe Mode**: Prevents boot loops after crashes.
    - **Attempt Flags**: `SharedPreferences` tracks active attempts to detect native crashes.
    - **Blocklists**: SoC-based blocklists in both Kotlin and C++.

## Conventions
- **Logs**: Native tags `LLM_JNI`, `LLAMA_CPP`. Kotlin tags `LlmEngine`, `HardwareCapability`.
- **Mmap**: Disabled for unstable drivers (S22/S25, SD 8 Gen 1/2).
- **Batch Size**: 32 for problematic devices, 512 default.

## Known Issues
- **Adreno/Vulkan**: Pipeline creation fails on Adreno 740+.
- **Exynos/Vulkan**: Exynos 2200 unstable with mmap.
- **Tensor (Pixel)**: Likely missing from blocklists (Pixel 7 FE hang).
- **OpenCL**: Only officially supported on Snapdragon 8 Gen 3/Elite.
