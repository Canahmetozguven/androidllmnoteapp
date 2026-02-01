#include "AhbManager.h"
#include <dlfcn.h>
#include <vector>

// Log macros
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG_AHB, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG_AHB, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG_AHB, __VA_ARGS__)

// Function pointers for dynamically loaded functions (if needed)
typedef PFN_vkGetMemoryAndroidHardwareBufferANDROID vkGetMemoryAndroidHardwareBufferANDROID_fn;

AhbManager::AhbManager()
    : mSupported(false)
{
    // Check if AHardwareBuffer is available (API 26+)
    // The mere existence of this constructor suggests we're building for API 26+
    // A real implementation might check __ANDROID_API__ or query system properties
#if __ANDROID_API__ >= 26
    mSupported = true;
    LOGI("AhbManager initialized - AHardwareBuffer supported");
#else
    LOGW("AhbManager initialized - AHardwareBuffer NOT supported (API < 26)");
#endif
}

AhbManager::~AhbManager()
{
    LOGI("AhbManager destroyed");
}

bool AhbManager::isSupported() const
{
    return mSupported;
}

AHardwareBuffer* AhbManager::allocateAHB(uint32_t width, uint32_t height, uint32_t format, uint64_t usage)
{
    if (!mSupported)
    {
        LOGE("allocateAHB: AHardwareBuffer not supported on this platform");
        return nullptr;
    }

    AHardwareBuffer_Desc desc{};
    desc.width = width;
    desc.height = height;
    desc.layers = 1;
    desc.format = format;
    desc.usage = usage;

    AHardwareBuffer* ahb = nullptr;
    int result = AHardwareBuffer_allocate(&desc, &ahb);

    if (result != 0 || ahb == nullptr)
    {
        LOGE("allocateAHB: AHardwareBuffer_allocate failed with code %d", result);
        return nullptr;
    }

    LOGI("allocateAHB: Successfully allocated AHB (%ux%u, format=%u, usage=%llu)",
         width, height, format, (unsigned long long)usage);

    return ahb;
}

bool AhbManager::createVulkanExportableImage(VkDevice device, VkPhysicalDevice physicalDevice,
                                             uint32_t width, uint32_t height,
                                             SharedResource& outResource)
{
    if (!mSupported)
    {
        LOGE("createVulkanExportableImage: AHardwareBuffer not supported");
        return false;
    }

    outResource.width = width;
    outResource.height = height;
    outResource.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;

    // Step 1: Create VkImage with external memory create info
    VkExternalMemoryImageCreateInfo externalMemoryImageCreateInfo{};
    externalMemoryImageCreateInfo.sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO;
    externalMemoryImageCreateInfo.pNext = nullptr;
    externalMemoryImageCreateInfo.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;

    VkImageCreateInfo imageCreateInfo{};
    imageCreateInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageCreateInfo.pNext = &externalMemoryImageCreateInfo;
    imageCreateInfo.imageType = VK_IMAGE_TYPE_2D;
    imageCreateInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    imageCreateInfo.mipLevels = 1;
    imageCreateInfo.arrayLayers = 1;
    imageCreateInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageCreateInfo.tiling = VK_IMAGE_TILING_LINEAR;  // Important for AHB compatibility
    imageCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    imageCreateInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    imageCreateInfo.extent = {width, height, 1};
    imageCreateInfo.usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;

    VkResult result = vkCreateImage(device, &imageCreateInfo, nullptr, &outResource.vkImage);
    if (result != VK_SUCCESS)
    {
        LOGE("createVulkanExportableImage: vkCreateImage failed with code %d", result);
        return false;
    }

    // Step 2: Get memory requirements
    VkMemoryRequirements memoryRequirements{};
    vkGetImageMemoryRequirements(device, outResource.vkImage, &memoryRequirements);

    // Step 3: Setup dedicated memory allocation
    VkMemoryDedicatedAllocateInfo dedicatedAllocateInfo{};
    dedicatedAllocateInfo.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO;
    dedicatedAllocateInfo.pNext = nullptr;
    dedicatedAllocateInfo.buffer = VK_NULL_HANDLE;
    dedicatedAllocateInfo.image = outResource.vkImage;

    // Step 4: Create exportable memory allocation
    VkExportMemoryAllocateInfo exportMemoryAllocateInfo{};
    exportMemoryAllocateInfo.sType = VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO;
    exportMemoryAllocateInfo.pNext = &dedicatedAllocateInfo;
    exportMemoryAllocateInfo.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;

    // Find suitable memory type
    uint32_t memoryTypeIndex = findMemoryType(physicalDevice, 
                                               memoryRequirements.memoryTypeBits,
                                               VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    VkMemoryAllocateInfo memoryAllocateInfo{};
    memoryAllocateInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    memoryAllocateInfo.pNext = &exportMemoryAllocateInfo;
    memoryAllocateInfo.allocationSize = memoryRequirements.size;
    memoryAllocateInfo.memoryTypeIndex = memoryTypeIndex;

    result = vkAllocateMemory(device, &memoryAllocateInfo, nullptr, &outResource.vkMemory);
    if (result != VK_SUCCESS)
    {
        LOGE("createVulkanExportableImage: vkAllocateMemory failed with code %d", result);
        vkDestroyImage(device, outResource.vkImage, nullptr);
        outResource.vkImage = VK_NULL_HANDLE;
        return false;
    }

    // Step 5: Bind image to memory
    result = vkBindImageMemory(device, outResource.vkImage, outResource.vkMemory, 0);
    if (result != VK_SUCCESS)
    {
        LOGE("createVulkanExportableImage: vkBindImageMemory failed with code %d", result);
        vkFreeMemory(device, outResource.vkMemory, nullptr);
        vkDestroyImage(device, outResource.vkImage, nullptr);
        outResource.vkMemory = VK_NULL_HANDLE;
        outResource.vkImage = VK_NULL_HANDLE;
        return false;
    }

    // Step 6: Export AHardwareBuffer from Vulkan memory
    // Load the extension function dynamically
    vkGetMemoryAndroidHardwareBufferANDROID_fn vkGetMemoryAndroidHardwareBufferANDROID =
        (vkGetMemoryAndroidHardwareBufferANDROID_fn)vkGetDeviceProcAddr(device, "vkGetMemoryAndroidHardwareBufferANDROID");

    if (!vkGetMemoryAndroidHardwareBufferANDROID)
    {
        LOGE("createVulkanExportableImage: Failed to load vkGetMemoryAndroidHardwareBufferANDROID");
        vkFreeMemory(device, outResource.vkMemory, nullptr);
        vkDestroyImage(device, outResource.vkImage, nullptr);
        outResource.vkMemory = VK_NULL_HANDLE;
        outResource.vkImage = VK_NULL_HANDLE;
        return false;
    }

    VkMemoryGetAndroidHardwareBufferInfoANDROID info{};
    info.sType = VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    info.pNext = nullptr;
    info.memory = outResource.vkMemory;

    result = vkGetMemoryAndroidHardwareBufferANDROID(device, &info, &outResource.ahb);
    if (result != VK_SUCCESS)
    {
        LOGE("createVulkanExportableImage: vkGetMemoryAndroidHardwareBufferANDROID failed with code %d", result);
        vkFreeMemory(device, outResource.vkMemory, nullptr);
        vkDestroyImage(device, outResource.vkImage, nullptr);
        outResource.vkMemory = VK_NULL_HANDLE;
        outResource.vkImage = VK_NULL_HANDLE;
        return false;
    }

    LOGI("createVulkanExportableImage: Successfully created exportable image (%ux%u)", width, height);
    return true;
}

cl_mem AhbManager::importAHBToOpenCL(cl_context context, AHardwareBuffer* ahb,
                                     const cl_import_properties_arm* properties)
{
    if (!mSupported)
    {
        LOGE("importAHBToOpenCL: AHardwareBuffer not supported");
        return nullptr;
    }

    if (ahb == nullptr)
    {
        LOGE("importAHBToOpenCL: Invalid AHardwareBuffer (null)");
        return nullptr;
    }

    // Load OpenCL library dynamically
    void* libOpenCL = dlopen("libOpenCL.so", RTLD_NOW | RTLD_LOCAL);
    if (!libOpenCL)
    {
        LOGE("importAHBToOpenCL: Failed to load libOpenCL.so: %s", dlerror());
        return nullptr;
    }

    // Get function pointer for clGetExtensionFunctionAddressForPlatform
    typedef void* (*clGetExtensionFunctionAddressForPlatform_fn)(cl_platform_id, const char*);
    clGetExtensionFunctionAddressForPlatform_fn clGetExtensionFunctionAddressForPlatform =
        (clGetExtensionFunctionAddressForPlatform_fn)dlsym(libOpenCL, "clGetExtensionFunctionAddressForPlatform");

    if (!clGetExtensionFunctionAddressForPlatform)
    {
        LOGE("importAHBToOpenCL: Failed to load clGetExtensionFunctionAddressForPlatform");
        dlclose(libOpenCL);
        return nullptr;
    }

    // Get the platform from the context
    cl_platform_id platform;
    cl_int result = clGetContextInfo(context, CL_CONTEXT_PLATFORM, sizeof(cl_platform_id), &platform, nullptr);
    if (result != CL_SUCCESS)
    {
        LOGE("importAHBToOpenCL: Failed to get platform from context, error code: %d", result);
        dlclose(libOpenCL);
        return nullptr;
    }

    // Query available extensions to determine vendor path
    size_t ext_size = 0;
    clGetPlatformInfo(platform, CL_PLATFORM_EXTENSIONS, 0, nullptr, &ext_size);
    std::vector<char> ext_str(ext_size);
    clGetPlatformInfo(platform, CL_PLATFORM_EXTENSIONS, ext_size, ext_str.data(), nullptr);
    std::string extensions(ext_str.data());

    cl_mem clMemory = nullptr;

    // Try ARM path first (cl_arm_import_memory)
    if (extensions.find("cl_arm_import_memory") != std::string::npos)
    {
        LOGI("importAHBToOpenCL: Attempting ARM import path (cl_arm_import_memory)");
        
        clImportMemoryARM_fn clImportMemoryARM =
            (clImportMemoryARM_fn)clGetExtensionFunctionAddressForPlatform(platform, "clImportMemoryARM");

        if (clImportMemoryARM)
        {
            // Setup import properties if not provided
            const cl_import_properties_arm defaultProperties[3] = {
                CL_IMPORT_TYPE_ARM,
                CL_IMPORT_TYPE_ANDROID_HARDWARE_BUFFER_ARM,
                0  // Terminate with 0
            };

            const cl_import_properties_arm* importProperties = properties ? properties : defaultProperties;

            // Import the AHardwareBuffer into OpenCL
            cl_int importResult = CL_SUCCESS;
            clMemory = clImportMemoryARM(
                context,
                CL_MEM_READ_WRITE,              // Memory access flags
                importProperties,
                ahb,                            // AHardwareBuffer from Vulkan
                CL_IMPORT_MEMORY_WHOLE_ALLOCATION_ARM,
                &importResult);

            if (importResult == CL_SUCCESS && clMemory != nullptr)
            {
                LOGI("importAHBToOpenCL: Successfully imported AHB via ARM path");
                dlclose(libOpenCL);
                return clMemory;
            }
            else
            {
                LOGW("importAHBToOpenCL: ARM import failed with error code: %d", importResult);
            }
        }
        else
        {
            LOGW("importAHBToOpenCL: clImportMemoryARM function not found despite extension presence");
        }
    }

    // Try QCOM path (cl_qcom_android_hardware_buffer_interop)
    if (extensions.find("cl_qcom_android_hardware_buffer_interop") != std::string::npos)
    {
        LOGI("importAHBToOpenCL: Attempting Qualcomm import path (cl_qcom_android_hardware_buffer_interop)");
        
        // Qualcomm extension: Try loading vendor-specific function
        // Note: QCOM may use clCreateMemObjectFromAHardwareBufferQCOM or similar
        clCreateMemObjectFromAHardwareBufferQCOM_fn clCreateMemObjectFromAHardwareBufferQCOM =
            (clCreateMemObjectFromAHardwareBufferQCOM_fn)clGetExtensionFunctionAddressForPlatform(
                platform, "clCreateMemObjectFromAHardwareBufferQCOM");

        if (clCreateMemObjectFromAHardwareBufferQCOM)
        {
            cl_int importResult = CL_SUCCESS;
            clMemory = clCreateMemObjectFromAHardwareBufferQCOM(
                context,
                CL_MEM_READ_WRITE,
                ahb,
                &importResult);

            if (importResult == CL_SUCCESS && clMemory != nullptr)
            {
                LOGI("importAHBToOpenCL: Successfully imported AHB via QCOM vendor-specific function");
                dlclose(libOpenCL);
                return clMemory;
            }
            else
            {
                LOGW("importAHBToOpenCL: QCOM vendor function failed with error code: %d", importResult);
            }
        }
        else
        {
            LOGW("importAHBToOpenCL: QCOM vendor function not found, trying standard OpenCL approach");
            
            // Fallback: QCOM may support standard clCreateImage with AHB via properties
            // This is speculative - QCOM's actual API may differ
            // Some vendors allow passing AHB as host_ptr with special mem_flags
            LOGW("importAHBToOpenCL: QCOM standard OpenCL approach not implemented (requires vendor documentation)");
            LOGE("importAHBToOpenCL: QCOM path incomplete - extension present but no working import method found");
        }
    }

    dlclose(libOpenCL);

    if (clMemory == nullptr)
    {
        LOGE("importAHBToOpenCL: All import paths failed - no compatible extension found");
        LOGE("importAHBToOpenCL: Available extensions: %s", extensions.c_str());
    }

    return clMemory;
}

void AhbManager::releaseResource(VkDevice device, SharedResource& resource)
{
    // Release OpenCL memory
    if (resource.clMem)
    {
        clReleaseMemObject(resource.clMem);
        resource.clMem = nullptr;
        LOGI("releaseResource: Released OpenCL memory");
    }

    // Release Vulkan resources
    if (device != VK_NULL_HANDLE)
    {
        if (resource.vkImage != VK_NULL_HANDLE)
        {
            vkDestroyImage(device, resource.vkImage, nullptr);
            resource.vkImage = VK_NULL_HANDLE;
            LOGI("releaseResource: Destroyed Vulkan image");
        }

        if (resource.vkMemory != VK_NULL_HANDLE)
        {
            vkFreeMemory(device, resource.vkMemory, nullptr);
            resource.vkMemory = VK_NULL_HANDLE;
            LOGI("releaseResource: Freed Vulkan memory");
        }
    }

    // Release AHardwareBuffer (if owned)
    if (resource.ahb)
    {
        AHardwareBuffer_release(resource.ahb);
        resource.ahb = nullptr;
        LOGI("releaseResource: Released AHardwareBuffer");
    }

    resource.width = 0;
    resource.height = 0;
    resource.format = 0;
}

uint32_t AhbManager::findMemoryType(VkPhysicalDevice physicalDevice, uint32_t typeFilter, VkMemoryPropertyFlags properties)
{
    VkPhysicalDeviceMemoryProperties memProperties{};
    vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memProperties);

    for (uint32_t i = 0; i < memProperties.memoryTypeCount; i++)
    {
        if ((typeFilter & (1 << i)) && (memProperties.memoryTypes[i].propertyFlags & properties) == properties)
        {
            return i;
        }
    }

    LOGE("findMemoryType: Failed to find suitable memory type");
    return 0;  // Fallback to first memory type (may not be suitable)
}

// ========== Synchronization Helpers (Safe v1 Strategy) ==========
// These implement conservative blocking waits to ensure all GPU operations
// complete before accessing shared resources. This is a simple, safe approach
// that avoids race conditions but may not be optimal for performance.
// Future versions can implement fine-grained semaphore-based synchronization.

bool AhbManager::waitForOpenCL(cl_command_queue queue)
{
    if (queue == nullptr)
    {
        LOGE("waitForOpenCL: Invalid command queue (null)");
        return false;
    }

    LOGI("waitForOpenCL: Waiting for all OpenCL operations to complete...");
    
    // clFinish blocks until all previously queued OpenCL commands in the queue complete
    cl_int result = clFinish(queue);
    
    if (result != CL_SUCCESS)
    {
        LOGE("waitForOpenCL: clFinish failed with error code: %d", result);
        return false;
    }

    LOGI("waitForOpenCL: All OpenCL operations completed successfully");
    return true;
}

bool AhbManager::waitForVulkan(VkQueue queue)
{
    if (queue == VK_NULL_HANDLE)
    {
        LOGE("waitForVulkan: Invalid queue (VK_NULL_HANDLE)");
        return false;
    }

    LOGI("waitForVulkan: Waiting for all Vulkan operations to complete...");
    
    // vkQueueWaitIdle blocks until all command buffers submitted to the queue have completed
    VkResult result = vkQueueWaitIdle(queue);
    
    if (result != VK_SUCCESS)
    {
        LOGE("waitForVulkan: vkQueueWaitIdle failed with code %d", result);
        return false;
    }

    LOGI("waitForVulkan: All Vulkan operations completed successfully");
    return true;
}
