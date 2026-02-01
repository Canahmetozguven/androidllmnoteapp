# C++ ↔ Kotlin JNI Setup (Android LLM Engine)

## Scope
This document summarizes how Kotlin calls into native C++ (JNI) for llama.cpp inference, where the glue lives, and how native builds are produced in this repo.

## Key Files
**Kotlin (JNI wrapper + orchestration)**
- `app/src/main/java/com/synapsenotes/ai/core/ai/LlamaContext.kt`
  - Loads the native library (`System.loadLibrary("llm_notes_cpp")`).
  - Declares `external` JNI methods (loadModelNative, completion, embed, etc.).
- `app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt`
  - Interface + default implementation (`DefaultLlmContext`) that calls `LlamaContext`.
  - Provides safe fallbacks if native lib fails to load.
- `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt`
  - Owns model lifecycle and backend selection.
  - Uses a `Mutex` to serialize native calls.

**Native C++ (JNI entry + inference)**
- `app/src/main/cpp/native-lib.cpp`
  - `JNI_OnLoad` registers native methods via `RegisterNatives`.
  - Implements `loadModelNative`, `loadEmbeddingModelNative`, `completion`, etc.
  - Contains backend selection helpers and Vulkan/OpenCL safety flags.
- `app/src/main/cpp/LlmSession.h`
  - Holds global session state (`model`, `context`, embedding model/context, flags).

**Native Build**
- `app/src/main/cpp/CMakeLists.txt`
  - Configures Vulkan/OpenCL, includes vendored llama.cpp, builds `llm_notes_cpp`.
- `build_vulkan.sh`
  - WSL-only build script for host shader tools + Android release build.

## JNI Registration & Signatures
Native methods are registered explicitly in `JNI_OnLoad` (in `native-lib.cpp`) to avoid `UnsatisfiedLinkError` and to work with obfuscation:

```cpp
jclass clazz = env->FindClass("com/synapsenotes/ai/core/ai/LlamaContext");
env->RegisterNatives(clazz, methods, ...);
```

The Kotlin signatures in `LlamaContext.kt` match the C++ signatures exactly (examples):
- `loadModelNative(String, String?, Int, Int, Boolean, Int): Boolean`
- `loadEmbeddingModelNative(String, Int, Int, Boolean, Int): Boolean`
- `completion(String, String, Array<String>, LlmCallback): String`

## Kotlin → Native Call Flow
1. **App code** calls `LlmEngine.loadModel(...)` or `completion(...)`.
2. `LlmEngine` acquires a **Mutex** to keep native calls single‑threaded.
3. `LlmEngine` delegates to `LlmContext`.
4. `DefaultLlmContext` calls JNI methods on `LlamaContext`.
5. JNI methods route to C++ via `RegisterNatives`.

## Build Flow (WSL Only)
Native builds are **not supported via Android Studio Gradle** (per repo conventions). Use WSL:

1. `./build_vulkan.sh`
   - Step 1: Build `vulkan-shaders-gen` (host)
   - Step 2: `./gradlew :app:assembleRelease :app:bundleRelease -PuseVulkan=true`

Outputs:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`

## Common Failure Points
1. **JNI registration / obfuscation**
   - JNI uses `RegisterNatives` and exact class names. R8 can break this if JNI classes are obfuscated.

2. **Native library not loaded**
   - `LlamaContext` sets `isLibraryLoaded` and short‑circuits in `DefaultLlmContext`.

3. **GPU backend instability**
   - Vulkan/OpenCL can fail on certain phone GPUs. Backend selection tries multiple options and can mark failures.

4. **WSL build requirement**
   - Native build requires shader generator and NDK tooling in WSL; Gradle alone won’t build correctly.

## Do / Don’t (Repo Conventions)
**Do**
- Use `RegisterNatives` in `JNI_OnLoad` for all JNI entry points.
- Use `LlmEngine` for all native calls (ensures single‑threaded access).
- Build native artifacts via `build_vulkan.sh` (WSL).

**Don’t**
- Don’t bypass `LlmEngine` to call JNI directly from UI threads.
- Don’t rely on implicit `Java_pkg_Class_method` JNI names.
- Don’t build native via Android Studio Gradle alone.

## Related Docs
- `docs/android_tasks/09-llama-jni-setup.md`
- `docs/review/04_native_cpp.md`
