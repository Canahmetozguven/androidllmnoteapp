# 01 - Build & Configuration Review

## Overview
This section covers the Gradle build scripts (`build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`), Version Catalog (`libs.versions.toml`), and shell automation scripts (`release.sh`, `build_vulkan.sh`, `setup_android_sdk.sh`).

## Strengths
*   **Modern Modern Gradle**: The project uses Kotlin DSL (`.kts`) and Version Catalogs (`libs.versions.toml`), which is the current best practice for dependency management.
*   **Security**: Sensitive keys (API keys, Signing configs) are loaded from `local.properties` and `keystore.properties` rather than being hardcoded.
*   **Reproducibility**: Shell scripts (`release.sh`) are provided to standardize the build process, including native shader compilation.
*   **Native Integration**: Explicit handling of CMake arguments for Vulkan and OpenCL support in `app/build.gradle.kts`.
*   **Versioning**: Clever auto-versioning logic implementation in `app/build.gradle.kts` based on timestamps.

## Areas for Improvement

### 1. SDK & Toolchain Mismatches
*   **Issue**: `app/build.gradle.kts` specifies `compileSdk = 35` and `targetSdk = 35`. However, `setup_android_sdk.sh` installs `platforms;android-34` and `build-tools;34.0.0`.
*   **Impact**: Build might fail in a fresh environment created by the setup script due to missing SDK 35.
*   **Recommendation**: Update `setup_android_sdk.sh` to match the Gradle configuration (SDK 35).

### 2. Hardcoded Paths in Shell Scripts
*   **Issue**: `release.sh` and `build_vulkan.sh` enforce a specific Java home path: `-Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64`.
*   **Impact**: This breaks the script on systems where Java is installed elsewhere (e.g., macOS, non-Debian Linux, or Windows without this exact path).
*   **Recommendation**: Allow the environment to accept an existing `JAVA_HOME` or dynamically detect it, falling back to a default only if needed.

### 3. Script Redundancy
*   **Issue**: `release.sh` and `build_vulkan.sh` share ~90% of their logic (building shaders, then building the APK). 
*   **Impact**: Maintenance burden. Changes to shader compilation logic must be replicated in both.
*   **Recommendation**: Unify into a single `build.sh` with flags (e.g., `./build.sh --release --vulkan`).

### 4. Hardcoded NDK Versions
*   **Issue**: NDK version `26.1.10909125` is hardcoded in both `app/build.gradle.kts` and `setup_android_sdk.sh`.
*   **Impact**: Updating the NDK require changes in multiple places.
*   **Recommendation**: Define NDK version in `libs.versions.toml` or `gradle.properties` and reference it in both Gradle and the setup script (via parsing or shared config).

### 5. Missing Static Analysis
*   **Issue**: There are no configured plugins for code quality/linting (e.g., Ktlint, Detekt) in `build.gradle.kts`.
*   **Recommendation**: Add `detekt` or `ktlint` to the build pipeline to enforce style and catch common errors automatically.

## Action Plan
- [ ] **Fix**: Update `setup_android_sdk.sh` to install Android 35 components.
- [ ] **Refactor**: Merge `release.sh` and `build_vulkan.sh` into a unified build script or extract common logic.
- [ ] **Fix**: Remove hardcoded `JAVA_HOME` from shell scripts; use `$JAVA_HOME` environment variable.
- [ ] **Enhance**: Add logic to read NDK version from a single source of truth.
