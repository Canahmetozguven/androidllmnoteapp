#pragma once

#include <android/hardware_buffer.h>
#include <vulkan/vulkan.h>

#ifdef GGML_USE_OPENCL
#include <CL/cl.h>
#include <CL/cl_ext.h>
#endif

#include <vector>
#include <string>
#include <android/log.h>

#define TAG_AHB "AhbManager"

#ifdef GGML_USE_OPENCL
// Forward declarations for function pointers to avoid linking issues
typedef cl_mem (*clImportMemoryARM_fn)(cl_context, cl_mem_flags, const cl_import_properties_arm*, void*, size_t, cl_int*);

// QCOM extension function pointer (if different from ARM)
typedef cl_mem (*clCreateMemObjectFromAHardwareBufferQCOM_fn)(cl_context, cl_mem_flags, AHardwareBuffer*, cl_int*);
#endif

class AhbManager {
public:
    struct SharedResource {
        AHardwareBuffer* ahb = nullptr;
        VkImage vkImage = VK_NULL_HANDLE;
        VkDeviceMemory vkMemory = VK_NULL_HANDLE;
#ifdef GGML_USE_OPENCL
        cl_mem clMem = nullptr;
#endif
        uint32_t width = 0;
        uint32_t height = 0;
        uint32_t format = 0; // AHARDWAREBUFFER_FORMAT_...
    };

    AhbManager();
    ~AhbManager();

    // Check if system supports AHB
    bool isSupported() const;

    // Allocate an AHardwareBuffer directly
    AHardwareBuffer* allocateAHB(uint32_t width, uint32_t height, uint32_t format, uint64_t usage);

    // Create a Vulkan Image backed by an AHardwareBuffer (Import or Export)
    bool createVulkanExportableImage(VkDevice device, VkPhysicalDevice physicalDevice, 
                                     uint32_t width, uint32_t height, 
                                     SharedResource& outResource);

#ifdef GGML_USE_OPENCL
    // Import an existing AHB into OpenCL
    cl_mem importAHBToOpenCL(cl_context context, AHardwareBuffer* ahb, 
                             const cl_import_properties_arm* properties = nullptr);
    
    // Wait for all OpenCL operations on the queue to complete
    bool waitForOpenCL(cl_command_queue queue);
#endif

    // Wait for all Vulkan operations on the queue to complete
    bool waitForVulkan(VkQueue queue);

private:
    bool mSupported;
    
    // Helper to find memory type index
    uint32_t findMemoryType(VkPhysicalDevice physicalDevice, uint32_t typeFilter, VkMemoryPropertyFlags properties);
};
