# PROJECT KNOWLEDGE BASE

**Generated:** 2026-01-23
**Context:** Hybrid Android + Vulkan + WSL Build System

## OVERVIEW
Android application integrating `llama.cpp` for on-device LLM inference using Vulkan GPU acceleration.
**Stack:** Kotlin (MVVM/Compose) + C++ (JNI/Vulkan) + WSL (Build Host).

## STRUCTURE
```
.
├── app/
│   ├── src/main/java/.../ai/  # Android Code (Kotlin)
│   └── src/main/cpp/          # Native Code (C++/Vulkan)
│       └── llama/             # Vendored llama.cpp (Sub-project)
├── docs/                      # Technical docs & task tracking
├── build_vulkan.sh            # WSL-side build orchestrator
└── setup_android_sdk.sh       # WSL environment setup
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| **UI/Feature Logic** | `app/src/main/java/.../feature` | Jetpack Compose screens |
| **Native Bridge** | `app/src/main/cpp/native-lib.cpp` | JNI `RegisterNatives` entry point |
| **LLM Engine** | `app/src/main/java/.../core/ai` | Kotlin wrapper for JNI calls |
| **Build Config** | `app/src/main/cpp/CMakeLists.txt` | Native build & Vulkan flags |
| **Build Scripts** | `./build_vulkan.sh` | **MUST RUN IN WSL** |

## CRITICAL WORKFLOWS
### 1. Hybrid Build (Windows + WSL)
- **Edit**: Windows (Android Studio).
- **Sync**: `wsl bash -c "cp -r ..."` (Windows → WSL).
- **Build**: `wsl bash -c "./build_vulkan.sh"`.
- **Deploy**: `adb install -r ...` (Windows).

### 2. Native Safety
- **JNI**: Use `RegisterNatives` in `JNI_OnLoad`. NEVER use `Java_pkg_name` conventions.
- **Memory**: Explicitly call `llama_free` / `llama_model_free`.
- **Sync**: Verify file presence in WSL before building to avoid `UnsatisfiedLinkError`.

## CONVENTIONS
- **Kotlin**: Clean Architecture (Core/Domain/Data/Feature). Hilt DI.
- **C++**: Manual JNI registration. Explicit memory management.
- **Git**: NO `.gguf` files. NO secrets. NO force push.

## ANTI-PATTERNS (STRICT)
- **DO NOT** build native code via Android Studio Gradle (it lacks WSL context).
- **DO NOT** commit `.gguf` models.
- **NEVER** use `Unsafe` or bypass Hilt for ViewModels.
- **NEVER** run inference on Main Thread.

## COMMANDS
```bash
# WSL ONLY
./setup_android_sdk.sh       # First setup
./build_vulkan.sh            # Full native rebuild
./publish.sh                 # Release build

# Windows
./gradlew assembleDebug      # Java-only build (Native will fail if not pre-built)
./gradlew ciTest             # Run ALL tests (requires emulator/device)
./gradlew testDebugUnitTest  # Run JVM Unit Tests (Fast)
```

## TESTING & TDD
This project follows Test-Driven Development (TDD).
- **Unit Tests** (`app/src/test`): Use JUnit 5 + MockK/Mockito. For ViewModels, Repositories, UseCases.
- **Instrumented Tests** (`app/src/androidTest`): Use JUnit 4. For Room DAOs, integration tests requiring Context.

### TDD Workflow
1. Write failing test in `src/test` (Unit) or `src/androidTest` (DB/UI).
2. Implement feature/fix.
3. Verify with `./gradlew ciTest`.

### Conventions
- **Naming**: `ClassNameTest`.
- **Structure**: Arrange, Act, Assert.
- **Coroutines**: Use `CoroutineTestExtension` (JUnit 5) or `runTest`.
- **Mocking**: Prefer `MockK` for Kotlin, `Mockito` where legacy/java interop needed.
