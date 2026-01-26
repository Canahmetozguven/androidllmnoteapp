cmake_minimum_required(VERSION 3.22.1)

project("llm_notes_cpp")

find_library(
        log-lib
        log
)

# Enable Vulkan for GPU acceleration - Default ON but can be overridden
option(GGML_VULKAN "Enable Vulkan Backend" ON)
option(LLAMA_VULKAN "Enable Vulkan Backend" ON)

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

if (OpenCL_LIBRARY)
    set(OpenCL_FOUND ON)
    set(OpenCL_LIBRARIES ${OpenCL_LIBRARY})
    # Headers are usually in common locations or under llama/ggml/include/CL
    include_directories(SYSTEM llama/ggml/src/ggml-opencl)
endif()

# Disable OpenMP if needed (sometimes conflicts with complex GPU setups), but keep it for now.

add_subdirectory(llama)

add_library(
        llm_notes_cpp
        SHARED
        native-lib.cpp
)

target_include_directories(llm_notes_cpp PRIVATE llama/include)

target_link_libraries(
        llm_notes_cpp
        ${log-lib}
        llama
)
