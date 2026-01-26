#!/bin/bash
# Publish script for Android with Vulkan (Release Build)
set -e

# Environment setup
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

PROJECT_DIR="$(pwd)"
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
    
    # Use Android SDK CMake if system cmake is missing
    CMAKE_BIN="cmake"
    if ! command -v cmake &> /dev/null; then
        SDK_CMAKE="/c/Users/canahmet/AppData/Local/Android/Sdk/cmake/3.22.1/bin/cmake.exe"
        if [ -f "$SDK_CMAKE" ]; then
            CMAKE_BIN="$SDK_CMAKE"
        fi
    fi
    
    # Use Ninja if make is missing (Android SDK usually has ninja)
    GENERATOR=""
    BUILD_TOOL="make"
    if ! command -v make &> /dev/null; then
         GENERATOR="-GNinja"
         BUILD_TOOL="ninja"
         MAKE_CMD="ninja"
         if ! command -v ninja &> /dev/null; then
             # Try finding ninja in cmake bin dir or common SDK paths
             if [ -f "$(dirname "$CMAKE_BIN")/ninja.exe" ]; then
                 MAKE_CMD="$(dirname "$CMAKE_BIN")/ninja.exe"
                 BUILD_TOOL="$MAKE_CMD"
             elif [ -f "/c/Users/canahmet/AppData/Local/Android/Sdk/cmake/3.22.1/bin/ninja.exe" ]; then
                 MAKE_CMD="/c/Users/canahmet/AppData/Local/Android/Sdk/cmake/3.22.1/bin/ninja.exe"
                 BUILD_TOOL="$MAKE_CMD"
             fi
         fi
    else
         BUILD_TOOL="make"
         MAKE_CMD="make -j$(nproc)"
    fi

    # Build host tool (Requires C++ compiler in PATH: cl.exe, g++, or clang++)
    # Do NOT pass arguments (like -j4) to CMAKE_MAKE_PROGRAM
    "$CMAKE_BIN" .. -DGGML_VULKAN=ON -DCMAKE_BUILD_TYPE=Release $GENERATOR -DCMAKE_MAKE_PROGRAM="$BUILD_TOOL"
    $MAKE_CMD vulkan-shaders-gen
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

# Run assembleRelease for APK and bundleRelease for AAB (Clean first to ensure version code update)
# ./gradlew clean :app:assembleRelease :app:bundleRelease -PuseVulkan=true -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64
# Remove invalid Java Home override, let Gradle find it
# Override gradle.properties windows path with WSL path
./gradlew clean :app:assembleRelease :app:bundleRelease -PuseVulkan=true -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64

echo ""
echo "=== Publish Build Complete ==="
echo "Release APK: $PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
echo "Release Bundle (AAB): $PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab"

# Copy artifacts to project root release_artifacts/
mkdir -p "$PROJECT_DIR/release_artifacts"
cp "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" "$PROJECT_DIR/release_artifacts/"
cp "$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab" "$PROJECT_DIR/release_artifacts/"

echo "Artifacts copied to: $PROJECT_DIR/release_artifacts/"

# Copy artifacts to Windows Mounted Directory (Assuming typical WSL mount)
WINDOWS_DIR="/mnt/c/Users/canahmet/Documents/projects/android_note_app"
if [ -d "$WINDOWS_DIR" ]; then
    echo "Copying artifacts to Windows directory: $WINDOWS_DIR/release_artifacts"
    mkdir -p "$WINDOWS_DIR/release_artifacts"
    cp -f "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" "$WINDOWS_DIR/release_artifacts/"
    cp -f "$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab" "$WINDOWS_DIR/release_artifacts/"
    echo "Done."
else
    echo "Warning: Windows directory not found at $WINDOWS_DIR. Artifacts only in WSL."
fi
