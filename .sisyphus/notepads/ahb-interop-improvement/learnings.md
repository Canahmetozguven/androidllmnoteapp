# Learnings - AHB Interop Improvement

## Context
- Goal: Hybrid Vulkan + OpenCL interop using AHB (shared buffers).
- Device Scope: ARM Mali + Qualcomm Adreno.
- Vendored Code: DO NOT TOUCH `app/src/main/cpp/llama/`.
- Min SDK: 28+.

## AHB Requirements
- Vulkan Extension: `VK_ANDROID_external_memory_android_hardware_buffer`
- OpenCL Extensions (ARM): `cl_arm_import_memory`, `cl_arm_import_memory_android_hardware_buffer`
- OpenCL Extensions (Qualcomm): `cl_qcom_android_hardware_buffer_interop`

## Implementation Pattern
- Detect extensions at runtime.
- Use JNI/Native layer for all AHB logic.
- Expose capability to Kotlin via `HardwareCapabilityProvider`.

## Implementation Completed - AHB Capability Detection

### What was added to native-lib.cpp:
1. **AHBCapability struct** (lines 17-27):
   - Tracks Vulkan and OpenCL AHB extension availability
   - Stores GPU vendor info
   - Provides `is_ahb_available()` helper method

2. **detect_ahb_capabilities() function** (lines 29-130):
   - Detects GPU vendor using existing `detect_gpu_vendor()`
   - Checks Vulkan AHB support (optimistic assumption, requires runtime verification)
   - Dynamically loads OpenCL library using `dlopen`
   - Queries OpenCL platform extensions using `clGetPlatformIDs` and `clGetPlatformInfo`
   - Searches for vendor-specific extensions:
     - ARM Mali: `cl_arm_import_memory`, `cl_arm_import_memory_android_hardware_buffer`
     - Qualcomm Adreno: `cl_qcom_android_hardware_buffer_interop`
   - Logs detailed capability information

3. **isAHBSupported JNI method** (lines 905-961):
   - New public API: `Java_com_synapsenotes_ai_core_ai_LlamaContext_isAHBSupported(backend_id)`
   - Returns JNI_TRUE/JNI_FALSE based on backend and vendor
   - Backend-specific logic:
     - CPU (0): Returns false (AHB not needed)
     - Vulkan (1): Checks `vulkan_ahb_supported`
     - OpenCL (2): Vendor-aware check (ARM uses two extensions, Qualcomm uses one)

4. **JNI Registration update** (line 245):
   - Added `{"isAHBSupported", "(I)Z", (void*)...}` to RegisterNatives array

### Extension Detection Strategy:
- **Vulkan**: Optimistic flag (true by default) with note that full verification requires `vkEnumerateDeviceExtensionProperties` during instance creation
- **OpenCL**: Runtime query via dlsym pattern (no header dependencies)
  - Handles multiple library paths (`/system/vendor/lib64/libOpenCL.so`, etc.)
  - Gracefully fails if OpenCL unavailable
  - Uses raw CL API constants to avoid header requirements (e.g., `CL_PLATFORM_EXTENSIONS = 0x0900`)

### Vendor Abstraction:
- Reuses existing `GPUVendor` enum (GPU_ADRENO, GPU_MALI, GPU_UNKNOWN)
- ARM Mali path: Requires BOTH `cl_arm_import_memory` AND `cl_arm_import_memory_android_hardware_buffer`
- Qualcomm Adreno path: Requires `cl_qcom_android_hardware_buffer_interop`
- Unknown vendors: Logs warning, returns false for safety

### Logging:
- All detection steps logged to `ANDROID_LOG_INFO/WARN/DEBUG` with tag "LLM_JNI"
- Summary log includes vendor, all extension flags, and overall availability status
- Per-backend capability logging in `isAHBSupported`

### Next Steps (for Kotlin integration):
- Add corresponding method in `LlamaContext.kt`: `external fun isAHBSupported(backendId: Int): Boolean`
- Expose via `HardwareCapabilityProvider` or similar abstraction
- Use in UI to show AHB availability badge/warning

### Known Limitations:
- Vulkan check is optimistic (assumes API 28+ has extension support)
  - Full check requires Vulkan instance creation + `vkEnumerateDeviceExtensionProperties`
  - Deferred to avoid initialization overhead during capability probe
- OpenCL extension check requires platform initialization (minor overhead)
- Does not verify format compatibility (RGBA8, etc.) - assumes standard AHB formats supported

### Testing Strategy:
- Test on ARM Mali device (Samsung Exynos, MediaTek) - should detect `cl_arm_*` extensions
- Test on Qualcomm Adreno device (Snapdragon) - should detect `cl_qcom_*` extension
- Test on CPU-only device - should gracefully handle missing OpenCL library
- Verify logs in `adb logcat | grep LLM_JNI` for detailed extension enumeration


## Compilation Fixes - DefaultHardwareCapabilityProvider.kt and GpuProbeService.kt

### Problem Summary
- Missing imports in `DefaultHardwareCapabilityProvider.kt` causing "Unresolved reference" errors
- Duplicate `probeBackend` method implementation in the same file
- `GpuProbeService.kt` import validation

### Solution Applied

#### DefaultHardwareCapabilityProvider.kt Fixes

**Added Missing Imports (Lines 3-20):**
```kotlin
import android.content.Context
import android.content.ServiceConnection
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.Build
import android.content.pm.PackageManager
import android.app.ActivityManager
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import com.synapsenotes.ai.core.ai.IGpuProbeService
import com.synapsenotes.ai.core.ai.probe.GpuProbeService
```

**Duplicate Method Removal:**
- Identified TWO implementations of `override fun probeBackend(backend: BackendType): Boolean`
  - First implementation (line 46): Uses `GpuProbeService` and `bindAndProbe` - **KEPT** ✅
  - Second implementation (line 530): Identical logic - **REMOVED** ✅
- The kept implementation correctly:
  - Returns true for CPU backend
  - Checks failed backends list
  - Creates Intent for GpuProbeService
  - Uses coroutines with timeout (5 seconds)
  - Calls bindAndProbe() method

**Preserved Critical Methods:**
- `isAHBInteropSupported()` at line 564 - Still intact ✅
- `checkPreviousCrash()` at line 163 - Still intact ✅

#### GpuProbeService.kt Verification
- Import `com.synapsenotes.ai.core.ai.IGpuProbeService` already present at line 8 ✅
- No changes needed

### Verification
All compilation errors resolved:
- ✅ All required imports now present
- ✅ Single active `probeBackend` method
- ✅ All critical methods preserved
- ✅ Correct method implementation kept (uses GpuProbeService + bindAndProbe pattern)

### Technical Notes
- The GpuProbeService pattern enables isolated backend probing in a separate service process
- This prevents crashes in the main app process during hardware capability detection
- The `bindAndProbe` coroutine-based approach handles service connection with proper lifecycle
- Death recipient and timeout mechanisms ensure robustness


## QCOM AHB Import Implementation (2026-02-01)

### Implementation Details
- Updated `AhbManager.cpp::importAHBToOpenCL()` to support both ARM and Qualcomm vendor paths
- Function now queries OpenCL platform extensions at runtime to determine which path to use
- Implements vendor-agnostic fallback logic

### ARM Path (Existing - Verified)
- Extension: `cl_arm_import_memory`
- Function: `clImportMemoryARM`
- Properties: `CL_IMPORT_TYPE_ANDROID_HARDWARE_BUFFER_ARM`
- Status: ✅ Fully implemented and tested

### QCOM Path (New - Speculative)
- Extension: `cl_qcom_android_hardware_buffer_interop`
- Function: `clCreateMemObjectFromAHardwareBufferQCOM` (speculative name)
- Fallback: Standard OpenCL approach mentioned but not implemented
- Status: ⚠️ Structural implementation complete, but untested on real hardware

### Key Design Decisions
1. **Try ARM First**: ARM path is attempted first as it's well-documented and proven
2. **Graceful Fallback**: If ARM fails, QCOM path is tried automatically
3. **Comprehensive Logging**: Each step logs its status (INFO/WARN/ERROR) for debugging
4. **No Hardcoded Vendor**: Uses runtime extension detection instead of vendor enum

### Known Limitations
- QCOM vendor function name is speculative (may need adjustment based on actual driver)
- Standard OpenCL approach for QCOM is mentioned but not implemented (requires vendor docs)
- No access to QCOM-specific documentation to verify exact API signature

### Testing Strategy
- Run on ARM Mali device: Should continue using ARM path successfully
- Run on QCOM Adreno device: Will attempt QCOM path and log detailed error if function not found
- Logs will reveal actual available extensions and guide future refinement

### Header Changes
- Added `clCreateMemObjectFromAHardwareBufferQCOM_fn` typedef to `AhbManager.h`
- Type signature matches expected pattern: (context, flags, ahb, error_code)

### Files Modified
- `app/src/main/cpp/AhbManager.h` - Added QCOM function pointer typedef
- `app/src/main/cpp/AhbManager.cpp` - Implemented dual-path import logic with runtime detection

