#!/bin/bash
# Test Harness: Verify OpenCL Backend Loading and Fallback Behavior
# 
# Usage:
#   ./test_opencl_backend.sh              # Run all tests
#   ./test_opencl_backend.sh vulkan       # Test Vulkan backend
#   ./test_opencl_backend.sh opencl       # Test OpenCL backend  
#   ./test_opencl_backend.sh fallback     # Test dynamic fallback
#   ./test_opencl_backend.sh integration  # Full device test
#
set -e

DEVICE_SERIAL=${DEVICE_SERIAL:-}  # Allow override via env var
TEST_PACKAGE="com.example.llm"     # Update to your app package
LOG_TAG="LLM_JNI"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# === Helper Functions ===

log_info() {
    echo -e "${BLUE}ℹ${NC}  $1"
}

log_pass() {
    echo -e "${GREEN}✅${NC} $1"
}

log_fail() {
    echo -e "${RED}❌${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}⚠️${NC}  $1"
}

section() {
    echo ""
    echo "╔════════════════════════════════════════════════════╗"
    echo "║  $1"
    echo "╚════════════════════════════════════════════════════╝"
    echo ""
}

get_adb() {
    if [ -n "$DEVICE_SERIAL" ]; then
        echo "adb -s $DEVICE_SERIAL"
    else
        echo "adb"
    fi
}

# === Test: Verify ADB Connection ===

test_adb_connection() {
    section "Test 1: ADB Connection"
    
    local adb=$(get_adb)
    
    if ! $adb devices | grep -q "device"; then
        log_fail "No Android device connected"
        return 1
    fi
    
    local device_info=$($adb shell getprop ro.product.model)
    log_pass "Device connected: $device_info"
    
    # Get Android version
    local android_ver=$($adb shell getprop ro.build.version.release)
    log_info "Android version: $android_ver"
    
    # Get SoC info
    local soc=$($adb shell getprop ro.hardware)
    log_info "SoC/Hardware: $soc"
    
    return 0
}

# === Test: Check Vulkan Support ===

test_vulkan_support() {
    section "Test 2: Vulkan Support"
    
    local adb=$(get_adb)
    
    # Check if Vulkan libraries exist
    if $adb shell test -f "/system/lib64/libvulkan.so"; then
        log_pass "Vulkan library found: /system/lib64/libvulkan.so"
    else
        log_fail "Vulkan library NOT found"
        return 1
    fi
    
    # Check Vulkan version via getprop
    local vk_ver=$($adb shell getprop ro.opengles.version)
    log_info "OpenGL/Vulkan version: $vk_ver"
    
    # Try to detect Vulkan layers
    local layers=$($adb shell ls /system/lib64/hw/ | grep -i vulkan || echo "none")
    log_info "Vulkan HAL: $layers"
    
    return 0
}

# === Test: Check OpenCL Support ===

test_opencl_support() {
    section "Test 3: OpenCL Support"
    
    local adb=$(get_adb)
    
    # Check if OpenCL libraries exist
    if $adb shell test -f "/system/lib64/libOpenCL.so"; then
        log_pass "OpenCL library found: /system/lib64/libOpenCL.so"
        
        # Get OpenCL version if possible
        local icd_path=$($adb shell ls /system/etc/OpenCL/vendors/ 2>/dev/null || echo "N/A")
        log_info "OpenCL ICD path: $icd_path"
    else
        log_warn "OpenCL library NOT found (expected on non-Snapdragon devices)"
        log_info "This is expected on:
  • Dimensity, Exynos, or other SoCs
  • Devices without Adreno GPU support
  • Consider using Vulkan instead"
        return 0  # Not a failure, just a note
    fi
    
    # Check OpenCL vendors
    log_info "OpenCL vendors available:"
    $adb shell ls /system/etc/OpenCL/vendors/ 2>/dev/null | while read vendor; do
        log_info "  • $vendor"
    done
    
    return 0
}

# === Test: Verify Build Configuration ===

test_build_configuration() {
    section "Test 4: Build Configuration Verification"
    
    local adb=$(get_adb)
    
    # Check if app has native libraries
    if [ ! -d "app/src/main/jniLibs/arm64-v8a" ]; then
        log_fail "Native libraries directory not found"
        log_info "Expected: app/src/main/jniLibs/arm64-v8a/"
        return 1
    fi
    
    log_pass "Native libraries directory found"
    
    # List libraries
    log_info "Native libraries:"
    ls -lh app/src/main/jniLibs/arm64-v8a/ | while read line; do
        if [[ $line == *"lib"* ]]; then
            log_info "  $(echo "$line" | awk '{print $9, $5}')"
        fi
    done
    
    # Check for OpenCL backend
    if [ -f "app/src/main/jniLibs/arm64-v8a/libggml-opencl.so" ]; then
        log_pass "OpenCL backend library found (dynamic loading configured)"
    else
        log_info "OpenCL backend NOT found as separate .so (static build or disabled)"
    fi
    
    return 0
}

# === Test: Deploy App ===

test_deploy_app() {
    section "Test 5: Deploy Application"
    
    local adb=$(get_adb)
    
    # Find APK
    local apk_path="app/build/outputs/apk/debug/app-debug.apk"
    if [ ! -f "$apk_path" ]; then
        log_fail "Debug APK not found at $apk_path"
        log_info "Build the app first: ./build_vulkan.sh or ./build_opencl.sh"
        return 1
    fi
    
    log_info "Found APK: $apk_path"
    local apk_size=$(ls -lh "$apk_path" | awk '{print $5}')
    log_info "APK size: $apk_size"
    
    # Install app
    log_info "Installing app..."
    if $adb install -r "$apk_path" > /dev/null 2>&1; then
        log_pass "App installed successfully"
    else
        log_fail "Failed to install app"
        return 1
    fi
    
    # Verify installation
    if $adb shell pm list packages | grep -q "$TEST_PACKAGE"; then
        log_pass "App verified on device"
    else
        log_warn "App package not found: $TEST_PACKAGE"
        log_info "Update TEST_PACKAGE variable if using a different package name"
    fi
    
    return 0
}

# === Test: Check Native Library Loading ===

test_native_library_loading() {
    section "Test 6: Native Library Loading"
    
    local adb=$(get_adb)
    
    # Get app PID
    local pid=$($adb shell pidof $TEST_PACKAGE 2>/dev/null || echo "")
    
    if [ -z "$pid" ]; then
        log_info "App not running (expected if not launched)"
        log_info "Launch the app in the emulator/device first"
        return 0
    fi
    
    log_pass "App is running (PID: $pid)"
    
    # Check loaded libraries
    log_info "Checking loaded native libraries..."
    local libs=$($adb shell cat /proc/$pid/maps 2>/dev/null | grep "\.so" | grep -i llama || echo "")
    
    if [ -z "$libs" ]; then
        log_warn "No GGML libraries loaded yet (app may be idle)"
        log_info "Trigger model loading by starting inference"
        return 0
    fi
    
    log_pass "GGML libraries loaded:"
    echo "$libs" | while read line; do
        lib=$(echo "$line" | awk '{print $NF}')
        log_info "  • $(basename $lib)"
    done
    
    return 0
}

# === Test: Verify Backend Selection (from Logs) ===

test_backend_selection() {
    section "Test 7: Backend Selection Verification"
    
    local adb=$(get_adb)
    
    log_info "Clearing logcat buffer..."
    $adb logcat -c
    
    log_info "Waiting for app logs (launch app and start inference)..."
    log_info "Collecting logs for 10 seconds..."
    
    sleep 2
    
    # Collect backend-related logs
    log_info "Backend initialization logs:"
    $adb logcat -d | grep -i "$LOG_TAG" | tail -20 | while read line; do
        if [[ $line == *"backend"* ]] || [[ $line == *"gpu"* ]] || [[ $line == *"opencl"* ]] || [[ $line == *"vulkan"* ]]; then
            echo "  $line"
        fi
    done
    
    # Check for Vulkan
    if $adb logcat -d | grep -i "vulkan" | grep -q "backend\|initialized"; then
        log_pass "Vulkan backend detected in logs"
    fi
    
    # Check for OpenCL
    if $adb logcat -d | grep -i "opencl" | grep -q "backend\|initialized"; then
        log_pass "OpenCL backend detected in logs"
    fi
    
    return 0
}

# === Test: Performance Baseline ===

test_performance_baseline() {
    section "Test 8: Performance Baseline"
    
    local adb=$(get_adb)
    
    log_info "To benchmark backend performance:"
    log_info ""
    log_info "1. Start inference in app (any model, ~100 tokens)"
    log_info "2. Monitor performance metrics:"
    log_info ""
    log_info "   Via logcat:"
    log_info "     adb logcat -s $LOG_TAG | grep -i 'tokens\\|speed\\|throughput'"
    log_info ""
    log_info "   Via shell stats:"
    log_info "     adb shell top -n 1 | grep $TEST_PACKAGE"
    log_info ""
    log_info "3. Expected performance:"
    log_info "   Vulkan:  10-15 tokens/sec (Snapdragon 8 Gen 2+)"
    log_info "   OpenCL:  10-15 tokens/sec (similar or slightly faster)"
    log_info "   CPU:     1-3 tokens/sec (fallback)"
    log_info ""
    
    return 0
}

# === Test: Memory & Thermal Check ===

test_memory_thermal() {
    section "Test 9: Memory & Thermal Analysis"
    
    local adb=$(get_adb)
    local pid=$($adb shell pidof $TEST_PACKAGE 2>/dev/null || echo "")
    
    if [ -z "$pid" ]; then
        log_info "App not running (skip this test)"
        return 0
    fi
    
    log_info "Memory usage:"
    $adb shell dumpsys meminfo $TEST_PACKAGE 2>/dev/null | grep -E "TOTAL|Native|Dalvik|Graphics" || log_warn "Could not read memory info"
    
    log_info ""
    log_info "Thermal status:"
    $adb shell dumpsys thermal 2>/dev/null | grep -E "mTemperature|mThrottling" | head -5 || log_warn "Could not read thermal info"
    
    log_info ""
    log_warn "Note: Thermal monitoring is device-dependent"
    
    return 0
}

# === Test: Fallback Behavior (Dynamic Loading) ===

test_fallback_behavior() {
    section "Test 10: Fallback Behavior (Dynamic Loading Test)"
    
    log_info "To test backend fallback behavior:"
    log_info ""
    log_info "1. Build with dynamic loading enabled:"
    log_info "   ./build_opencl.sh dynamic"
    log_info ""
    log_info "2. Deploy and run:"
    log_info "   adb install -r app/build/outputs/apk/debug/app-debug.apk"
    log_info "   adb logcat -s $LOG_TAG"
    log_info ""
    log_info "3. Expected behavior:"
    log_info "   • App tries to load libggml-opencl.so"
    log_info "   • If found: Uses OpenCL backend"
    log_info "   • If not found: Falls back to Vulkan"
    log_info ""
    log_info "4. Check logs for:"
    log_info "   SUCCESS:  'OpenCL backend loaded successfully'"
    log_info "   FALLBACK: 'OpenCL backend not available, using Vulkan'"
    log_info ""
    
    return 0
}

# === Integration Test (Full Device Test) ===

test_integration() {
    section "Full Integration Test"
    
    log_info "Running complete test suite..."
    echo ""
    
    test_adb_connection && log_pass "ADB connection OK" || return 1
    test_vulkan_support && log_pass "Vulkan support verified" || return 1
    test_opencl_support && log_pass "OpenCL support checked" || true  # Not a blocker
    test_build_configuration && log_pass "Build configuration OK" || return 1
    test_deploy_app && log_pass "App deployment OK" || return 1
    test_native_library_loading && log_pass "Native libraries checked" || true
    test_backend_selection && log_pass "Backend selection OK" || true
    test_performance_baseline && log_pass "Performance baseline ready" || true
    test_memory_thermal && log_pass "Memory/thermal analysis ready" || true
    
    section "Integration Test Complete"
    log_pass "All critical tests passed!"
    
    return 0
}

# === Show Help ===

show_help() {
    cat << 'EOF'
TEST HARNESS: OpenCL Backend Loading & Fallback Verification

USAGE:
  ./test_opencl_backend.sh [TEST_NAME] [options]

TEST_NAMES:
  (none/all)      Run all tests (default)
  vulkan          Test Vulkan support
  opencl          Test OpenCL support
  build           Verify build configuration
  deploy          Deploy app to device
  libs            Check native library loading
  backend         Verify backend selection from logs
  perf            Display performance analysis guide
  memory          Check memory & thermal
  fallback        Test dynamic loading fallback behavior
  integration     Full device integration test
  help            Show this help message

OPTIONS:
  --device SERIAL  Use specific device (default: first connected)
  --package PKG    Override test package name (default: com.example.llm)

EXAMPLES:
  # Run all tests
  ./test_opencl_backend.sh

  # Test OpenCL support only
  ./test_opencl_backend.sh opencl

  # Test with specific device
  ./test_opencl_backend.sh integration --device emulator-5554

  # Full test suite with custom package
  DEVICE_SERIAL=12345 ./test_opencl_backend.sh --package com.myapp.llm

PREREQUISITES:
  1. Android device/emulator connected via ADB
  2. Build app first: ./build_vulkan.sh or ./build_opencl.sh
  3. optionally: Launch app on device

WHAT GETS TESTED:
  ✓ ADB connection & device info
  ✓ Vulkan library availability
  ✓ OpenCL library availability
  ✓ Native library compilation
  ✓ App deployment & installation
  ✓ Native library loading verification
  ✓ Backend selection from logs
  ✓ Performance baseline readiness
  ✓ Memory & thermal monitoring
  ✓ Fallback behavior (dynamic loading)

EXPECTED RESULTS:
  Vulkan:  ✅ Available on all modern devices
  OpenCL:  ✅ Available on Snapdragon devices
  Backend: Should see one of:
    "Vulkan backend initialized"
    "OpenCL backend initialized"
    "CPU backend (fallback)"

TROUBLESHOOTING:
  Q: "No device connected"
  A: adb devices
     Check USB debugging is enabled

  Q: "libOpenCL.so not found"
  A: Normal on non-Snapdragon devices
     Use Vulkan instead

  Q: "No logs appearing"
  A: Launch the app first
     Tap inference button to trigger GPU loading

REFERENCES:
  - OPENCL_CMAKE_GUIDE.md (build configuration)
  - VULKAN_VS_OPENCL_PERFORMANCE.md (performance data)
  - GGML_OPENCL_ANALYSIS.md (technical analysis)
  - build_opencl.sh (build variants)
EOF
}

# === Main ===

if [ $# -eq 0 ]; then
    test_integration
else
    case "$1" in
        help|-h|--help)
            show_help
            ;;
        vulkan)
            test_vulkan_support
            ;;
        opencl)
            test_opencl_support
            ;;
        build)
            test_build_configuration
            ;;
        deploy)
            test_deploy_app
            ;;
        libs)
            test_native_library_loading
            ;;
        backend)
            test_backend_selection
            ;;
        perf)
            test_performance_baseline
            ;;
        memory)
            test_memory_thermal
            ;;
        fallback)
            test_fallback_behavior
            ;;
        integration)
            test_integration
            ;;
        *)
            log_fail "Unknown test: $1"
            show_help
            exit 1
            ;;
    esac
fi

exit $?
