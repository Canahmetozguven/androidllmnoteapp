# Project Improvement TODOs

## Build & Configuration
- [ ] **Fix SDK Mismatch**: Update `setup_android_sdk.sh` to use Android SDK 35 (currently installs 34) to match `build.gradle.kts`.
- [ ] **Portability**: Remove hardcoded `/usr/lib/jvm/java-17-openjdk-amd64` from `release.sh` and `build_vulkan.sh`. Respect user's `$JAVA_HOME`.
- [ ] **Refactor**: Unify `release.sh` and `build_vulkan.sh` to reduce code duplication.
- [ ] **Config**: Centralize NDK version definition (currently scattered in gradle and shell scripts).
- [ ] **Tooling**: Add `detekt` or `ktlint` for static code analysis.

## CI/CD
- [ ] **Critical**: Create `.github/workflows/android-ci.yml` to run `./gradlew assembleDebug` and `./gradlew test` on every PR.
- [ ] **Enhancement**: Add Gradle caching to speed up CI.
- [ ] **Lint**: Add lint check step to CI.

## App Core (Kotlin)
- [ ] **Refactor**: Extract Prompt Construction logic from `ChatViewModel` to `PromptBuilder` to support different templates (ChatML, Alpaca, etc).
- [ ] **Enhancement**: Improve context truncation (currently strict char limit) to be smarter (nearest newline).
- [ ] **Refactor**: Extract `NavHost` content from `MainActivity` to a dedicated `AppNavigation` composable.

## Native (C++)
- [ ] **Refactor**: Encapsulate JNI global variables (`g_model`, etc.) into a context object/handle to prevent state issues.
- [ ] **Fix**: Remove hardcoded ChatML fallback in `native-lib.cpp`. All templates should come from the App/Model metadata.
- [ ] **Perf**: Optimize JNI logging callback.

## GPU Acceleration & Backends
- [ ] **Investigation Complete**: OpenCL dynamic loading support verified (see `GGML_OPENCL_ANALYSIS.md`)
- [ ] **Optional Feature**: Add OpenCL backend as fallback to Vulkan for Snapdragon compatibility
  - Option A: Static linking with `-DGGML_OPENCL=ON -DGGML_OPENCL_EMBED_KERNELS=ON`
  - Option B: Dynamic loading with `-DGGML_BACKEND_DL=ON` (allows graceful fallback)
  - Option C: Adreno-optimized kernels with `-DGGML_OPENCL_USE_ADRENO_KERNELS=ON`
- [ ] **Implementation**: Create `build_opencl.sh` script to test OpenCL variants without modifying main build
- [ ] **Testing**: Add backend detection/logging to verify which GPU acceleration is active
- [ ] **Performance**: Document Vulkan vs OpenCL benchmarks on Snapdragon devices
- [ ] **Reference Docs**: Created at project root:
  - `GGML_OPENCL_ANALYSIS.md` - Technical deep dive
  - `GGML_OPENCL_REFERENCE.md` - Implementation guide
  - `OPENCL_QUICK_REFERENCE.txt` - Terminal-friendly lookup
  - `OPENCL_ANALYSIS_INDEX.md` - Navigation guide

## Resources
- [ ] **Critical**: Modify `backup_rules.xml` to EXCLUDE the model directory (e.g., `files/models`) to prevent backing up GBs of data to Google Cloud.
- [ ] **I18n**: Extract hardcoded UI strings from Composables into `strings.xml` to support translation.
