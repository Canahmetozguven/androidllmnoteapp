#!/bin/bash
# Build script for Android with Vulkan - Two-step process
# This script builds the host shader generator first, then the Android APK
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

# Only rebuild if needed (check if binary exists and is executable)
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
    
    # Find the built shader generator
    SHADER_GEN=$(find build-host -name 'vulkan-shaders-gen' -type f -executable | head -1)
    if [ -z "$SHADER_GEN" ]; then
        echo "ERROR: vulkan-shaders-gen not found after build!"
        exit 1
    fi
    SHADER_GEN_DIR=$(dirname "$(realpath "$SHADER_GEN")")
fi

# Add to PATH for the Android build
export PATH="$SHADER_GEN_DIR:$PATH"
echo "vulkan-shaders-gen directory: $SHADER_GEN_DIR"
which vulkan-shaders-gen

echo ""
echo "=== Step 2: Build Android APK ==="
cd "$PROJECT_DIR"
rm -rf app/.cxx  # Clear CMake cache to pick up new config

./gradlew :app:assembleDebug -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64

echo ""
echo "=== Build Complete ==="
echo "APK: $PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
