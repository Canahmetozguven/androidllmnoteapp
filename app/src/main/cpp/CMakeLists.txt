cmake_minimum_required(VERSION 3.22.1)

project("llm_notes_cpp")

find_library(
        log-lib
        log
)

# Enable Vulkan for GPU acceleration - Default ON but can be overridden
option(GGML_VULKAN "Enable Vulkan Backend" ON)
option(LLAMA_VULKAN "Enable Vulkan Backend" ON)

if (GGML_VULKAN)
    # Include external Vulkan headers downloaded from Khronos
    include_directories(SYSTEM external)

    # Use absolute path to host toolchain for cross-compilation
    set(GGML_VULKAN_SHADERS_GEN_TOOLCHAIN "${CMAKE_CURRENT_SOURCE_DIR}/host-toolchain.cmake" CACHE FILEPATH "Host toolchain for vulkan-shaders-gen" FORCE)

    # FORCE VULKAN: Point to NDK glslc
    set(Vulkan_GLSLC_EXECUTABLE "$ENV{ANDROID_NDK_HOME}/shader-tools/linux-x86_64/glslc")
    # Fallback if env var not set, assume standard location relative to CMAKE_ANDROID_NDK
    if (NOT EXISTS "${Vulkan_GLSLC_EXECUTABLE}")
        file(GLOB NDK_GLSLC_PATHS "${CMAKE_ANDROID_NDK}/shader-tools/*/glslc")
        if (NDK_GLSLC_PATHS)
            list(GET NDK_GLSLC_PATHS 0 Vulkan_GLSLC_EXECUTABLE)
        endif()
    endif()

    message(STATUS "Forcing Vulkan_GLSLC_EXECUTABLE to ${Vulkan_GLSLC_EXECUTABLE}")
    set(Vulkan_GLSLC_EXECUTABLE "${Vulkan_GLSLC_EXECUTABLE}" CACHE FILEPATH "Path to glslc" FORCE)
    set(Vulkan_FOUND ON CACHE BOOL "Vulkan Found" FORCE) 
endif()

# Force static linking of backends (no DL)
set(GGML_BACKEND_DL OFF CACHE BOOL "Backend DL" FORCE)

# Enable OpenCL for Adreno stability - Default ON but can be overridden
option(GGML_OPENCL "Enable OpenCL Backend" ON)
option(GGML_OPENCL_USE_ADRENO_KERNELS "Use Adreno optimized kernels" ON)
option(GGML_OPENCL_EMBED_KERNELS "Embed OpenCL kernels" ON)

# Find OpenCL on Android (often in /system/lib64/libOpenCL.so or /vendor/lib64/libOpenCL.so)
# We use a stub or direct link for build time.
find_library(OpenCL_LIBRARY OpenCL)
if (NOT OpenCL_LIBRARY)
    # Common locations on Android devices
    find_library(OpenCL_LIBRARY NAMES OpenCL PATHS /system/lib64 /system/lib /vendor/lib64 /vendor/lib)
endif()

# Fallback: Build OpenCL Stub if system library not found (e.g. CI/WSL environment)
if (NOT OpenCL_LIBRARY)
    message(STATUS "OpenCL not found on host. Building stub library from opencl_stub.c")
    add_library(OpenCL_stub SHARED opencl_stub.c)
    # Ensure linker sees it as -lOpenCL
    set_target_properties(OpenCL_stub PROPERTIES OUTPUT_NAME "OpenCL")
    set(OpenCL_LIBRARY OpenCL_stub)
    set(OpenCL_LIBRARIES OpenCL_stub)
    
    set(OpenCL_FOUND ON)
endif()

# Register custom FindOpenCL.cmake to handle submodule dependencies
list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
set(OPENCL_HEADERS_PATH "${CMAKE_CURRENT_SOURCE_DIR}/external" CACHE PATH "OpenCL Headers" FORCE)

if (OpenCL_FOUND)
    message(STATUS "OpenCL Enabled. Library: ${OpenCL_LIBRARY}")
    add_definitions(-DGGML_USE_OPENCL)
    set(GGML_OPENCL ON CACHE BOOL "Enable OpenCL" FORCE)
    
    # Headers are usually in common locations or under llama/ggml/include/CL
    # We downloaded headers to external/CL
    include_directories(SYSTEM external)
endif()

# Disable OpenMP if needed (sometimes conflicts with complex GPU setups), but keep it for now.

add_subdirectory(llama)

if (GGML_OPENCL)
    # Fix linking for OpenCL backend which llama.cpp might miss in static builds
    # Verify target exists first
    if (TARGET ggml-opencl)
        message(STATUS "Manually linking ggml-opencl to ggml (Fixing undefined symbol: ggml_backend_opencl_reg)")
        target_link_libraries(ggml PUBLIC ggml-opencl)
    endif()
endif()

add_library(
        llm_notes_cpp
        SHARED
        native-lib.cpp
        AhbManager.cpp
)

target_include_directories(llm_notes_cpp PRIVATE llama/include)

target_link_libraries(
        llm_notes_cpp
        ${log-lib}
        llama
        android
        vulkan
        ${OpenCL_LIBRARY}
)

# Enforce 16KB page size alignment for Android 15+ compatibility
target_link_options(llm_notes_cpp PRIVATE "-Wl,-z,max-page-size=16384")

# Enable Vulkan Android Platform extensions (required for AHB)
target_compile_definitions(llm_notes_cpp PRIVATE VK_USE_PLATFORM_ANDROID_KHR)

# ARM64-specific optimizations (CPU Fallback)
if(${CMAKE_ANDROID_ARCH_ABI} MATCHES "arm64-v8a")
    # Target armv8.2-a+dotprod for faster CPU inference on modern Android
    # This is safe for Snapdragon 845+, Pixel 3+, Exynos 9810+
    target_compile_options(llm_notes_cpp PRIVATE "-march=armv8.2-a+dotprod")
    message(STATUS "Applied ARM64 optimizations: -march=armv8.2-a+dotprod")
endif()
