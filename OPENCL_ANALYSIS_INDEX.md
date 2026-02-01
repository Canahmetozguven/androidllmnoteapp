# GGML OpenCL Dynamic Loading - Complete Analysis Index

## 📊 Analysis Overview

This directory contains a comprehensive analysis of how GGML (ggml.org) implements OpenCL dynamic loading support for your Android LLM note app project.

**Key Finding:** ✅ **YES** - GGML fully supports dynamic loading of OpenCL via `dlopen()`, but uses a generic backend registry system rather than an OpenCL-specific flag.

---

## 📄 Documentation Files

### 1. **GGML_OPENCL_ANALYSIS.md** (10 sections, ~10 KB)
**Comprehensive technical analysis with code examples**

Contents:
- How OpenCL backend is built (CMakeLists configuration)
- Compile flags applied
- Public API symbols
- Dynamic loading mechanism details
- Current build configuration in your project
- How to enable OpenCL
- Creating stub/fallback implementations
- Required symbols for stubs
- Android-specific considerations
- Key file locations and decision tree

**Best for:** Deep understanding, troubleshooting, implementation planning

---

### 2. **GGML_OPENCL_REFERENCE.md** (Technical reference card, ~11 KB)
**Quick reference with architecture diagrams and CMake examples**

Contents:
- Quick answer table
- Dynamic loading architecture diagram
- CMake configuration examples
- Compilation flags table
- Public API symbols list
- Dynamic loading entry points
- Search paths for backends
- Stub implementation template
- Android NDK support details
- File structure overview
- Build configuration decision tree

**Best for:** Implementation, quick lookup, CMake modifications

---

### 3. **OPENCL_QUICK_REFERENCE.txt** (Quick lookup, ~11 KB)
**Terminal-friendly quick reference for frequently asked questions**

Contents:
- Q&A format for common questions
- Required symbols for stubs/fallbacks
- Compile flags table
- Build options with commands and results
- File locations
- Dynamic loading entry points explained
- Search paths and naming conventions
- Android-specific notes
- When to use each approach
- Debugging commands
- Next steps

**Best for:** Terminal reference, CI/CD documentation, quick answers

---

## 🎯 Quick Answers

| Question | Answer |
|----------|--------|
| **Does GGML support dlopen for OpenCL?** | ✅ YES (via `GGML_BACKEND_DL`) |
| **Is there `GGML_OPENCL_USE_LOADER` flag?** | ❌ NO (uses generic system) |
| **Default behavior?** | Static linking (kernels embedded) |
| **Can change to dynamic?** | ✅ YES (`-DGGML_BACKEND_DL=ON`) |
| **Symbols needed for stubs?** | 7 C functions (2 entry points + 5 public API) |

---

## 🔧 CMake Configuration Quick Start

### Current (Vulkan only)
```bash
cmake .. (no OpenCL flags)
```

### Add OpenCL Static
```bash
cmake .. -DGGML_OPENCL=ON -DGGML_OPENCL_USE_ADRENO_KERNELS=ON
```

### Add OpenCL Dynamic
```bash
cmake .. -DGGML_OPENCL=ON -DGGML_BACKEND_DL=ON
```

---

## 📍 Key Files in Your Project

| File | Purpose | Lines |
|------|---------|-------|
| `app/src/main/cpp/llama/ggml/src/ggml-opencl/ggml-opencl.cpp` | OpenCL implementation | 448 KB |
| `app/src/main/cpp/llama/ggml/include/ggml-opencl.h` | Public API header | 27 |
| `app/src/main/cpp/llama/ggml/src/ggml-opencl/CMakeLists.txt` | Build config | 141 |
| `app/src/main/cpp/llama/ggml/src/ggml-backend-reg.cpp` | Dynamic loader | 160-623 |
| `app/src/main/cpp/llama/ggml/CMakeLists.txt` | Main build config | 249-254 |

---

## 🧬 Required Symbols (for Stubs)

### Entry Points (for dynamic loading)
```c
int ggml_backend_score(void);                  // Return 0 = unsupported
ggml_backend_reg_t ggml_backend_init(void);    // Return nullptr
```

### Public API (for link-time or dlsym)
```c
ggml_backend_t ggml_backend_opencl_init(void);
bool ggml_backend_is_opencl(ggml_backend_t);
ggml_backend_buffer_type_t ggml_backend_opencl_buffer_type(void);
ggml_backend_buffer_type_t ggml_backend_opencl_host_buffer_type(void);
ggml_backend_reg_t ggml_backend_opencl_reg(void);
```

---

## 🎮 For Snapdragon/Adreno GPUs

Enable Adreno optimizations:
```bash
-DGGML_OPENCL_USE_ADRENO_KERNELS=ON
```

Supported Adreno generations:
- **A7X:** Adreno 730, 740, 750 (Snapdragon 8 Gen 3)
- **A8X:** Adreno 830 (Snapdragon 8 Gen 2)
- **X1E:** Adreno X1 Elite

---

## 💡 Decision Guide

### Keep Vulkan (Current) ✓
- Already working
- Optimized for your architecture
- No additional configuration needed

### Add OpenCL Static
- If: Multi-GPU support needed
- If: Adreno optimization desired
- Trade-off: Adds binary size, compilation time

### Add OpenCL Dynamic
- If: Want runtime GPU selection
- If: Reduce binary size for CPU variants
- Benefit: Load only when needed

### Create Stub/Fallback
- If: Code references OpenCL but should skip gracefully
- If: Want transparent CPU-only fallback
- Benefit: Code compatibility without implementation

---

## 📂 How Dynamic Loading Works

```
1. System calls: ggml_backend_load_best("opencl", ...)
         ↓
2. Searches for: libggml-opencl.so
         ↓
3. Calls: dlopen(path, RTLD_NOW | RTLD_LOCAL)
         ↓
4. Resolves: ggml_backend_score()  → Check version
             ggml_backend_init()   → Get registration
         ↓
5. Returns: Backend registered or skipped if unsupported
```

**Search paths** (in order):
1. `${GGML_BACKEND_DIR}`
2. Executable directory
3. Current working directory
4. User-specified path

---

## 🔍 Debugging

### Check if OpenCL enabled:
```bash
grep "GGML_OPENCL" build/CMakeCache.txt
```

### Find kernel files:
```bash
find . -name "*.cl" | head -20
```

### Verify symbol exports:
```bash
nm -D libggml-opencl.so | grep ggml_backend
```

### Force backend loading:
```c
ggml_backend_load("/path/to/libggml-opencl.so")
```

---

## 📚 Related Documentation

- [llama.cpp README](./app/src/main/cpp/llama/README.md)
- [Native build README](./app/src/main/cpp/README.md)
- [GGML Backend Documentation](https://github.com/ggml-org/ggml)
- [OpenCL Specification](https://www.khronos.org/opencl/)

---

## ✨ Summary

1. **GGML supports dynamic loading** via generic backend registry
2. **No OpenCL-specific loader flag** - uses `GGML_BACKEND_DL` for all backends
3. **Your project uses Vulkan** - adding OpenCL is optional
4. **For Snapdragon:** Enable Adreno optimizations flag
5. **Creating stubs:** Only 7 C functions needed
6. **Default behavior:** Kernels embedded (no external files)
7. **Current setup is optimal** - only change if you have specific needs

---

## 🚀 Next Steps

1. Decide: Keep Vulkan or add OpenCL?
2. If adding: Static vs Dynamic?
3. For Snapdragon: Enable `GGML_OPENCL_USE_ADRENO_KERNELS`
4. If stubbing: Implement 7 required C functions
5. Test: Verify backend loads or gracefully skips
6. Document: Update build scripts with new flags

---

**Analysis Date:** February 1, 2025  
**Files Analyzed:** 5 CMakeLists.txt + 3 source files + 1 header  
**Total Code Examined:** ~500 KB  
**Documentation Generated:** 3 files (~33 KB)
