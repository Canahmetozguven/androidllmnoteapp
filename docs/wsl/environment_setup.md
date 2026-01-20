# Detailed WSL Environment Configuration for Vulkan Android Build

This document captures the exact state of the WSL environment used to build the Android LLM application with Vulkan acceleration. This is intended for technical reproducibility and as a reference for publishing.

## 1. System Specifications
- **Operating System**: Ubuntu 22.04.3 LTS (running on WSL2)
- **Kernel**: Linux DESKTOP-MR1KOEH 6.6.87.2-microsoft-standard-WSL2
- **Architecture**: x86_64

## 2. Android SDK & NDK (WSL Side)
The Linux-specific versions of the Android SDK and NDK are installed in the home directory to provide the native cross-compilation toolchain.

- **SDK Root**: `/home/canahmet/android-sdk`
- **Primary NDK**: `/home/canahmet/android-sdk/ndk/26.1.10909125`
- **Secondary NDK**: `/home/canahmet/android-sdk/ndk/25.1.8937393`
- **Shader Tools**: `glslc` is sourced from the NDK's `shader-tools` directory.

## 3. Toolchain Versions
- **CMake**: 3.22.1 (standard for Android Gradle builds)
- **GCC/G++**: 11.4.0 (for host tools like `vulkan-shaders-gen`)
- **Ninja**: 1.10.1 (used as the generator for native builds)

## 4. Project Directory Structure & Sync
The project is maintained in two locations to leverage both the Windows GUI (Android Studio/Emulator) and Linux native build performance.

- **Windows Path**: `C:\Users\canahmet\Documents\projects\android_note_app`
- **WSL Path**: `/home/canahmet/projects/android_note_app`

### Synchronization Mechanism
The source code is synchronized from Windows to WSL before every build to ensure that the Linux environment has the latest code changes while maintaining proper Linux file permissions.

## 5. Build Orchestration (`build_vulkan.sh`)
The build is executed in a two-stage pipeline:

1.  **Host Stage**: Compiles the `vulkan-shaders-gen` binary for the Linux host (`x86_64`).
2.  **Android Stage**:
    - Exports the host tool binary to the `PATH`.
    - Invokes `./gradlew :app:assembleDebug`.
    - CMake within the Gradle build detects the `vulkan-shaders-gen` tool and uses it to compile the Vulkan compute shaders into the final Android JNI library.

## 6. Vulkan-Specific Patches applied in WSL
- **Header Injection**: Official Khronos headers are cloned into `app/src/main/cpp/external` inside WSL to bypass NDK limitations.
- **Dynamic Dispatch**: `ggml-vulkan.cpp` was patched to use the C++ dynamic dispatcher, which is critical for supporting the Vulkan loader on older Android versions (API 26).
- **Extension Filtering**: Logic added to check physical device capabilities before device creation to avoid the `ErrorExtensionNotPresent` crash on emulators.
