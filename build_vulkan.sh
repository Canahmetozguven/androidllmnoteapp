#!/bin/bash
# Build script for Android with Vulkan - Two-step process
# This script builds the host shader generator first, then the Android APK
set -e

# === Step 0: Sync Source from Windows ===
echo "=== Step 0: Syncing Source from Windows ==="
# Source: Windows Project Path (mounted in WSL)
WINDOWS_SRC="/mnt/c/Users/canahmet/Documents/projects/android_note_app/app/src/"
# Destination: WSL Project Path
WSL_DEST="$HOME/projects/android_note_app/app/src/"

echo "Syncing from: $WINDOWS_SRC"
echo "To: $WSL_DEST"

# Check if source exists to avoid wiping local files if mount fails
if [ -d "$WINDOWS_SRC" ]; then
    # Use rsync to safely copy changes, excluding .git to avoid permission errors
    # -a: archive mode (recursive + preserve attributes)
    # -v: verbose
    # --delete: remove files in dest that are gone in source (optional, safer to omit for now)
    rsync -av --exclude='.git' --exclude='build' "$WINDOWS_SRC" "$WSL_DEST"
    echo "✅ Sync complete."
else
    echo "⚠️  WARNING: Windows source path not found at $WINDOWS_SRC"
    echo "   Skipping sync. Building with current WSL files."
fi

# Environment setup
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

PROJECT_DIR="$(pwd)"
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
echo "=== Step 2: Build Android Release APK & AAB ==="
cd "$PROJECT_DIR"
# rm -rf app/.cxx  # Clear CMake cache to pick up new config

# Use JAVA_HOME if set, otherwise fallback to known path
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
fi

# Build Release variant (APK and AAB)
# -PuseVulkan=true triggers the native Vulkan build logic in app/build.gradle
./gradlew :app:assembleRelease :app:bundleRelease -PuseVulkan=true

# --- Step 3: Copy Artifacts ---
# Create a dedicated directory for build outputs if it doesn't exist
ARTIFACTS_DIR="$PROJECT_DIR/artifacts"
mkdir -p "$ARTIFACTS_DIR"

echo ""
echo "=== Copying Artifacts ==="
echo "Destination: $ARTIFACTS_DIR"

# Copy APK if it exists
if [ -f "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" ]; then
    cp "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" "$ARTIFACTS_DIR/"
    echo "✅ Copied APK to artifacts/"
else
    echo "⚠️  APK not found at expected path"
fi

# Copy AAB if it exists
if [ -f "$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab" ]; then
    cp "$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab" "$ARTIFACTS_DIR/"
    echo "✅ Copied AAB to artifacts/"
fi

echo ""
echo "=== Build Complete ==="
echo "Artifacts are located in: $ARTIFACTS_DIR"

