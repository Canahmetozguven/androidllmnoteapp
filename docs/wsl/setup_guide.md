# Environment Setup & Reproducibility Guide

## 1. Prerequisites

### Windows Host
- **Android SDK & NDK**: Installed via Android Studio (default path: `%LOCALAPPDATA%/Android/Sdk`).
- **Android Emulator**: An `x86_64` AVD with API 36 or higher, configured with GPU acceleration.
- **WSL2**: Ubuntu 22.04 or similar.

### WSL Environment
- **Linux Android SDK/NDK**: Download and extract the Linux version of the Android SDK and NDK (v26+ recommended) into `~/android-sdk`.
- **Build Tools**:
  ```bash
  sudo apt update
  sudo apt install build-essential cmake git ninja-build libvulkan-dev
  ```

## 2. Project Directory Structure
The project must exist in both filesystems, but the **WSL version** is the source of truth for builds.
- **Windows**: `C:\Users\...\android_note_app`
- **WSL**: `~/projects/android_note_app`

## 3. The `build_vulkan.sh` Script
This script is the core of the WSL setup. It ensures the environment is correctly mapped before calling Gradle.

```bash
#!/bin/bash
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 1. Build Host Tools
cd app/src/main/cpp/llama
mkdir -p build-host && cd build-host
cmake .. -DGGML_VULKAN=ON -DCMAKE_BUILD_TYPE=Release
make vulkan-shaders-gen -j$(nproc)

# 2. Build Android App
cd ../../../..
export PATH="$(pwd)/app/src/main/cpp/llama/build-host/bin:$PATH"
./gradlew :app:assembleDebug
```

## 4. Vulkan Configuration Details

### CMake Definitions
In `app/src/main/cpp/CMakeLists.txt`, we force the following to ensure Vulkan is correctly linked:
- `GGML_VULKAN=ON`: Core flag for Vulkan code.
- `GGML_BACKEND_DL=OFF`: Disables dynamic loading; backends are statically linked into `libllm_notes_cpp.so`.
- `GGML_VULKAN_SHADERS_GEN_TOOLCHAIN`: Points to a local `host-toolchain.cmake` to handle cross-compilation specific to the shader generator.

### Header Management
To avoid NDK version drift, headers are manually managed:
- **Location**: `app/src/main/cpp/external/Vulkan-Headers`
- **Logic**: CMake is configured to search these directories *before* the NDK sysroot.

## 5. Deployment Pipeline
Automation of the sync and install process:
```powershell
# Sync source to WSL
wsl bash -c "rm -rf ~/projects/android_note_app/app/src && cp -r /mnt/c/path/to/src ~/projects/android_note_app/app/"

# Run Build
wsl bash -c "cd ~/projects/android_note_app && ./build_vulkan.sh"

# Install
adb install -r \\wsl$\Ubuntu-22.04\home\canahmet\projects\android_note_app\app\build\outputs\apk\debug\app-debug.apk
```
