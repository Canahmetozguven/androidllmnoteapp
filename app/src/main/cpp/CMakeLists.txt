cmake_minimum_required(VERSION 3.22.1)

project("llm_notes_cpp")

find_library(
        log-lib
        log
)

# Enable Vulkan for GPU acceleration
set(GGML_VULKAN ON CACHE BOOL "Enable Vulkan Backend" FORCE)
set(GGML_VULKAN ON) # Force non-cache variable too

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
set(Vulkan_FOUND ON CACHE BOOL "Vulkan Found" FORCE) # Try to force it if find_package fails

# Force static linking of backends (no DL)
set(GGML_BACKEND_DL OFF CACHE BOOL "Backend DL" FORCE)

# Force GGML_USE_VULKAN globally to ensure it's picked up by ggml-backend-reg.cpp
# add_compile_definitions(GGML_USE_VULKAN) # Let build system handle this if DL is OFF
# add_definitions(-DGGML_USE_VULKAN)

# Disable OpenCL to avoid linker errors on x86_64 (incompatible library) and because we are using Vulkan
set(GGML_OPENCL OFF CACHE BOOL "Disable OpenCL" FORCE)

# set(CMAKE_MAKE_PROGRAM "C:/Users/canahmet/AppData/Local/Android/Sdk/cmake/3.22.1/bin/ninja.exe" CACHE FILEPATH "Path to Ninja" FORCE)

# set(LLAMA_BUILD_COMMON ON)
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
