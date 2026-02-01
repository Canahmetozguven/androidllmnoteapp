#!/bin/bash
# Unified Build Script for Synapse Notes AI (Android)
# Enables both Vulkan and OpenCL backends for maximum device optimization.
# 
# Usage:
#   ./build.sh static    # Build with static backends (default)
#   ./build.sh dynamic   # Build with dynamic backend loading (dlopen)
#   ./build.sh clean     # Clean build artifacts
#   ./build.sh release   # Clean build + Static build (Production)
#
set -e

# === Configuration ===
BUILD_MODE="${1:-static}"  # static, dynamic, clean, release
BUILD_TYPE="Release"
ANDROID_ARCH="arm64-v8a"

# Environment setup
export ANDROID_HOME=${ANDROID_HOME:-$HOME/android-sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

PROJECT_DIR="$(pwd)"
LLAMA_DIR="$PROJECT_DIR/app/src/main/cpp/llama"

# Sync Source from Windows (WSL only)
sync_source() {
    # Hardcoded Windows path for your environment
    local WINDOWS_SRC="/mnt/c/Users/canahmet/Documents/projects/android_note_app/app/src/"
    local WSL_DEST="$PROJECT_DIR/app/src/"
    
    echo "🔍 Checking source path: $WINDOWS_SRC"

    if [[ -d "$WINDOWS_SRC" && "$PROJECT_DIR" != "/mnt/c/"* ]]; then
        echo "🔄 Syncing source from Windows..."
        mkdir -p "$WSL_DEST"
        # Sync app/src to app/src
        # --delete ensures deleted files in Windows are removed from WSL
        rsync -av --delete --exclude='.git' --exclude='build' --exclude='.cxx' "$WINDOWS_SRC" "$WSL_DEST"
        echo "✅ Sync complete."
    else
        echo "⚠️  Skipping sync: Windows source not found or running inside /mnt/c/"
    fi
}

# Build host shader generator
build_shader_gen() {
    echo "🔧 Building vulkan-shaders-gen for Host..."
    cd "$LLAMA_DIR"
    
    EXISTING_GEN=$(find build-host -name 'vulkan-shaders-gen' -type f -executable 2>/dev/null | head -1)
    if [ -n "$EXISTING_GEN" ]; then
        echo "   ✅ Found existing: $EXISTING_GEN"
        SHADER_GEN_DIR=$(dirname "$(realpath "$EXISTING_GEN")")
    else
        rm -rf build-host
        mkdir -p build-host && cd build-host
        cmake .. -DGGML_VULKAN=ON -DCMAKE_BUILD_TYPE=Release > /dev/null 2>&1
        make vulkan-shaders-gen -j$(nproc) > /dev/null 2>&1
        cd "$LLAMA_DIR"
        SHADER_GEN=$(find build-host -name 'vulkan-shaders-gen' -type f -executable | head -1)
        SHADER_GEN_DIR=$(dirname "$(realpath "$SHADER_GEN")")
        echo "   ✅ Built: $SHADER_GEN"
    fi
    export PATH="$SHADER_GEN_DIR:$PATH"
    cd "$PROJECT_DIR"
}

# Clean build
clean_build() {
    echo "🗑️  Cleaning build artifacts..."
    rm -rf "$LLAMA_DIR/build-host" 2>/dev/null || true
    rm -rf "$PROJECT_DIR/app/build" 2>/dev/null || true
    rm -rf "$PROJECT_DIR/app/.cxx" 2>/dev/null || true
    rm -rf "$PROJECT_DIR/.gradle" 2>/dev/null || true
    echo "✅ Clean complete"
}

# Main build logic
build_all() {
    local mode=$1
    local cmake_extra=""
    
    if [ "$mode" == "dynamic" ]; then
        cmake_extra="-DGGML_BACKEND_DL=ON"
        echo "🏗️  Building in DYNAMIC mode (Backend DL enabled)..."
    else
        echo "🏗️  Building in STATIC mode..."
    fi

    # Sync happens inside build_all to ensure it runs before compilation
    sync_source
    build_shader_gen

    echo "⚙️  Compiling Android APK with Vulkan + OpenCL..."
    ./gradlew :app:assembleRelease \
        -PuseVulkan=true \
        -PuseOpenCL=true \
        -PcmakeFlags="$cmake_extra" \
        2>&1 | grep -E "^Building|error|warning|:app:" || true

    echo "✅ Build Process Finished"
    echo "📦 Output: app/build/outputs/apk/release/app-release.apk"
}

# Execution
case "$BUILD_MODE" in
    clean)
        clean_build
        ;;;
    release)
        echo "🚀 Starting Full Release Build (Clean + Static)..."
        clean_build
        build_all "static"
        ;;;
    static|dynamic)
        build_all "$BUILD_MODE"
        ;;;
    *)
        echo "Usage: $0 {static|dynamic|clean|release}"
        exit 1
        ;;;
esac