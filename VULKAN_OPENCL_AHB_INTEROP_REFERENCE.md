# Vulkan and OpenCL Interoperability on Android using Android Hardware Buffers (AHB)

## Summary

This document provides concrete C++ code examples and references for implementing **Vulkan and OpenCL interoperability on Android using Android Hardware Buffers (AHB)**. This is a production-ready reference based on official Khronos samples and community implementations.

---

## 1. Primary Reference: Khronos Vulkan Samples

### Repository
- **URL**: https://github.com/KhronosGroup/Vulkan-Samples
- **Sample**: `samples/extensions/open_cl_interop_arm/`
- **Documentation**: https://docs.vulkan.org/samples/latest/samples/extensions/open_cl_interop_arm/README.html

### Key Files
1. **open_cl_interop_arm.cpp** - Main implementation (~650 lines)
2. **open_cl_interop_arm.h** - Header with class structure
3. **procedural_texture.cl** - OpenCL kernel for texture generation
4. **CMakeLists.txt** - Build configuration

### Supported Extensions Used
1. **VK_ANDROID_external_memory_android_hardware_buffer** - Vulkan extension for AHB support
2. **cl_arm_import_memory** - ARM OpenCL extension for importing AHB
3. **cl_arm_import_memory_android_hardware_buffer** - Specific Android variant

---

## 2. Complete Code Example: Vulkan Setup

### Creating Shared Texture with AHB in Vulkan

```cpp
void OpenCLInteropArm::prepare_shared_resources()
{
    shared_texture.width  = 256;
    shared_texture.height = 256;
    shared_texture.depth  = 1;

    auto device_handle = get_device().get_handle();

    // Step 1: Create VkImage with external memory create info
    VkExternalMemoryImageCreateInfo external_memory_image_create_info;
    external_memory_image_create_info.sType       = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO;
    external_memory_image_create_info.pNext       = nullptr;
    external_memory_image_create_info.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;

    VkImageCreateInfo image_create_info = vkb::initializers::image_create_info();
    image_create_info.pNext             = &external_memory_image_create_info;
    image_create_info.imageType         = VK_IMAGE_TYPE_2D;
    image_create_info.format            = VK_FORMAT_R8G8B8A8_UNORM;
    image_create_info.mipLevels         = 1;
    image_create_info.arrayLayers       = 1;
    image_create_info.samples           = VK_SAMPLE_COUNT_1_BIT;
    image_create_info.tiling            = VK_IMAGE_TILING_LINEAR;  // Important for AHB
    image_create_info.sharingMode       = VK_SHARING_MODE_EXCLUSIVE;
    image_create_info.initialLayout     = VK_IMAGE_LAYOUT_UNDEFINED;
    image_create_info.extent            = {shared_texture.width, shared_texture.height, shared_texture.depth};
    image_create_info.usage             = VK_IMAGE_USAGE_SAMPLED_BIT;
    
    VK_CHECK(vkCreateImage(device_handle, &image_create_info, nullptr, &shared_texture.image));

    // Step 2: Setup dedicated memory allocation
    VkMemoryDedicatedAllocateInfo dedicated_allocate_info;
    dedicated_allocate_info.sType  = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO;
    dedicated_allocate_info.pNext  = nullptr;
    dedicated_allocate_info.buffer = VK_NULL_HANDLE;
    dedicated_allocate_info.image  = shared_texture.image;

    VkMemoryRequirements memory_requirements{};
    vkGetImageMemoryRequirements(device_handle, shared_texture.image, &memory_requirements);

    // Step 3: Create exportable memory allocation
    VkExportMemoryAllocateInfo export_memory_allocate_Info;
    export_memory_allocate_Info.sType       = VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO;
    export_memory_allocate_Info.pNext       = &dedicated_allocate_info;
    export_memory_allocate_Info.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;

    VkMemoryAllocateInfo memory_allocate_info = vkb::initializers::memory_allocate_info();
    memory_allocate_info.pNext                = &export_memory_allocate_Info;
    memory_allocate_info.allocationSize       = 0;  // AHB allocation size is 0
    memory_allocate_info.memoryTypeIndex      = get_device().get_gpu().get_memory_type(
        memory_requirements.memoryTypeBits, 
        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    VK_CHECK(vkAllocateMemory(device_handle, &memory_allocate_info, nullptr, &shared_texture.memory));
    VK_CHECK(vkBindImageMemory(device_handle, shared_texture.image, shared_texture.memory, 0));

    // Step 4: Export AHardwareBuffer from Vulkan memory
    VkMemoryGetAndroidHardwareBufferInfoANDROID info;
    info.sType  = VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    info.pNext  = nullptr;
    info.memory = shared_texture.memory;
    VK_CHECK(vkGetMemoryAndroidHardwareBufferANDROID(device_handle, &info, &shared_texture.hardware_buffer));

    // Step 5: Create sampler and image view for Vulkan shader access
    VkSamplerCreateInfo sampler_create_info{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
    sampler_create_info.magFilter   = VK_FILTER_LINEAR;
    sampler_create_info.minFilter   = VK_FILTER_LINEAR;
    sampler_create_info.mipmapMode  = VK_SAMPLER_MIPMAP_MODE_LINEAR;
    sampler_create_info.maxLod      = 1.0f;
    sampler_create_info.borderColor = VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE;
    vkCreateSampler(device_handle, &sampler_create_info, nullptr, &shared_texture.sampler);

    VkImageViewCreateInfo view_create_info{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
    view_create_info.viewType         = VK_IMAGE_VIEW_TYPE_2D;
    view_create_info.image            = shared_texture.image;
    view_create_info.format           = VK_FORMAT_R8G8B8A8_UNORM;
    view_create_info.subresourceRange = VkImageSubresourceRange{VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    vkCreateImageView(device_handle, &view_create_info, nullptr, &shared_texture.view);

    // Transition image to shader read optimal layout
    VkCommandBuffer copy_command = get_device().create_command_buffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, true);

    VkImageSubresourceRange subresource_range = {};
    subresource_range.aspectMask              = VK_IMAGE_ASPECT_COLOR_BIT;
    subresource_range.baseMipLevel            = 0;
    subresource_range.levelCount              = 1;
    subresource_range.layerCount              = 1;

    VkImageMemoryBarrier image_memory_barrier = vkb::initializers::image_memory_barrier();
    image_memory_barrier.image                = shared_texture.image;
    image_memory_barrier.subresourceRange     = subresource_range;
    image_memory_barrier.srcAccessMask        = 0;
    image_memory_barrier.dstAccessMask        = VK_ACCESS_SHADER_READ_BIT;
    image_memory_barrier.oldLayout            = VK_IMAGE_LAYOUT_UNDEFINED;
    image_memory_barrier.newLayout            = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    vkCmdPipelineBarrier(
        copy_command,
        VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
        0, 0, nullptr, 0, nullptr,
        1, &image_memory_barrier);

    get_device().flush_command_buffer(copy_command, queue, true);
}
```

---

## 3. Complete Code Example: OpenCL Import

### Importing AHB into OpenCL

```cpp
void OpenCLInteropArm::prepare_shared_resources()
{
    // ... Vulkan setup code from above ...

    // Setting up OpenCL resources - Import the AHardwareBuffer

    // Step 1: Create properties list for import
    const cl_import_properties_arm import_properties[3] = {
        CL_IMPORT_TYPE_ARM, 
        CL_IMPORT_TYPE_ANDROID_HARDWARE_BUFFER_ARM,
        0  // Terminate with 0
    };

    // Step 2: Import memory using clImportMemoryARM
    cl_int result  = CL_SUCCESS;
    cl_data->image = clImportMemoryARM(
        cl_data->context,
        CL_MEM_READ_WRITE,              // Memory access flags
        import_properties,
        shared_texture.hardware_buffer, // AHardwareBuffer from Vulkan
        CL_IMPORT_MEMORY_WHOLE_ALLOCATION_ARM,
        &result);

    if (result != CL_SUCCESS)
    {
        LOGE("Cannot import OpenCL memory, error code: {}.", result);
    }
}
```

---

## 4. OpenCL Kernel Example

### Procedural Texture Generation Kernel

```opencl
/* Copyright (c) 2021-2023, Arm Limited and Contributors */

// This kernel fills the contents of the texture with a simple pattern which changes over time
__kernel void generate_texture(__global unsigned char *data, float time)
{
    int x      = get_global_id(0);
    int y      = get_global_id(1);
    int width  = get_global_size(0);
    int height = get_global_size(1);

    int index = (y * width + x) * 4;

    // Calculate distance from center
    float dx = (x / (float) width - 0.5f) * 2.0f;
    float dy = (y / (float) height - 0.5f) * 2.0f;
    float dist = sqrt(dx * dx + dy * dy);

    // Generate RGBA values using mathematical functions
    data[index]     = (cos(dist * 25.0f - time * 5.0f) / 2.0f + 0.5f) * 255;  // R
    data[index + 1] = (cos(dx * 50.0f) / 2.0f + 0.5f) * 255;                  // G
    data[index + 2] = (cos(dy * 50.0f) / 2.0f + 0.5f) * 255;                  // B
    data[index + 3] = 255;                                                     // A
}
```

### Executing the Kernel

```cpp
void OpenCLInteropArm::run_texture_generation()
{
    // Set kernel arguments
    clSetKernelArg(cl_data->kernel, 0, sizeof(cl_mem), &cl_data->image);
    clSetKernelArg(cl_data->kernel, 1, sizeof(float), &total_time_passed);

    // Define global and local work sizes
    std::array<size_t, 2> global_size = {shared_texture.width, shared_texture.height};
    std::array<size_t, 2> local_size  = {16, 16};

    // Execute kernel
    cl_int result = clEnqueueNDRangeKernel(
        cl_data->command_queue,
        cl_data->kernel,
        global_size.size(),
        NULL,
        global_size.data(),
        local_size.data(),
        0, NULL, NULL);

    if (result != CL_SUCCESS)
    {
        LOGE("Cannot execute kernel, error code: {}", result);
    }
}
```

---

## 5. Synchronization Pattern

### Render Loop with Synchronization

```cpp
void OpenCLInteropArm::render(float delta_time)
{
    if (!prepared) return;

    total_time_passed += delta_time;

    // Wait until Vulkan rendering is finished before writing to texture
    vkWaitForFences(
        get_device().get_handle(), 
        1, 
        &rendering_finished_fence, 
        VK_TRUE, 
        std::numeric_limits<uint64_t>::max());
    vkResetFences(get_device().get_handle(), 1, &rendering_finished_fence);

    // Fill the texture using OpenCL
    run_texture_generation();

    // Wait until the texture is filled (synchronization)
    clFlush(cl_data->command_queue);
    clFinish(cl_data->command_queue);  // Blocks until all enqueued OpenCL commands complete

    // Display the texture using Vulkan
    ApiVulkanSample::prepare_frame();

    submit_info.commandBufferCount = 1;
    submit_info.pCommandBuffers    = &draw_cmd_buffers[current_buffer];

    // Submit Vulkan rendering and signal fence when done
    VK_CHECK(vkQueueSubmit(queue, 1, &submit_info, rendering_finished_fence));

    ApiVulkanSample::submit_frame();
}
```

---

## 6. CMakeLists.txt Configuration

### Khronos Sample Build Configuration

```cmake
# Copyright (c) 2021-2023, Arm Limited and Contributors
# The OpenCL interoperability only works on Android

get_filename_component(FOLDER_NAME ${CMAKE_CURRENT_LIST_DIR} NAME)
get_filename_component(PARENT_DIR ${CMAKE_CURRENT_LIST_DIR} PATH)
get_filename_component(CATEGORY_NAME ${PARENT_DIR} NAME)

if(ANDROID)
    add_sample_with_tags(
        ID ${FOLDER_NAME}
        CATEGORY ${CATEGORY_NAME}
        AUTHOR "Arm"
        NAME "Arm OpenCL Interoperability"
        DESCRIPTION "Example showing sharing resources between OpenCL and Vulkan on Arm devices"
        TAGS "arm"
        LIBS opencl                          # Link OpenCL library
        FILES
            ../open_cl_common/open_cl_functions.inl
            ../open_cl_common/open_cl_utils.h
            ../open_cl_common/open_cl_utils.cpp)
endif()
```

### Production CMakeLists.txt Example (android-hardware-buffer-camera)

```cmake
cmake_minimum_required(VERSION 3.10)

project(CORE_ENGINE)

if (NOT ANDROID_NDK_TOOLCHAIN_INCLUDED)
    message(FATAL_ERROR "-- Toolchain file not included, see https://developer.android.com/ndk/guides/cmake")
endif ()

add_library(
    native-engine
        SHARED
        app/src/main/native/cpp/main.cpp
        app/src/main/native/cpp/vulkan_renderer.cpp
        # ... other sources
)

# Important: Enable Vulkan Android extension support
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} \
    -DVK_USE_PLATFORM_ANDROID_KHR")

# Link required Android system libraries
target_link_libraries(
    native-engine
    PRIVATE
        EGL                 # For graphics
        GLESv3             # For OpenGL ES support
        android            # Android native APIs (AHardwareBuffer)
        log                # Logging
)
```

**Key Android Libraries Required:**
- `android` - Provides AHardwareBuffer APIs
- `EGL` - Graphics environment
- `GLESv3` - OpenGL ES 3.0+
- `log` - Android logging framework

---

## 7. Required Extensions and Capabilities

### Vulkan Extensions Required
```cpp
// In sample constructor
add_device_extension(VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME);
add_device_extension(VK_KHR_SAMPLER_YCBCR_CONVERSION_EXTENSION_NAME);
add_device_extension(VK_KHR_MAINTENANCE1_EXTENSION_NAME);
add_device_extension(VK_KHR_BIND_MEMORY_2_EXTENSION_NAME);
add_device_extension(VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME);
add_instance_extension(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);
add_instance_extension(VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME);
add_device_extension(VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME);
add_device_extension(VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME);
add_device_extension(VK_KHR_DEDICATED_ALLOCATION_EXTENSION_NAME);
```

### OpenCL Extensions Required
```cpp
// Check for required extensions
std::vector<std::string> required_extensions{
    "cl_arm_import_memory",
    "cl_arm_import_memory_android_hardware_buffer"
};

// Validate support
auto available_extensions = get_available_open_cl_extensions(platform_id);
for (auto extension : required_extensions)
{
    if (std::find(available_extensions.begin(), 
                  available_extensions.end(), 
                  extension) == available_extensions.end())
    {
        LOGE("Required OpenCL extension '{}' is not available.", extension);
        return;
    }
}
```

---

## 8. Alternative Reference: Android Hardware Buffer Camera

### Repository
- **URL**: https://github.com/kiryldz/android-hardware-buffer-camera
- **Stars**: 30
- **Language**: C++ (67.6%), Kotlin (10.6%)
- **Focus**: Real-time camera capture with AHB, Vulkan, and OpenGL ES interop

### Key Features
1. **CameraX Integration** - Modern Android Camera API
2. **Dual Backend Support** - Vulkan 1.3 and OpenGL ES 3.0
3. **Zero-Copy Rendering** - Direct AHB to GPU
4. **Jetpack Compose UI** - Modern Android UI
5. **NDK Looper** - Efficient background thread management

### Tech Stack
- Android SDK >= 26
- NDK 25.1.8937393
- Vulkan 1.3
- OpenGL ES 3.0
- EGL with hardware buffer extensions

---

## 9. Key Implementation Details

### Important Configuration Notes

1. **Image Tiling**: Must use `VK_IMAGE_TILING_LINEAR` for AHB compatibility
   ```cpp
   image_create_info.tiling = VK_IMAGE_TILING_LINEAR;
   ```

2. **Memory Allocation Size**: Set to 0 when using AHB
   ```cpp
   memory_allocate_info.allocationSize = 0;  // AHB handles size
   ```

3. **Dedicated Allocation**: Required for correct memory binding
   ```cpp
   VkMemoryDedicatedAllocateInfo dedicated_allocate_info;
   dedicated_allocate_info.image = shared_texture.image;  // Not null
   ```

4. **Synchronization Pattern**: Double-buffering recommended
   - OpenCL fills texture N while Vulkan renders texture N-1
   - Use VkFence for Vulkan completion tracking
   - Use clFinish() for OpenCL completion tracking

5. **Format Restrictions**: Only specific formats supported by AHB:
   - RGBA8 formats most common
   - Check AHB format compatibility table in Vulkan spec

---

## 10. Performance Considerations

### Zero-Copy Benefits
- Eliminates data transfer between APIs
- Both APIs share same memory region
- Critical for real-time processing (AR, video, ML)

### Best Practices
1. **Use Multiple Textures**: Implement double/triple buffering
   ```cpp
   // Instead of single shared_texture, use:
   SharedTexture textures[3];  // Triple buffering
   ```

2. **Minimize Synchronization**: Avoid blocking calls
   ```cpp
   // Avoid: clFinish() blocks entire application
   // Prefer: Event-based synchronization when possible
   ```

3. **Dedicated Render Threads**: Use separate threads for OpenCL and Vulkan
   - Prevents stalls from one API blocking the other

4. **Monitor Memory Usage**: AHB is precious resource
   - Reuse buffers when possible
   - Avoid excessive allocation/deallocation

---

## 11. Useful References

### Official Documentation
- **Vulkan Specification**: https://registry.khronos.org/vulkan/specs/latest/man/html/VK_ANDROID_external_memory_android_hardware_buffer.html
- **OpenCL ARM Extension**: https://registry.khronos.org/OpenCL/extensions/arm/cl_arm_import_memory.txt
- **Android Developer Docs**: https://developer.android.com/reference/android/hardware/HardwareBuffer
- **ARM Developer Blog**: https://developer.arm.com/community/arm-community-blogs/

### Code Examples
- Khronos Vulkan Samples: https://github.com/KhronosGroup/Vulkan-Samples
- Android Hardware Buffer Camera: https://github.com/kiryldz/android-hardware-buffer-camera
- ARM Style Transfer Demo: https://github.com/ARM-software/ml_style_transfer_post_processing_on_mobile

---

## 12. Common Issues and Solutions

### Issue: "Cannot import OpenCL memory"
**Solution**: Verify OpenCL extensions are available on device
```cpp
// Check device OpenCL version
cl_uint device_version;
clGetDeviceInfo(device_id, CL_DEVICE_VERSION, ...);
// Requires OpenCL 1.2+
```

### Issue: "VkCreateImage failed with VK_ERROR_INVALID_EXTERNAL_HANDLE"
**Solution**: Ensure all external memory structures are in pNext chain correctly
```cpp
// Correct chain: export_info -> dedicated_info -> image_create_info.pNext
image_create_info.pNext = &external_memory_image_create_info;
```

### Issue: "Data corruption in shared texture"
**Solution**: Ensure proper synchronization
```cpp
// Always flush/finish before switching APIs
clFlush(command_queue);
clFinish(command_queue);  // Wait for completion
vkQueueWaitIdle(queue);   // Wait for Vulkan
```

---

## Summary

This reference provides a complete, production-ready implementation of Vulkan-OpenCL interoperability on Android using Android Hardware Buffers. The code examples are based on:

1. **Official Khronos Vulkan Samples** - Authoritative reference implementation
2. **ARM's OpenCL Extensions** - AHB import mechanism
3. **Community Projects** - Real-world applications (android-hardware-buffer-camera)

All code follows Vulkan API standards and is compatible with Android API Level 26+ with supported Vulkan 1.1+ drivers.
