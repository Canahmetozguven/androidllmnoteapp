# Android LLM Note App (Optimized)

A high-performance Android note-taking application featuring on-device Large Language Model (LLM) integration via `llama.cpp` with optimized Vulkan and OpenCL GPU acceleration.

## 🚀 Key Features
- **On-Device Inference**: Privacy-first AI chat that runs entirely on your device.
- **Dual-Backend GPU Acceleration**: 
    - **OpenCL**: Optimized for Qualcomm **Adreno** GPUs.
    - **Vulkan**: Primary backend for **Mali** and **Xclipse** GPUs.
- **RAG (Retrieval-Augmented Generation)**: Chat with your own notes using local vector search.
- **Material 3 Design**: Modern, fluid UI with light/dark mode support.

---

## 🛠 Prerequisites

### Windows Host
- **Android Studio**: For UI development and emulator management.
- **Android Emulator**: x86_64 AVD (API 36 recommended).
- **WSL2 (Ubuntu 22.04)**: For the native C++ build environment.

### WSL Environment
- **Android SDK & NDK**: Linux versions must be installed in `~/android-sdk`.
- **NDK Version**: `26.1.10909125`.

---

## 🏗 Setup & Building

### 1. Initial WSL Setup
Run the setup script inside WSL:
```bash
./scripts/setup/setup_android_sdk.sh
```

### 2. Taking a Build
Depending on your target device, choose the appropriate build mode:

- **Ultimate CPU Build (Recommended for Flagships)**: 
  Optimized for Snapdragon 8 Elite, Gen 3, and Dimensity 9400. Uses I8MM instructions to outperform the GPU.
  ```bash
  ./build.sh cpu_ultimate
  ```
  *See [ULTIMATE_CPU_GUIDELINES.md](./docs/strategy/ULTIMATE_CPU_GUIDELINES.md) for details.*

- **Standard GPU Build**:
  Enables Vulkan and OpenCL for broad compatibility.
  ```bash
  ./build.sh static
  ```

*Use `dynamic` mode for testing Backend DL (dlopen) capabilities.*

### 3. Deployment
APK will be located at `app/build/outputs/apk/release/app-release.apk`. 
Deploy via Windows:
```powershell
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 📂 Documentation Structure

The project documentation is organized as follows:

### 📖 [Guides](./docs/guides/)
- [START_HERE.md](./docs/guides/START_HERE.md): Quick start for new developers.
- [VULKAN_WSL_GUIDE.md](./docs/guides/VULKAN_WSL_GUIDE.md): Deep dive into WSL setup.
- [OPENCL_CMAKE_GUIDE.md](./docs/guides/OPENCL_CMAKE_GUIDE.md): OpenCL build configurations.

### 🏗️ [Architecture](./docs/architecture/)
- [IMPLEMENTATION_SUMMARY.md](./docs/architecture/IMPLEMENTATION_SUMMARY.md): Overview of the system architecture.
- [cpp_kotlin_jni_setup.md](./docs/architecture/cpp_kotlin_jni_setup.md): How Kotlin calls into C++.
- [GGML_OPENCL_ANALYSIS.md](./docs/architecture/GGML_OPENCL_ANALYSIS.md): OpenCL backend implementation details.

### 🎯 [Strategy](./docs/strategy/)
- [llama_strategy_recommendation.md](./docs/strategy/llama_strategy_recommendation.md): GPU backend selection logic.
- [VULKAN_VS_OPENCL_PERFORMANCE.md](./docs/architecture/VULKAN_VS_OPENCL_PERFORMANCE.md): Benchmark analysis.

### ✅ [Tasks](./docs/tasks/)
- [TODO.md](./docs/tasks/TODO.md): Immediate action items.
- [port_plan.md](./docs/tasks/port_plan.md): The long-term roadmap.

---

## 💻 Development Workflow

1.  **Edit Code**: Use Android Studio on **Windows**.
2.  **Sync to WSL**: Changes are automatically synced by the build script if paths are configured.
3.  **Rebuild**: Run `./scripts/build/build_full.sh` in WSL.
4.  **Verify**: Check the Hardware Dashboard in the app to see which backend is active.