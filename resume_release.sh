#!/bin/bash
# Resume Release script - Skips Clean/Rebuild
set -e

# Environment setup
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

PROJECT_DIR="$(pwd)"
LLAMA_DIR="$PROJECT_DIR/app/src/main/cpp/llama"

echo "=== Step 1: Ensure vulkan-shaders-gen exists ==="
cd "$LLAMA_DIR"
EXISTING_GEN=$(find build-host -name 'vulkan-shaders-gen' -type f -executable 2>/dev/null | head -1)
if [ -n "$EXISTING_GEN" ]; then
    echo "Found existing vulkan-shaders-gen at: $EXISTING_GEN"
    SHADER_GEN_DIR=$(dirname "$(realpath "$EXISTING_GEN")")
else
    # Fallback if missing, though user said native built...
    echo "Building vulkan-shaders-gen..."
    mkdir -p build-host && cd build-host
    cmake .. -DGGML_VULKAN=ON -DCMAKE_BUILD_TYPE=Release
    make vulkan-shaders-gen -j$(nproc)
    cd "$LLAMA_DIR"
    SHADER_GEN=$(find build-host -name 'vulkan-shaders-gen' -type f -executable | head -1)
    SHADER_GEN_DIR=$(dirname "$(realpath "$SHADER_GEN")")
fi

export PATH="$SHADER_GEN_DIR:$PATH"
echo "vulkan-shaders-gen directory: $SHADER_GEN_DIR"

echo ""
echo "=== Step 2: Resume Android Release Build (No Clean) ==="
cd "$PROJECT_DIR"
# SKIPPED: rm -rf app/.cxx

# Run Gradle WITHOUT clean
./gradlew :app:assembleRelease :app:bundleRelease -PuseVulkan=true -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64

echo ""
echo "=== Release Build Complete ==="
echo "APK: $PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
echo "AAB: $PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab"

# Copy to release_artifacts
echo "Copying artifacts..."
mkdir -p release_artifacts
cp app/build/outputs/apk/release/app-release.apk release_artifacts/
cp app/build/outputs/bundle/release/app-release.aab release_artifacts/

echo "Artifacts available in ./release_artifacts/"
