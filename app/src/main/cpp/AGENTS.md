# NATIVE LAYER KNOWLEDGE BASE

## OVERVIEW
High-performance C++ bridge managing Vulkan-accelerated LLM inference via JNI and llama.cpp.

## STRUCTURE
```
.
├── native-lib.cpp       # Primary JNI entry point & inference loop
├── CMakeLists.txt       # Native build config (Vulkan forced ON)
├── host-toolchain.cmake # Cross-compilation toolchain for WSL
├── external/            # Vulkan headers (Khronos)
└── llama/               # Vendored llama.cpp core (DO NOT TOUCH)
```

## WHERE TO LOOK
| Component | Location | Details |
|-----------|----------|---------|
| **JNI Registration** | `native-lib.cpp` -> `JNI_OnLoad` | Explicit method mapping to Java classes |
| **Inference Loop** | `native-lib.cpp` -> `completion` | Token generation, batching, and callbacks |
| **Vulkan Config** | `CMakeLists.txt` | `GGML_VULKAN` flags and shader compiler paths |
| **Memory Cleanup** | `native-lib.cpp` -> `unload` | Manual pointer freeing (`llama_free`) |

## CONVENTIONS
- **JNI Registration**: ALWAYS use `RegisterNatives` in `JNI_OnLoad`. Never rely on implicit `Java_pkg_Cls_method` naming exports alone.
- **Memory Management**: 
  - Manually free `llama_model`, `llama_context`, and `llama_batch`.
  - Use `std::vector` for buffers; avoid large stack allocations.
- **Concurrency**: 
  - Global state (`g_model`, `g_context`) is shared; ensure single-threaded access or add mutexes if expanding.
  - Respect `g_stop_requested` atomic flag for user cancellation.
- **Logging**: Use `__android_log_print` with tag `LLM_JNI`.

## TESTING STRATEGY
- **Kotlin Wrapper**: Test `LlmContext` (Kotlin) by mocking the JNI calls if possible, or usually just test `LlmEngine` (Core layer) and mock `LlmContext`.
- **Native Logic**: Unit tests for C++ are in `llama.cpp` submodule. We assume `llama.cpp` is correct.
- **Integration**: `VulkanBuildTest` (Instrumented) verifies the shared library loads on device.

## ANTI-PATTERNS
- **Gradle Builds**: NEVER attempt to build this layer purely from Android Studio. It requires the WSL environment (see root `build_vulkan.sh`).
- **Implicit Linking**: Do not skip `RegisterNatives`; it prevents `UnsatisfiedLinkError` during refactors/obfuscation.
- **Main Thread**: Never call `completion` or `loadModelNative` from the Android UI thread.
- **Hardcoded Paths**: Do not hardcode model paths; pass them from Kotlin via JNI.
