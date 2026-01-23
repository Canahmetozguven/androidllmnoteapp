#!/bin/bash
# Publish script for Android with Vulkan (Release Build)
set -e

# Environment setup
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

PROJECT_DIR="$HOME/projects/android_note_app"
LLAMA_DIR="$PROJECT_DIR/app/src/main/cpp/llama"

echo "=== Step 1: Build vulkan-shaders-gen for Host ==="
cd "$LLAMA_DIR"

# Only rebuild if needed
EXISTING_GEN=$(find build-host -name 'vulkan-shaders-gen' -type f -executable 2>/dev/null | head -1)
if [ -n "$EXISTING_GEN" ]; then
    echo "Found existing vulkan-shaders-gen at: $EXISTING_GEN"
    SHADER_GEN_DIR=$(dirname "$(realpath "$EXISTING_GEN")")
else
    echo "Building vulkan-shaders-gen..."
    rm -rf build-host
    mkdir -p build-host && cd build-host
    cmake .. -DGGML_VULKAN=ON -DCMAKE_BUILD_TYPE=Release
    make vulkan-shaders-gen -j$(nproc)
    cd "$LLAMA_DIR"
    
    SHADER_GEN=$(find build-host -name 'vulkan-shaders-gen' -type f -executable | head -1)
    if [ -z "$SHADER_GEN" ]; then
        echo "ERROR: vulkan-shaders-gen not found after build!"
        exit 1
    fi
    SHADER_GEN_DIR=$(dirname "$(realpath "$SHADER_GEN")")
fi

export PATH="$SHADER_GEN_DIR:$PATH"
echo "vulkan-shaders-gen directory: $SHADER_GEN_DIR"

echo ""
echo "=== Step 2: Build Android Release APK ==="
cd "$PROJECT_DIR"
rm -rf app/.cxx

# Run assembleRelease for APK and bundleRelease for AAB
./gradlew :app:assembleRelease :app:bundleRelease -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64

echo ""
echo "=== Publish Build Complete ==="
echo "Release APK: $PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
echo "Release Bundle (AAB): $PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab"

# Copy artifacts to project root release_artifacts/
mkdir -p "$PROJECT_DIR/release_artifacts"
cp "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" "$PROJECT_DIR/release_artifacts/"
cp "$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab" "$PROJECT_DIR/release_artifacts/"

echo "Artifacts copied to: $PROJECT_DIR/release_artifacts/"
