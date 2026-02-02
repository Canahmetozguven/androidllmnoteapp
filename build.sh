#!/bin/bash
# Unified Build Script for Synapse Notes AI (Android)
# Enables both Vulkan and OpenCL backends for maximum device optimization.
# 
# Usage:
#   ./build.sh static    # Build with static backends (default)
#   ./build.sh dynamic   # Build with dynamic backend loading (dlopen)
#   ./build.sh clean     # Clean build artifacts
#   ./build.sh release   # Clean build + Static build (Production)
#   ./build.sh cpu_ultimate # Build optimized for High-End CPU (SD 8 Elite)
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

# Windows paths for artifact copying
WINDOWS_PROJECT="/mnt/c/Users/canahmet/Documents/projects/android_note_app"
WINDOWS_RELEASE_DIR="$WINDOWS_PROJECT/release_artifacts"

# Use JAVA_HOME if set, otherwise fallback to known path
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
fi

# === Step 0: Sync Source from Windows ===
sync_source() {
    echo "=== Step 0: Syncing Source from Windows ==="
    local WINDOWS_SRC="$WINDOWS_PROJECT/app/src/"
    local WSL_DEST="$PROJECT_DIR/app/src/"
    
    echo "Syncing from: $WINDOWS_SRC"
    echo "To: $WSL_DEST"

    if [[ -d "$WINDOWS_SRC" && "$PROJECT_DIR" != "/mnt/c/"* ]]; then
        mkdir -p "$WSL_DEST"
        # --delete ensures deleted files in Windows are removed from WSL
        rsync -av --delete --exclude='.git' --exclude='build' --exclude='.cxx' "$WINDOWS_SRC" "$WSL_DEST"
        echo "Sync complete."
    else
        echo "WARNING: Windows source not found or running inside /mnt/c/"
        echo "Skipping sync. Building with current WSL files."
    fi
}

# === Step 1: Build host shader generator ===
build_shader_gen() {
    echo ""
    echo "=== Step 1: Build vulkan-shaders-gen for Host ==="
    cd "$LLAMA_DIR"
    
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
    which vulkan-shaders-gen
    cd "$PROJECT_DIR"
}

# === Function: Extract version from build.gradle.kts ===
get_version() {
    local gradle_file="$PROJECT_DIR/app/build.gradle.kts"
    if [ -f "$gradle_file" ]; then
        VERSION_NAME=$(grep 'versionName' "$gradle_file" | head -1 | sed 's/.*"\(.*\)".*/\1/')
        echo "$VERSION_NAME"
    else
        echo "unknown"
    fi
}

# === Clean build ===
clean_build() {
    echo "Cleaning build artifacts..."
    rm -rf "$LLAMA_DIR/build-host"
    rm -rf "$PROJECT_DIR/app/build"
    rm -rf "$PROJECT_DIR/app/.cxx"
    rm -rf "$PROJECT_DIR/.gradle"
    echo "Clean complete"
}

# === Step 3: Copy Artifacts ===
bundle_omp() {
    echo "=== Bundling libomp.so ==="
    # Find libomp.so in NDK
    local OMP_PATH=$(find $ANDROID_HOME/ndk -name "libomp.so" | grep "aarch64" | head -1)
    
    if [ -n "$OMP_PATH" ]; then
        echo "Found libomp.so at: $OMP_PATH"
        local JNI_DIR="$PROJECT_DIR/app/src/main/jniLibs/arm64-v8a"
        mkdir -p "$JNI_DIR"
        cp "$OMP_PATH" "$JNI_DIR/"
        echo "Successfully bundled libomp.so to $JNI_DIR"
    else
        echo "WARNING: libomp.so not found in NDK. OpenMP might fail at runtime."
    fi
}

copy_artifacts() {
    local VERSION=$(get_version)
    local WSL_ARTIFACTS_DIR="$PROJECT_DIR/artifacts"
    
    mkdir -p "$WSL_ARTIFACTS_DIR"
    mkdir -p "$WINDOWS_RELEASE_DIR"
    
    echo ""
    echo "=== Step 3: Copying Artifacts ==="
    echo "Version: $VERSION"
    echo "WSL Destination: $WSL_ARTIFACTS_DIR"
    echo "Windows Destination: $WINDOWS_RELEASE_DIR"
    
    # Source paths
    local APK_SRC="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
    local AAB_SRC="$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab"
    
    # Versioned filenames
    local APK_VERSIONED="SynapseNotes-v${VERSION}.apk"
    local AAB_VERSIONED="SynapseNotes-v${VERSION}.aab"
    
    # Copy APK
    if [ -f "$APK_SRC" ]; then
        # Copy to WSL artifacts (plain name)
        cp "$APK_SRC" "$WSL_ARTIFACTS_DIR/app-release.apk"
        echo "Copied APK to WSL artifacts/"
        
        # Copy to Windows release_artifacts (versioned name)
        cp "$APK_SRC" "$WINDOWS_RELEASE_DIR/$APK_VERSIONED"
        echo "Copied $APK_VERSIONED to Windows release_artifacts/"
    else
        echo "WARNING: APK not found at $APK_SRC"
    fi
    
    # Copy AAB
    if [ -f "$AAB_SRC" ]; then
        # Copy to WSL artifacts (plain name)
        cp "$AAB_SRC" "$WSL_ARTIFACTS_DIR/app-release.aab"
        echo "Copied AAB to WSL artifacts/"
        
        # Copy to Windows release_artifacts (versioned name)
        cp "$AAB_SRC" "$WINDOWS_RELEASE_DIR/$AAB_VERSIONED"
        echo "Copied $AAB_VERSIONED to Windows release_artifacts/"
    else
        echo "WARNING: AAB not found at $AAB_SRC"
    fi
}

# === Step 2: Main build logic ===
build_all() {
    local mode=$1
    local cmake_extra=""
    
    if [ "$mode" == "dynamic" ]; then
        cmake_extra="-DGGML_BACKEND_DL=ON"
        echo "Building in DYNAMIC mode (Backend DL enabled)..."
    else
        echo "Building in STATIC mode..."
    fi

    # Sync source from Windows to WSL
    sync_source
    
    # Build shader generator for Vulkan
    build_shader_gen

    echo ""
    echo "=== Step 2: Build Android Release APK & AAB ==="
    cd "$PROJECT_DIR"
    rm -rf app/.cxx  # Clear CMake cache to pick up new config
    
    # Build Release variant (APK and AAB)
    # -PuseVulkan=true and -PuseOpenCL=true trigger the native build logic in app/build.gradle.kts
    ./gradlew :app:assembleRelease :app:bundleRelease \
        -Dorg.gradle.java.home="/usr/lib/jvm/java-17-openjdk-amd64" \
        -PuseVulkan=true \
        -PuseOpenCL=true \
        -PcmakeFlags="$cmake_extra"

    # Copy artifacts to WSL and Windows
    copy_artifacts

    echo ""
    echo "=== Build Complete ==="
    echo "Artifacts are located in:"
    echo "  WSL: $PROJECT_DIR/artifacts"
    echo "  Windows: $WINDOWS_RELEASE_DIR"
}

# === Execution ===
case "$BUILD_MODE" in
    clean)
        clean_build
        ;;
    release)
        echo "🚀 Starting Full Release Build (Clean + Static)..."
        clean_build
        build_all "static"
        ;;
    cpu_ultimate)
        echo "💪 Starting ULTIMATE CPU Build (Clean + I8MM + No GPU)..."
        clean_build
        
        # Override build_all logic slightly for this special case
        sync_source
        
        # We don't need shader gen for CPU only
        echo "⚠️  Skipping shader generation (CPU Mode)"
        
        echo "⚙️  Compiling Android APK with ULTIMATE CPU Optimizations..."
        # Bundle OpenMP for CPU mode
        bundle_omp
        
        ./gradlew :app:assembleRelease :app:bundleRelease \
            -Dorg.gradle.java.home="/usr/lib/jvm/java-17-openjdk-amd64" \
            -PuseVulkan=false \
            -PuseOpenCL=false \
            -PcmakeFlags="-DULTIMATE_CPU=ON -DGGML_OPENCL=OFF -DGGML_VULKAN=OFF" \
            2>&1 | grep -E "^Building|error|warning|:app:" || true
            
        copy_artifacts
        
        echo "✅ Ultimate CPU Build Finished"
        ;;
    static|dynamic)
        build_all "$BUILD_MODE"
        ;;
    *)
        echo "Usage: $0 {static|dynamic|clean|release}"
        exit 1
        ;;
esac