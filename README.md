# Android LLM Note App (Vulkan Accelerated)

A high-performance Android note-taking application featuring on-device Large Language Model (LLM) integration via `llama.cpp` and Vulkan GPU acceleration.

## 🚀 Key Features
- **On-Device Inference**: Privacy-first AI chat that runs entirely on your device.
- **GPU Acceleration**: High-speed responses using the Vulkan API.
- **RAG (Retrieval-Augmented Generation)**: Chat with your own notes using local vector search.
- **Material 3 Design**: Modern, fluid UI with light/dark mode support.

---

## 🛠 Prerequisites

### Windows Host
- **Android Studio**: For UI development and emulator management.
- **Android Emulator**: x86_64 AVD (API 36 recommended) with Graphics set to "Hardware - GLES 2.0" or "Automatic".
- **WSL2 (Ubuntu 22.04)**: For the native C++ build environment.

### WSL Environment
- **Android SDK & NDK**: Linux versions must be installed in `~/android-sdk`.
- **NDK Version**: `26.1.10909125` (specifically required for Vulkan compatibility).

---

## 🏗 Setup & Building

The project uses a hybrid build system where the Android app is managed by Gradle, but the heavy lifting of shader generation and C++ cross-compilation is orchestrated in WSL.

### 1. Initial WSL Setup
Run the setup script inside your WSL terminal to configure environment variables and install the required NDK:
```bash
chmod +x setup_android_sdk.sh
./setup_android_sdk.sh
```

### 2. Taking a Build
To build the application with full Vulkan support, run the two-step build script in WSL:
```bash
chmod +x build_vulkan.sh
./build_vulkan.sh
```
*This script builds the host shader compiler first, then generates the SPIR-V headers, and finally compiles the APK.*

### 3. Deployment
Once the build completes in WSL, the APK will be located at `app/build/outputs/apk/debug/app-debug.apk`. 
Deploy it to your emulator via Windows CMD/PowerShell:
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 💻 Development Workflow

To maintain high performance and compatibility, follow this sync-and-build workflow:

1.  **Edit Code**: Use Android Studio on **Windows** to modify Kotlin, Compose UI, or C++ files.
2.  **Sync to WSL**: Since the build runs in WSL, synchronize your changes:
    ```bash
    # Example sync command inside WSL
    cp -r /mnt/c/Users/<user>/Documents/projects/android_note_app/app/src ~/projects/android_note_app/app/
    ```
3.  **Rebuild**: Run `./build_vulkan.sh` in WSL.
4.  **Verify**: Check the **Green Dot** indicator in the Chat header to ensure the GPU is active.

---

## 📂 Documentation
Detailed technical papers and setup guides are located in the `docs/` folder:
- [Technical Implementation](docs/wsl/technical_implementation.md): Deep dive into Vulkan patches and JNI logic.
- [Environment Snapshot](docs/wsl/environment_setup.md): Exact system state for reproducibility.
- [Setup Guide](docs/wsl/setup_guide.md): Step-by-step environment configuration.

---

## ⚖ Troubleshooting
- **Crash on "Load Model"**: Ensure you have uninstalled the previous version of the app to clear stale native library caches.
- **No GPU Acceleration**: Check `adb logcat | grep LLAMA_CPP`. If you see "ErrorExtensionNotPresent", the emulator is falling back to CPU.
- **Compilation Errors**: Ensure you are using NDK `26.1.10909125`. Newer or older versions may have conflicting Vulkan headers.
