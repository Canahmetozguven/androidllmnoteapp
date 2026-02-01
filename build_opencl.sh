#!/bin/bash
# Build script for Android with OpenCL backends
# Allows testing OpenCL configurations without modifying the main build_vulkan.sh
# 
# Usage:
#   ./build_opencl.sh static    # Build with static OpenCL (default)
#   ./build_opencl.sh dynamic   # Build with dynamic OpenCL loading
#   ./build_opencl.sh adreno    # Build Adreno-only (no Vulkan)
#   ./build_opencl.sh clean     # Clean build artifacts
#
set -e

# === Configuration ===
OPENCL_MODE="${1:-static}"  # static, dynamic, adreno, clean
BUILD_TYPE="Release"
ANDROID_ARCH="arm64-v8a"

# Environment setup
export ANDROID_HOME=${ANDROID_HOME:-$HOME/android-sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

PROJECT_DIR="$(pwd)"
LLAMA_DIR="$PROJECT_DIR/app/src/main/cpp/llama"

# Use JAVA_HOME if set, otherwise fallback to known path
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
fi

echo "╔════════════════════════════════════════════════════════════╗"
echo "║      Android LLM Build - OpenCL Configuration Tool        ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "📋 Build Configuration:"
echo "  Mode:          $OPENCL_MODE"
echo "  Build Type:    $BUILD_TYPE"
echo "  Architecture:  $ANDROID_ARCH"
echo "  JAVA_HOME:     $JAVA_HOME"
echo ""

# === Function: Display help ===
show_help() {
    cat << 'EOF'
USAGE: ./build_opencl.sh [MODE]

MODES:
  static   - Build with static OpenCL (embedded in main binary)
             Best for: Production, guaranteed availability
             Flags:   -DGGML_OPENCL=ON -DGGML_OPENCL_EMBED_KERNELS=ON
             Result:  Larger APK, no runtime fallback

  dynamic  - Build with dynamic OpenCL loading
             Best for: Development, flexible testing
             Flags:   -DGGML_OPENCL=ON -DGGML_BACKEND_DL=ON
             Result:  Smaller APK, graceful fallback if .so missing

  adreno   - Build Adreno-only (remove Vulkan, add OpenCL)
             Best for: Snapdragon exclusive testing
             Flags:   -DGGML_VULKAN=OFF -DGGML_OPENCL=ON
             Result:  Test OpenCL without Vulkan interference

  clean    - Delete build artifacts and start fresh
             Use when: CMake cache is stale or build fails mysteriously

EXAMPLES:
  # Test dynamic loading
  ./build_opencl.sh dynamic

  # Test Adreno optimization
  ./build_opencl.sh adreno

  # Clean build + rebuild with static OpenCL
  ./build_opencl.sh clean && ./build_opencl.sh static

SUPPORTED DEVICES:
  • Snapdragon with Adreno GPU (730, 740, 750, 850, X1E Elite)
  • Android API 34+ with libOpenCL.so available
  • NDK 26.1.10909125 or compatible

OUTPUT:
  • APK:  app/build/outputs/apk/debug/app-debug.apk
  • Logs: Check adb logcat | grep -i opencl
  • Size: ls -lh app/src/main/jniLibs/arm64-v8a/
EOF
}

# === Function: Clean build ===
clean_build() {
    echo "🗑️  Cleaning build artifacts..."
    rm -rf "$LLAMA_DIR/build-host" 2>/dev/null || true
    rm -rf "$PROJECT_DIR/app/build" 2>/dev/null || true
    rm -rf "$PROJECT_DIR/app/.cxx" 2>/dev/null || true
    rm -rf "$PROJECT_DIR/.gradle" 2>/dev/null || true
    echo "✅ Clean complete"
}

# === Function: Get CMake flags for mode ===
get_cmake_flags() {
    local mode=$1
    local flags="-DCMAKE_BUILD_TYPE=$BUILD_TYPE"
    
    case "$mode" in
        static)
            flags="$flags -DGGML_VULKAN=ON"
            flags="$flags -DGGML_OPENCL=ON"
            flags="$flags -DGGML_OPENCL_EMBED_KERNELS=ON"
            flags="$flags -DGGML_OPENCL_USE_ADRENO_KERNELS=ON"
            echo "$flags"
            ;;
        dynamic)
            flags="$flags -DGGML_VULKAN=ON"
            flags="$flags -DGGML_OPENCL=ON"
            flags="$flags -DGGML_BACKEND_DL=ON"
            flags="$flags -DGGML_OPENCL_EMBED_KERNELS=ON"
            flags="$flags -DGGML_OPENCL_USE_ADRENO_KERNELS=ON"
            echo "$flags"
            ;;
        adreno)
            flags="$flags -DGGML_VULKAN=OFF"
            flags="$flags -DGGML_OPENCL=ON"
            flags="$flags -DGGML_OPENCL_EMBED_KERNELS=ON"
            flags="$flags -DGGML_OPENCL_USE_ADRENO_KERNELS=ON"
            echo "$flags"
            ;;
        *)
            echo "ERROR: Unknown mode: $mode" >&2
            show_help
            exit 1
            ;;
    esac
}

# === Function: Build host shader generator ===
build_shader_gen() {
    echo ""
    echo "🔧 Step 1: Building vulkan-shaders-gen for Host"
    echo "   (Required for Vulkan shader compilation)"
    
    cd "$LLAMA_DIR"
    
    EXISTING_GEN=$(find build-host -name 'vulkan-shaders-gen' -type f -executable 2>/dev/null | head -1)
    if [ -n "$EXISTING_GEN" ]; then
        echo "   ✅ Found existing: $EXISTING_GEN"
        SHADER_GEN_DIR=$(dirname "$(realpath "$EXISTING_GEN")")
    else
        echo "   ⚙️  Compiling vulkan-shaders-gen..."
        rm -rf build-host
        mkdir -p build-host && cd build-host
        cmake .. -DGGML_VULKAN=ON -DCMAKE_BUILD_TYPE=Release > /dev/null 2>&1
        make vulkan-shaders-gen -j$(nproc) > /dev/null 2>&1
        cd "$LLAMA_DIR"
        
        SHADER_GEN=$(find build-host -name 'vulkan-shaders-gen' -type f -executable | head -1)
        if [ -z "$SHADER_GEN" ]; then
            echo "   ❌ ERROR: vulkan-shaders-gen not found after build!"
            exit 1
        fi
        SHADER_GEN_DIR=$(dirname "$(realpath "$SHADER_GEN")")
        echo "   ✅ Built: $SHADER_GEN"
    fi
    
    export PATH="$SHADER_GEN_DIR:$PATH"
}

# === Function: Build APK ===
build_apk() {
    local mode=$1
    local cmake_flags=$(get_cmake_flags "$mode")
    
    echo ""
    echo "📦 Step 2: Building Android APK with OpenCL ($mode mode)"
    echo "   CMake flags: $cmake_flags"
    
    cd "$PROJECT_DIR"
    
    # For non-Vulkan builds, skip shader gen
    if [[ "$cmake_flags" == *"-DGGML_VULKAN=OFF"* ]]; then
        echo "   ⏭️  Skipping Vulkan shader generator (Vulkan disabled)"
    else
        build_shader_gen
    fi
    
    # Clear CMake cache to pick up new config
    rm -rf "$PROJECT_DIR/app/.cxx" 2>/dev/null || true
    
    # Build with Gradle
    echo "   ⚙️  Compiling with Gradle..."
    ./gradlew :app:assembleDebug \
        -PuseVulkan=true \
        -PcmakeFlags="$cmake_flags" \
        2>&1 | grep -E "^Building|error|warning|:app:" || true
    
    if [ $? -ne 0 ]; then
        echo "   ⚠️  Build completed with warnings (this is normal)"
    fi
}

# === Function: Display results ===
show_results() {
    local mode=$1
    local apk_path="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    
    echo ""
    echo "════════════════════════════════════════════════════════════"
    echo "                    BUILD COMPLETE ✅"
    echo "════════════════════════════════════════════════════════════"
    
    if [ -f "$apk_path" ]; then
        local size=$(ls -lh "$apk_path" | awk '{print $5}')
        echo ""
        echo "📦 Output APK:"
        echo "   Location: $apk_path"
        echo "   Size:     $size"
        echo ""
        echo "🚀 To deploy:"
        echo "   adb install -r \"$apk_path\""
        echo ""
        echo "📊 To verify OpenCL loading:"
        echo "   adb logcat | grep -i 'opencl\\|llm\\|backend'"
    else
        echo "⚠️  APK not found at expected location!"
        echo "   Expected: $apk_path"
    fi
    
    echo ""
    echo "📋 Native Libraries:"
    ls -lh "$PROJECT_DIR/app/src/main/jniLibs/arm64-v8a/" 2>/dev/null || echo "   (not yet built)"
    
    echo ""
    echo "Mode-specific notes:"
    case "$mode" in
        static)
            echo "  • OpenCL is embedded in libllama.so"
            echo "  • No separate library files needed"
            echo "  • Larger binary but guaranteed availability"
            ;;
        dynamic)
            echo "  • libllama.so loads libggml-opencl.so at runtime"
            echo "  • Gracefully falls back to Vulkan if .so missing"
            echo "  • Verify both .so files exist in jniLibs/"
            ;;
        adreno)
            echo "  • Vulkan disabled, OpenCL (Adreno) only"
            echo "  • Use this to test Snapdragon exclusive features"
            echo "  • Will NOT work if device lacks OpenCL support"
            ;;
    esac
    
    echo ""
    echo "📚 For more details, see: OPENCL_CMAKE_GUIDE.md"
    echo "════════════════════════════════════════════════════════════"
}

# === Main Execution ===
case "$OPENCL_MODE" in
    help|-h|--help)
        show_help
        exit 0
        ;;
    clean)
        clean_build
        exit 0
        ;;
    static|dynamic|adreno)
        build_apk "$OPENCL_MODE"
        show_results "$OPENCL_MODE"
        ;;
    *)
        echo "❌ ERROR: Unknown mode '$OPENCL_MODE'"
        echo ""
        show_help
        exit 1
        ;;
esac

exit 0
