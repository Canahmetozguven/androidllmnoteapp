# Task 09 - Llama JNI Setup

## Goal
Integrate the native `llama.cpp` library and expose it to Kotlin.

## Reference
- `src/services/DeepSeekInference.ts`
- `src/services/EmbeddingService.ts`

## Steps
1. Add NDK and CMake support in `build.gradle.kts`.
2. Clone `llama.cpp` into `app/src/main/cpp/` or as a submodule.
3. Create `CMakeLists.txt` to compile the native library.
4. Build `libllama.so` for `armeabi-v7a`, `arm64-v8a`, and `x86_64`.
5. Create JNI bridge functions.
6. Create Kotlin wrapper class `LlamaContext`.

## Output
- App builds and loads native library without crashing.
- Kotlin can call basic inference or embedding functions.

## Notes
This is the riskiest part of the port. Expect iteration.
