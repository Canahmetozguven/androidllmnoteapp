# Build and Setup Scripts for WSL

This directory contains the essential shell scripts used to initialize the WSL environment and perform the Vulkan-accelerated Android build.

## 1. `setup_android_sdk.sh`
**Purpose**: One-time initialization of the WSL environment.
- Configures environment variables (`ANDROID_HOME`, `PATH`) in `~/.bashrc`.
- Automatically accepts Android SDK licenses.
- Installs necessary SDK components: Platform Tools, Android 34 Platform, Build Tools 34.0.0, and **NDK 26.1.10909125**.

**Usage**:
```bash
chmod +x setup_android_sdk.sh
./setup_android_sdk.sh
```

## 2. `build_vulkan.sh`
**Purpose**: The main orchestration script for building the application.
- **Stage 1 (Host)**: Builds `vulkan-shaders-gen` for the Linux host. This tool is required by `llama.cpp` to compile Vulkan shaders into C++ headers.
- **Stage 2 (Android)**: Invokes Gradle to compile the Android APK. It ensures the host shader generator is in the `PATH` so CMake can find it during the cross-compilation process.
- **Cleanup**: Automatically clears the CMake cache (`app/.cxx`) before building to ensure configuration changes (like Vulkan flags) are picked up.

**Usage**:
```bash
chmod +x build_vulkan.sh
./build_vulkan.sh
```

---

## Important Note on Execution
Always run these scripts from within the WSL terminal. Ensure your project is synchronized to the WSL filesystem (`~/projects/android_note_app`) before running the build script.
