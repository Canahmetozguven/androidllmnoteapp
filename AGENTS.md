# Agentic Development Guide - Android LLM Note App

This repository contains an Android application integrated with `llama.cpp` for on-device LLM inference using Vulkan acceleration.

## 🛠 Build & Test Commands

### General Android Build
- **Assemble Debug APK**: `./gradlew assembleDebug`
- **Clean Build**: `./gradlew clean assembleDebug`
- **Lint**: `./gradlew lint`

### Vulkan Accelerated Build (WSL Required)
The project uses a hybrid WSL-Windows build process for native C++ components and Vulkan shader generation.
- **Full Vulkan Build**: `wsl bash -c "./build_vulkan.sh"`
- **Setup WSL Environment**: `wsl bash -c "./setup_android_sdk.sh"`

### Testing
- **Run all unit tests**: `./gradlew test`
- **Run a single unit test**: `./gradlew testDebugUnitTest --tests "com.example.llmnotes.core.ai.LlmEngineTest"`
- **Run instrumentation tests**: `./gradlew connectedCheck` (requires a connected device or emulator)

---

## 🎨 Code Style Guidelines

### Kotlin (Android)
- **Architecture**: Follow Clean Architecture with MVVM. Layers: `core`, `domain`, `data`, `feature`, `ui`.
- **Dependency Injection**: Use **Hilt**. Annotate ViewModels with `@HiltViewModel` and entry points with `@AndroidEntryPoint`.
- **UI**: Use **Jetpack Compose**. Prefer stateless composables and state hoisting.
- **Concurrency**: Use **Coroutines**. Use `viewModelScope` in ViewModels and `Dispatchers.IO` for disk/network/native operations.
- **Naming**: 
    - Classes: `PascalCase` (e.g., `NoteRepositoryImpl`).
    - Functions/Variables: `camelCase` (e.g., `loadModel`).
    - Constants: `SCREAMING_SNAKE_CASE` (e.g., `TAG`).
- **Formatting**: Standard Kotlin style (4-space indentation). Group imports by package.

### C++ (Native JNI)
- **Bridge**: Follow strict JNI naming for exported functions: `Java_package_name_ClassName_methodName`.
- **Memory Management**: Explicitly manage `llama_model*` and `llama_context*`. Ensure `llama_free` and `llama_model_free` are called in `unload`.
- **Logging**: Use `#define TAG "TAG_NAME"` and `__android_log_print(LEVEL, TAG, format, ...)`.
- **Error Handling**: Return `jboolean` (JNI_TRUE/JNI_FALSE) for success/failure or `jstring` containing an error message.
- **Formatting**: Consistent with standard C++ (4-space indentation).

---

## 🚀 Development Workflow

1.  **UI/Logic Changes**: Modify Kotlin code in `app/src/main/java`.
2.  **Native Changes**: Modify C++ code in `app/src/main/cpp`.
3.  **Sync to WSL**: Since the build orchestration happens in WSL, ensure source changes are synced:
    `wsl bash -c "cp -r /mnt/c/Users/<user>/.../app/src ~/projects/android_note_app/app/"`
4.  **Rebuild**: Execute `./build_vulkan.sh` in WSL to generate shaders and compile the native library.
5.  **Deploy**: Install the generated APK from `app/build/outputs/apk/debug/app-debug.apk` using `adb install -r`.

---

## 🛡️ Safety & Security
- **Secrets**: Never commit `local.properties` or any API keys.
- **Large Files**: Do not commit `.gguf` model files to Git (they are ignored via `.gitignore`).
- **Native Stability**: Always implement CPU fallbacks in `loadModel` to prevent app crashes on incompatible hardware.

## ⚠️ Important Troubleshooting: Native Build Caching & Sync

**Issue:** Persistent `UnsatisfiedLinkError` or `java.lang.NoSuchMethodError` when calling native functions, even after a rebuild.

**Root Cause:**
1.  **Sync Failure:** The file synchronization command (`cp`) from Windows to WSL might silently fail (e.g., due to permission errors with `.git` directories or locked files), leaving the build environment with outdated source code.
2.  **Aggressive Caching:** CMake and the Android build system (`.cxx`, `build/intermediates`) cache intermediate object files aggressively. If a function signature changes (or is renamed), the linker might link against stale objects, resulting in a binary that lacks the new symbols.

**Prevention & Fixes:**
1.  **Explicit JNI Registration:** Avoid relying on the `Java_package_name_ClassName_methodName` naming convention. Instead, use `RegisterNatives` in `JNI_OnLoad`. This explicitly maps Java methods to C++ functions at runtime, bypassing name-mangling fragility and providing immediate errors if something is wrong.
    ```cpp
    // Example in native-lib.cpp
    JNINativeMethod methods[] = {
        {"nativeMethodName", "()V", (void*)actualCppFunction},
    };
    env->RegisterNatives(clazz, methods, count);
    ```
2.  **Nuclear Clean:** If you suspect a native build issue, do not rely on `./gradlew clean`. You must manually delete the build artifacts in both environments:
    - Windows: `rm -rf app/build app/.cxx`
    - WSL: `rm -rf ~/projects/android_note_app/app/build`
3.  **Verify Sync:** After running a sync command, verify the content of the file in WSL using `grep` or `cat` before building to ensure your changes actually made it across.
