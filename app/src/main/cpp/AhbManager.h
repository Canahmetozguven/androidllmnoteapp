#pragma once

#include <android/hardware_buffer.h>
#include <vulkan/vulkan.h>
#include <CL/cl.h>
#include <CL/cl_ext.h>
#include <vector>
#include <string>
#include <android/log.h>

#define TAG_AHB "AhbManager"

// Forward declarations for function pointers to avoid linking issues
typedef cl_mem (*clImportMemoryARM_fn)(cl_context, cl_mem_flags, const cl_import_properties_arm*, void*, size_t, cl_int*);

// QCOM extension function pointer (if different from ARM)
// Note: QCOM may use standard clCreateBuffer/clCreateImage with AHB handle
// Or a vendor-specific function like clCreateMemObjectFromAHardwareBufferQCOM
typedef cl_mem (*clCreateMemObjectFromAHardwareBufferQCOM_fn)(cl_context, cl_mem_flags, AHardwareBuffer*, cl_int*);

class AhbManager {
public:
    struct SharedResource {
        AHardwareBuffer* ahb = nullptr;
        VkImage vkImage = VK_NULL_HANDLE;
        VkDeviceMemory vkMemory = VK_NULL_HANDLE;
        cl_mem clMem = nullptr;
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
    // For export: Creates VkImage -> Allocates Memory -> Exports AHB
    // For import: Takes existing AHB -> Imports to VkImage
    // Note: To implement Export, we need the VkDevice.
    bool createVulkanExportableImage(VkDevice device, VkPhysicalDevice physicalDevice, 
                                     uint32_t width, uint32_t height, 
                                     SharedResource& outResource);

    // Import an existing AHB into OpenCL
    // Requires the cl_arm_import_memory extension function pointer
    cl_mem importAHBToOpenCL(cl_context context, AHardwareBuffer* ahb, 
                             const cl_import_properties_arm* properties = nullptr);

    // Release resources
    void releaseResource(VkDevice device, SharedResource& resource);

    // Synchronization helpers (conservative "safe v1" strategy)
    // These methods implement blocking waits to ensure all operations complete
    // before accessing shared resources across APIs
    
    // Wait for all OpenCL operations on the queue to complete
    // Uses clFinish for conservative blocking synchronization
    bool waitForOpenCL(cl_command_queue queue);
    
    // Wait for all Vulkan operations on the queue to complete
    // Uses vkQueueWaitIdle for conservative blocking synchronization
    bool waitForVulkan(VkQueue queue);

private:
    bool mSupported;
    
    // Helper to find memory type index
    uint32_t findMemoryType(VkPhysicalDevice physicalDevice, uint32_t typeFilter, VkMemoryPropertyFlags properties);
};
