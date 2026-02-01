# GGML OpenCL Dynamic Loading - Technical Reference Card

## Quick Answer

| Question | Answer |
|----------|--------|
| **Does GGML support dynamic loading of OpenCL via dlopen?** | ✅ YES |
| **Is there a `GGML_OPENCL_USE_LOADER` flag?** | ❌ NO (uses `GGML_BACKEND_DL` instead) |
| **Default behavior** | Static linking (kernels embedded) |
| **Can be changed to dynamic?** | ✅ YES (`-DGGML_BACKEND_DL=ON`) |

---

## Dynamic Loading Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                    GGML Backend Registry System                   │
│                 (ggml-backend-reg.cpp, lines 160-623)            │
└──────────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
              dlopen (POSIX)     LoadLibrary (Windows)
              dlsym             GetProcAddress
                    │                   │
                    └─────────┬─────────┘
                              ▼
                   ┌──────────────────────┐
                   │ Search for library:  │
                   ├──────────────────────┤
                   │ libggml-opencl.so    │
                   │ libggml-opencl-*.so  │
                   │ ggml-opencl.dll      │
                   │ ggml-opencl-*.dll    │
                   └──────────────────────┘
                              │
                    ┌─────────┴─────────────────────┐
                    ▼                               ▼
            Load Symbols:                 Validate Backend:
         ├─ ggml_backend_score()      ├─ score_fn() > 0?
         └─ ggml_backend_init()       ├─ API version OK?
                                      └─ Return registration
                              │
                              ▼
                        Backend Available
```

---

## CMake Configuration

### Current Project Setup

**File:** `app/src/main/cpp/llama/ggml/CMakeLists.txt` (line 249)

```cmake
option(GGML_OPENCL OFF)  # Default: disabled
```

### To Enable OpenCL Static

```bash
cmake .. -DGGML_OPENCL=ON
```

**Result:**
- Compiles `ggml-opencl.cpp` into main library
- Links against: `${OpenCL_LIBRARIES}` (Android NDK)
- Includes: `${OpenCL_INCLUDE_DIRS}` (CL/cl.h)

### To Enable OpenCL Dynamic

```bash
cmake .. -DGGML_OPENCL=ON -DGGML_BACKEND_DL=ON
```

**Result:**
- Creates separate: `libggml-opencl.so`
- Loaded at runtime by: `ggml_backend_load_best()` or `ggml_backend_load()`

---

## Compilation Flags Applied

| Flag | Applied By | Effect |
|------|-----------|--------|
| `GGML_OPENCL_SOA_Q` | `ggml-opencl/CMakeLists.txt:17` | Structure-of-Arrays quantization format |
| `GGML_OPENCL_TARGET_VERSION=300` | `ggml-opencl/CMakeLists.txt:18` | Target OpenCL 3.0 API |
| `GGML_OPENCL_PROFILING` | `ggml-opencl/CMakeLists.txt:14` | Enable timing/profiling (if `-DGGML_OPENCL_PROFILING=ON`) |
| `GGML_OPENCL_EMBED_KERNELS` | `ggml-opencl/CMakeLists.txt:26` | Embed kernels as `.cl.h` headers (default: ON) |
| `GGML_OPENCL_USE_ADRENO_KERNELS` | `ggml-opencl/CMakeLists.txt:22` | Use Adreno-optimized kernels (recommended for Snapdragon) |

---

## Public API Symbols

### Header File
**Location:** `app/src/main/cpp/llama/ggml/include/ggml-opencl.h`

### Exported Functions

```c
// Core initialization
GGML_BACKEND_API ggml_backend_t ggml_backend_opencl_init(void);

// Device detection
GGML_BACKEND_API bool ggml_backend_is_opencl(ggml_backend_t backend);

// Buffer management
GGML_BACKEND_API ggml_backend_buffer_type_t ggml_backend_opencl_buffer_type(void);
GGML_BACKEND_API ggml_backend_buffer_type_t ggml_backend_opencl_host_buffer_type(void);

// Backend registration (for registry system)
GGML_BACKEND_API ggml_backend_reg_t ggml_backend_opencl_reg(void);
```

---

## Dynamic Loading Entry Points

### For dlopen'd Libraries

```c
// REQUIRED: Backend availability check
// Returns: 0 = unsupported, >0 = supported
int ggml_backend_score(void);

// REQUIRED: Backend initialization entry point
// Returns: ggml_backend_reg_t with function pointers
ggml_backend_reg_t ggml_backend_init(void);
```

### Loading Code (from ggml-backend-reg.cpp:262-305)

```cpp
ggml_backend_reg_t ggml_backend_registry::load_backend(const fs::path & path, bool silent) {
    dl_handle_ptr handle { dl_load_library(path) };
    if (!handle) {
        GGML_LOG_ERROR("failed to load %s: %s\n", path.c_str(), dl_error());
        return nullptr;
    }

    // Check if backend is supported on this system
    auto score_fn = (ggml_backend_score_t) dl_get_sym(handle.get(), "ggml_backend_score");
    if (score_fn && score_fn() == 0) {
        return nullptr;  // Backend not supported
    }

    // Load initialization function
    auto backend_init_fn = (ggml_backend_init_t) dl_get_sym(handle.get(), "ggml_backend_init");
    if (!backend_init_fn) {
        GGML_LOG_ERROR("failed to find ggml_backend_init in %s\n", path.c_str());
        return nullptr;
    }

    // Initialize backend
    ggml_backend_reg_t reg = backend_init_fn();
    if (!reg || reg->api_version != GGML_BACKEND_API_VERSION) {
        GGML_LOG_ERROR("incompatible API version\n");
        return nullptr;
    }

    register_backend(reg, std::move(handle));
    return reg;
}
```

---

## Search Paths for Dynamic Backends

### Execution Order

1. **`GGML_BACKEND_DIR`** (CMake cache variable)
   - Set during compilation: `-DGGML_BACKEND_DIR=/path/to/backends`

2. **Executable directory** (where the binary runs from)
   - Linux/Android: `get_executable_path()` via `/proc/self/exe`
   - macOS: `_NSGetExecutablePath()`
   - Windows: `GetModuleFileNameW()`

3. **Current working directory** (`.`)
   - `fs::current_path()`

4. **User-specified path** (at runtime)
   - `ggml_backend_load(path)` with explicit path

### Naming Convention

**Pattern:** `[lib]ggml-<backend>[-<variant>].[so|dll]`

Examples:
- `libggml-opencl.so` (base backend)
- `libggml-opencl-v8x-vulkan.so` (variant)
- `ggml-opencl.dll` (Windows)

### Variant Selection

The loader automatically picks the **highest scoring** implementation:

```cpp
static ggml_backend_reg_t ggml_backend_load_best(const char * name, bool silent, const char * user_search_path) {
    // Find all libggml-<name>-*.so files
    // Call ggml_backend_score() for each
    // Load the one with highest score
}
```

---

## Stub Implementation Template

If you need to create a minimal stub that disables OpenCL:

```cpp
// File: ggml-opencl-stub.cpp
#include "ggml-opencl.h"

extern "C" {
    // Entry points for dynamic loader
    int ggml_backend_score(void) {
        // Return 0 to indicate backend not available
        return 0;
    }

    ggml_backend_reg_t ggml_backend_init(void) {
        // Return nullptr to skip loading
        return nullptr;
    }

    // Public API (gracefully return null)
    ggml_backend_t ggml_backend_opencl_init(void) {
        return nullptr;
    }

    bool ggml_backend_is_opencl(ggml_backend_t backend) {
        (void)backend;  // unused
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

### Compile as Shared Library

```bash
gcc -shared -fPIC \
    -I./ggml/include \
    ggml-opencl-stub.cpp \
    -o libggml-opencl-stub.so

# Or for Android
arm-linux-android-g++ -shared -fPIC \
    -I./ggml/include \
    ggml-opencl-stub.cpp \
    -o libggml-opencl.so
```

---

## Android-Specific Details

### NDK OpenCL Support

**Available in Android NDK:**
- Header: `<CL/cl.h>`
- Stub library: `libOpenCL.so` (OpenCL ICD Loader)
- Vendor driver: Loaded at runtime (Adreno for Snapdragon)

### Adreno Optimizations

**With `-DGGML_OPENCL_USE_ADRENO_KERNELS=ON`:**

```cpp
// ggml-opencl-cpp lines 1056-1058, 1076-1078
if (backend_ctx->gpu_family != ADRENO ||
    backend_ctx->adreno_cl_compiler_version.newer_than_or_same(E031, 38, 11, 0) ||
    backend_ctx->adreno_cl_compiler_version.type == DX) {
    // Load Adreno-specific kernels
}
```

### Detected Adreno Generations

From `ggml-opencl.cpp:222-238`:

```cpp
ADRENO_GPU_GEN get_adreno_gpu_gen(const char *device_name) {
    if (strstr(device_name, "730") ||
        strstr(device_name, "740") ||
        strstr(device_name, "750")) {
        return ADRENO_GPU_GEN::A7X;  // Snapdragon 8 Gen 3
    }
    if (strstr(device_name, "830")) {
        return ADRENO_GPU_GEN::A8X;  // Snapdragon 8 Gen 2
    }
    if (strstr(device_name, "X1")) {
        return ADRENO_GPU_GEN::X1E;  // X1 Elite
    }
}
```

---

## File Structure

```
app/src/main/cpp/llama/ggml/
├── CMakeLists.txt
│   └── Line 249: option(GGML_OPENCL OFF)
│
├── include/
│   └── ggml-opencl.h ..................... Public API
│
└── src/
    ├── ggml-backend-reg.cpp .............. Dynamic loading mechanism
    │   ├── Lines 160-174: Platform loaders (dlopen/LoadLibrary)
    │   ├── Lines 262-305: load_backend() function
    │   └── Lines 607-632: ggml_backend_load_all_from_path()
    │
    └── ggml-opencl/
        ├── CMakeLists.txt ............... Kernel embedding, compilation flags
        ├── ggml-opencl.cpp .............. 448KB implementation
        │   ├── Lines 1056-1078: Adreno kernel skipping for old compilers
        │   ├── Lines 222-238: GPU detection
        │   └── Lines 240-264: Compiler version parsing
        │
        └── kernels/ ..................... 100+ .cl files (embedded by default)
```

---

## Build Configuration Decision Tree

```
Do you want OpenCL on Android?
│
├─ NO (keep current setup)
│   └─ Leave -DGGML_OPENCL=OFF (default)
│
└─ YES (add GPU acceleration)
    │
    ├─ Option A: Static (embedded kernels)
    │   └─ cmake .. -DGGML_OPENCL=ON \
    │       -DGGML_OPENCL_EMBED_KERNELS=ON \
    │       -DGGML_OPENCL_USE_ADRENO_KERNELS=ON
    │       Result: Single libggml.so with OpenCL built-in
    │
    └─ Option B: Dynamic (separate .so)
        └─ cmake .. -DGGML_OPENCL=ON \
            -DGGML_BACKEND_DL=ON \
            -DGGML_OPENCL_EMBED_KERNELS=ON
            Result: libggml-opencl.so loaded at runtime
            Benefit: Can omit on devices without OpenCL
```

---

## Conclusion

1. **Dynamic loading IS supported** - uses generic `GGML_BACKEND_DL` mechanism
2. **No special OpenCL flag** - same system as CUDA, Metal, Vulkan, etc.
3. **Entry points required:** `ggml_backend_score()` + `ggml_backend_init()`
4. **For Snapdragon:** Use `GGML_OPENCL_USE_ADRENO_KERNELS=ON`
5. **Kernel embedding:** Default behavior (no external .cl files)
6. **Your choice:** Keep Vulkan (current) or add OpenCL (optional for multi-GPU)
