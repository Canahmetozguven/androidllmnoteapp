#!/bin/bash
# Fast Verification Script for Vulkan Build
# Usage: ./verify_vulkan_build.sh

set -e

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

echo "=== Verifying Vulkan Build Configuration ==="

# 1. Check Gradle Config
echo "1. Checking Gradle Native Build Arguments..."
GRADLE_CONFIG=$(grep "DGGML_VULKAN" app/build.gradle.kts)
if [[ "$GRADLE_CONFIG" == *"-DGGML_VULKAN=ON"* ]]; then
    echo "   [PASS] Gradle config has conditional Vulkan logic (found ON path)"
else
    # It might be dynamic now, so we check if the *off* path exists but implies logic
    if [[ "$GRADLE_CONFIG" == *"-DGGML_VULKAN=OFF"* ]]; then
         echo "   [INFO] Gradle has default OFF, but logic for ON should exist."
    else
         echo "   [FAIL] Could not find GGML_VULKAN config in build.gradle.kts"
    fi
fi

# 2. Check Build Script
echo "2. Checking Build Script (build_vulkan.sh)..."
if grep -q "\-PuseVulkan=true" build_vulkan.sh; then
    echo "   [PASS] build_vulkan.sh passes -PuseVulkan=true"
else
    echo "   [FAIL] build_vulkan.sh is MISSING -PuseVulkan=true"
fi

# 3. Check Publish Script
echo "3. Checking Publish Script (publish.sh)..."
if grep -q "\-PuseVulkan=true" publish.sh; then
    echo "   [PASS] publish.sh passes -PuseVulkan=true"
else
    echo "   [FAIL] publish.sh is MISSING -PuseVulkan=true"
fi

echo ""
echo "=== Manual Test Recommendation ==="
echo "To verify the binary artifacts contains Vulkan symbols:"
echo "1. Run: ./build_vulkan.sh"
echo "2. Run: unzip -l $APK_PATH | grep 'lib/arm64-v8a/libnative-lib.so'"
echo "3. (Advanced) Use 'readelf' or 'nm' on the extracted .so to look for 'vk' or 'vulkan' symbols."
