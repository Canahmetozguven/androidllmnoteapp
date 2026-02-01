# Android C++ GPU Vendor Detection Research

## Goal
Detect GPU vendor (Adreno vs Mali) reliably on Android **without** initializing a heavy Vulkan context, which might crash on buggy drivers.

## Method 1: System Properties (Safest & Fastest)
Android exposes hardware information via system properties. We can access these in C++ using `<sys/system_properties.h>`.

### Key Properties to Check
1.  **`ro.hardware.egl`**: Often contains the GPU vendor name (e.g., `adreno`, `mali`).
2.  **`ro.hardware.vulkan`**: Sometimes present, specific to Vulkan driver.
3.  **`ro.board.platform`**: The SoC name (e.g., `kalama` for SD 8 Gen 2, `exynos`).
4.  **`ro.hardware`**: Generic hardware string.

### Implementation Details
Use `__system_property_get` from the NDK:

```cpp
#include <sys/system_properties.h>
#include <string>

std::string getSystemProperty(const char* key) {
    char value[PROP_VALUE_MAX];
    if (__system_property_get(key, value) > 0) {
        return std::string(value);
    }
    return "";
}

bool isAdreno() {
    std::string egl = getSystemProperty("ro.hardware.egl");
    if (egl.find("adreno") != std::string::npos) return true;
    
    std::string platform = getSystemProperty("ro.board.platform");
    // Check known Qualcomm platforms if needed
    return false;
}
```

## Method 2: EGL Query (Safe, Lightweight)
Initializing a dummy EGL display is much lighter than a full Vulkan instance.

### Steps:
1.  `eglGetDisplay(EGL_DEFAULT_DISPLAY)`
2.  `eglInitialize(...)`
3.  `eglQueryString(display, EGL_VENDOR)` -> Returns "Qualcomm", "ARM", etc.
4.  `eglTerminate(display)`

### Pros/Cons
-   **Pros**: More accurate than system properties (reads actual driver info).
-   **Cons**: Still touches the driver stack. If the driver is *extremely* broken, `eglInitialize` could hang or crash (rare).

## Method 3: Parsing /proc/cpuinfo (Hardware Fallback)
Reading `/proc/cpuinfo` can reveal the CPU implementer (0x51 = Qualcomm, 0x41 = ARM), which correlates strongly with the GPU.

-   **Qualcomm CPU** -> Almost certainly Adreno GPU.
-   **Samsung/Google Tensor** -> Usually Mali GPU.
-   **MediaTek** -> Mali or PowerVR.

## Recommendation for Strategy
1.  **Primary**: Check **System Properties** (`ro.hardware.egl`, `ro.board.platform`). It's zero-risk (no driver code executed).
2.  **Secondary**: If properties are ambiguous, try a lightweight **EGL Query**.
3.  **Fallback**: Default to OpenCL for Qualcomm SoCs (detected via properties), Vulkan for others.
