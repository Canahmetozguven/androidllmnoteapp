# GGML OpenCL Dynamic Loading Analysis

## Summary

**YES**, GGML/llama.cpp **DOES support dynamic loading via `dlopen`** for the OpenCL backend, but with important caveats for your Android use case.

---

## 1. How OpenCL Backend is Built

### Configuration Options

Located in `app/src/main/cpp/llama/ggml/CMakeLists.txt` (lines 249-254):

```cmake
option(GGML_OPENCL                          "ggml: use OpenCL"                                OFF)
option(GGML_OPENCL_PROFILING                "ggml: use OpenCL profiling (increases overhead)" OFF)
option(GGML_OPENCL_EMBED_KERNELS            "ggml: embed kernels"                             ON)
option(GGML_OPENCL_USE_ADRENO_KERNELS       "ggml: use optimized kernels for Adreno"          ON)
set   (GGML_OPENCL_TARGET_VERSION "300" CACHE STRING
                                            "gmml: OpenCL API version to target")
```

### Key Points:

- **No explicit `GGML_OPENCL_USE_LOADER` option** – Unlike some backends, OpenCL doesn't have a dedicated dynamic loader flag
- **`GGML_OPENCL_EMBED_KERNELS` (default ON)** – OpenCL kernels are embedded as compiled headers at build time
- **`GGML_OPENCL_USE_ADRENO_KERNELS` (default ON)** – Special optimizations for Adreno GPUs (Snapdragon)
- **`GGML_OPENCL_TARGET_VERSION`** – OpenCL API version to target (currently 300 = OpenCL 3.0)

---

## 2. Compile Flags Applied

From `app/src/main/cpp/llama/ggml/src/ggml-opencl/CMakeLists.txt`:

```cpp
add_compile_definitions(GGML_OPENCL_SOA_Q)
add_compile_definitions(GGML_OPENCL_TARGET_VERSION=${GGML_OPENCL_TARGET_VERSION})

// Optional compile flags:
GGML_OPENCL_PROFILING      // Enable profiling
GGML_OPENCL_EMBED_KERNELS  // Embed kernels as .cl.h headers
GGML_OPENCL_USE_ADRENO_KERNELS  // Use Adreno optimized kernels
```

---

## 3. Public API (ggml-opencl.h)

Located at: `app/src/main/cpp/llama/ggml/include/ggml-opencl.h`

**Required Symbols (if creating stubs):**

```c
// Backend initialization
GGML_BACKEND_API ggml_backend_t ggml_backend_opencl_init(void);
GGML_BACKEND_API bool ggml_backend_is_opencl(ggml_backend_t backend);

// Buffer management
GGML_BACKEND_API ggml_backend_buffer_type_t ggml_backend_opencl_buffer_type(void);
GGML_BACKEND_API ggml_backend_buffer_type_t ggml_backend_opencl_host_buffer_type(void);

// Backend registration
GGML_BACKEND_API ggml_backend_reg_t ggml_backend_opencl_reg(void);
```

---

## 4. Dynamic Loading Mechanism

### Backend Registry System

The dynamic loading is managed through `ggml-backend-reg.cpp` (lines 160-623):

#### Platform-Specific Loaders:

**POSIX (Linux/Android):**
```cpp
static void * dl_load_library(const fs::path & path) {
    dl_handle * handle = dlopen(path.string().c_str(), RTLD_NOW | RTLD_LOCAL);
    return handle;
}

static void * dl_get_sym(dl_handle * handle, const char * name) {
    return dlsym(handle, name);
}
```

**Windows:**
```cpp
static dl_handle * dl_load_library(const fs::path & path) {
    HMODULE handle = LoadLibraryW(path.wstring().c_str());
    return handle;
}

static void * dl_get_sym(dl_handle * handle, const char * name) {
    void * p = (void *) GetProcAddress(handle, name);
    return p;
}
```

### Loading Strategy

The `ggml_backend_load_best()` function (lines 522-601) searches for backend libraries:

**File Naming Convention:**
- **Linux/Android:** `libggml-<backend>-*.so` or `libggml-<backend>.so`
- **Windows:** `ggml-<backend>-*.dll` or `ggml-<backend>.dll`

**Search Paths (in order):**
1. `GGML_BACKEND_DIR` (CMake variable)
2. Executable directory
3. Current working directory
4. Custom user path (if provided)

### Required Symbols When Loading Dynamically

```cpp
// Symbol 1: Backend version/compatibility check
typedef int (*ggml_backend_score_t)();
auto score_fn = (ggml_backend_score_t) dl_get_sym(handle.get(), "ggml_backend_score");

// Symbol 2: Actual backend initialization
typedef ggml_backend_reg_t (*ggml_backend_init_t)();
auto backend_init_fn = (ggml_backend_init_t) dl_get_sym(handle.get(), "ggml_backend_init");
```

**Expected behavior:**
- `ggml_backend_score()` returns 0 if backend is not supported, >0 otherwise
- `ggml_backend_init()` returns a `ggml_backend_reg_t` struct with API version compatibility check

---

## 5. Current Build Configuration (Your Project)

### App CMakeLists.txt

From `app/src/main/cpp/CMakeLists.txt`:

```cmake
add_subdirectory(llama)
```

### llama.cpp CMakeLists.txt

**General approach:**
- Adds `ggml` subdirectory (line 206)
- Inherits all GGML backend configurations from parent

### How OpenCL is Handled:

1. **If GGML_OPENCL is ON:**
   - Links against OpenCL library (found via `find_package(OpenCL)`)
   - Compiles OpenCL kernels into `.cl.h` headers
   - Creates `ggml-opencl` backend library

2. **If GGML_OPENCL is OFF (current default):**
   - OpenCL backend is NOT built
   - `ggml_backend_opencl_init()` is not available

---

## 6. How to Enable OpenCL (Options)

### Option A: Static Build (Current Setup)

Enable in your build script:
```bash
cmake .. -DGGML_OPENCL=ON
```

This will:
- Link OpenCL statically into the main library
- Embed all kernels (default: `GGML_OPENCL_EMBED_KERNELS=ON`)
- No need for dynamic loading

### Option B: Dynamic Backend Loading

```bash
cmake .. -DGGML_OPENCL=ON -DGGML_BACKEND_DL=ON
```

This will:
- Build OpenCL as a separate shared library: `libggml-opencl.so` (Android)
- Load it at runtime via `dlopen`
- Can be omitted from final bundle and loaded conditionally

### Option C: Stub/Fallback Approach

If you want to disable OpenCL but provide a stub:

1. Create `libggml-opencl-stub.so` with:
   ```cpp
   #include "ggml-opencl.h"
   
   ggml_backend_t ggml_backend_opencl_init(void) { return nullptr; }
   bool ggml_backend_is_opencl(ggml_backend_t backend) { return false; }
   ggml_backend_buffer_type_t ggml_backend_opencl_buffer_type(void) { return nullptr; }
   ggml_backend_buffer_type_t ggml_backend_opencl_host_buffer_type(void) { return nullptr; }
   ggml_backend_reg_t ggml_backend_opencl_reg(void) { return nullptr; }
   ```

2. Return score of 0 to disable:
   ```cpp
   int ggml_backend_score(void) { return 0; }  // Backend not supported
   ```

---

## 7. Symbols Required for Creating a Stub

If you need to create a minimal OpenCL stub library:

### Required Exports

```c
// Public API symbols (from ggml-opencl.h)
ggml_backend_t ggml_backend_opencl_init(void);
bool ggml_backend_is_opencl(ggml_backend_t backend);
ggml_backend_buffer_type_t ggml_backend_opencl_buffer_type(void);
ggml_backend_buffer_type_t ggml_backend_opencl_host_buffer_type(void);
ggml_backend_reg_t ggml_backend_opencl_reg(void);

// Backend loader interface symbols (required for dynamic loading)
ggml_backend_reg_t ggml_backend_init(void);  // Entry point for dlopen'd libraries
int ggml_backend_score(void);                 // Version/compatibility check
```

### Minimal Stub Implementation

```cpp
#include "ggml-opencl.h"

extern "C" {
    // Return 0 to indicate backend not available
    int ggml_backend_score(void) {
        return 0;  // Not supported
    }

    ggml_backend_reg_t ggml_backend_init(void) {
        return nullptr;  // No backend available
    }

    // Public API stubs (gracefully handle null returns)
    ggml_backend_t ggml_backend_opencl_init(void) {
        return nullptr;
    }

    bool ggml_backend_is_opencl(ggml_backend_t backend) {
        (void)backend;
        return false;
    }

    ggml_backend_buffer_type_t ggml_backend_opencl_buffer_type(void) {
        return nullptr;
    }

    ggml_backend_buffer_type_t ggml_backend_opencl_host_buffer_type(void) {
        return nullptr;
    }

    ggml_backend_reg_t ggml_backend_opencl_reg(void) {
        return nullptr;
    }
}
```

---

## 8. Android-Specific Considerations

### Why OpenCL Matters on Android

- **Snapdragon GPUs (Adreno):** Best performance with OpenCL
- **Vulkan:** Your current accelerator, but OpenCL is more mature for this hardware
- **Dynamic Loading:** Can selectively enable/disable at runtime

### Challenges

1. **OpenCL ICD Loader:** Android typically has Adreno OpenCL driver built-in
   - No separate ICD manager like desktop Linux
   - Driver is loaded directly by vendor

2. **Kernel Compilation:** OpenCL kernels compile at runtime in `llama.cpp`
   - Can be slow on first inference
   - Embedded kernels (default) avoid this by including pre-compiled headers

3. **Symbol Availability:** The `<CL/cl.h>` header must be available
   - Included via Android NDK
   - May need to link against Adreno OpenCL stub libraries

---

## 9. Key File Locations

| Component | Path |
|-----------|------|
| Main OpenCL Implementation | `app/src/main/cpp/llama/ggml/src/ggml-opencl/ggml-opencl.cpp` |
| Public API Header | `app/src/main/cpp/llama/ggml/include/ggml-opencl.h` |
| Build Configuration | `app/src/main/cpp/llama/ggml/src/ggml-opencl/CMakeLists.txt` |
| Backend Registry | `app/src/main/cpp/llama/ggml/src/ggml-backend-reg.cpp` |
| Parent CMakeLists | `app/src/main/cpp/llama/ggml/CMakeLists.txt` |
| OpenCL Kernels | `app/src/main/cpp/llama/ggml/src/ggml-opencl/kernels/` |

---

## 10. Quick Decision Tree

**Do you need OpenCL on Android?**

- ✅ **YES, enable GPU:** Use Vulkan (current, recommended) or add OpenCL support
- ❓ **MAYBE, add later:** Use dynamic loading (`GGML_BACKEND_DL=ON`)
- ❌ **NO, CPU only:** Leave disabled (default), use Vulkan for GPU

**To add OpenCL support:**

```bash
# In your build_vulkan.sh or CMake:
cmake .. \
  -DGGML_OPENCL=ON \
  -DGGML_OPENCL_EMBED_KERNELS=ON \  # Recommended for Android
  -DGGML_OPENCL_USE_ADRENO_KERNELS=ON
```

---

## Conclusion

1. **Dynamic loading IS supported** via `dlopen` for all backends including OpenCL
2. **No special `GGML_OPENCL_USE_LOADER` flag** – uses generic backend registry system
3. **For Android with Snapdragon:** Consider enabling Adreno-optimized kernels
4. **Symbol requirements** for stubs are minimal (5 public functions + 2 internal loader symbols)
5. **Default behavior:** Kernels are embedded (no external `.cl` files needed)
