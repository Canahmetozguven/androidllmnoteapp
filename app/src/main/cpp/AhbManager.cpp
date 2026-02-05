#include "AhbManager.h"
#include <sys/system_properties.h>
#include <cstring>
#include <vector>
#include <android/hardware_buffer.h>

// Vulkan external memory structures (minimal declarations to avoid header dependency)
#define VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID 1000129004

typedef uint32_t VkStructureType;
typedef void* VkDevice;
typedef void* VkDeviceMemory;
typedef int32_t VkResult;

struct VkMemoryGetAndroidHardwareBufferInfoANDROID {
    VkStructureType sType;
    const void* pNext;
    VkDeviceMemory memory;
};

typedef VkResult (*vkGetMemoryAndroidHardwareBufferANDROID_fn)(
    VkDevice device,
    const VkMemoryGetAndroidHardwareBufferInfoANDROID* pInfo,
    AHardwareBuffer** pBuffer
);

// Helper to get Android system properties
static std::string get_system_property(const char* key) {
    char value[PROP_VALUE_MAX] = {0};
    __system_property_get(key, value);
    return std::string(value);
}

// Resolve GPU vendor from SoC/hardware strings (logic from native-lib.cpp)
static GPUVendor resolve_gpu_vendor(const std::string& soc, const std::string& hardware) {
    // Check for Adreno (Qualcomm Snapdragon)
    if (soc.find("msm") != std::string::npos || 
        soc.find("sm") != std::string::npos ||
        soc.find("sdm") != std::string::npos ||
        hardware.find("qcom") != std::string::npos) {
        return GPU_ADRENO;
    }
    
    // Check for Mali (Samsung Exynos, MediaTek, Google Tensor)
    if (soc.find("exynos") != std::string::npos ||
        soc.find("mt") != std::string::npos ||
        soc.find("gs201") != std::string::npos || // Tensor G2
        soc.find("zuma") != std::string::npos ||  // Tensor G3
        hardware.find("exynos") != std::string::npos ||
        hardware.find("gs201") != std::string::npos ||
        hardware.find("zuma") != std::string::npos) {
        return GPU_MALI;
    }
    
    return GPU_UNKNOWN;
}

AhbManager::AhbManager() 
    : interop_type_(AHB_PATH_NONE), gpu_vendor_(GPU_UNKNOWN) {
}

AhbManager::~AhbManager() {
}

bool AhbManager::init(JNIEnv* env) {
    __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Initializing AhbManager");
    
    // Detect GPU vendor
    gpu_vendor_ = detectGpuVendor();
    
    // Check OpenCL AHB extensions
    bool opencl_ahb = checkOpenClAhbExtensions();
    
    // Check Vulkan AHB extension
    bool vulkan_ahb = checkVulkanAhbExtension();
    
    // Select interop path based on vendor and extension availability
    if (gpu_vendor_ == GPU_MALI && opencl_ahb) {
        interop_type_ = AHB_PATH_ARM;
        __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Selected AHB path: ARM Mali");
    } else if (gpu_vendor_ == GPU_ADRENO && opencl_ahb) {
        interop_type_ = AHB_PATH_QCOM;
        __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Selected AHB path: Qualcomm Adreno");
    } else {
        interop_type_ = AHB_PATH_NONE;
        __android_log_print(ANDROID_LOG_WARN, AHB_TAG, "AHB interop not supported - vendor: %d, opencl: %d, vulkan: %d", 
                           gpu_vendor_, opencl_ahb, vulkan_ahb);
    }
    
    __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "AhbManager initialized - Type: %s", getInteropTypeString());
    return interop_type_ != AHB_PATH_NONE;
}

AhbInteropType AhbManager::getInteropType() const {
    return interop_type_;
}

const char* AhbManager::getInteropTypeString() const {
    switch (interop_type_) {
        case AHB_PATH_ARM:  return "ARM_MALI";
        case AHB_PATH_QCOM: return "QUALCOMM_ADRENO";
        case AHB_PATH_NONE: return "NONE";
        default:            return "UNKNOWN";
    }
}

GPUVendor AhbManager::detectGpuVendor() {
    std::string soc = get_system_property("ro.board.platform");
    std::string hardware = get_system_property("ro.hardware");
    
    GPUVendor vendor = resolve_gpu_vendor(soc, hardware);
    
    if (vendor == GPU_ADRENO) {
        __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Detected GPU: Adreno (Qualcomm) [soc=%s, hw=%s]", soc.c_str(), hardware.c_str());
    } else if (vendor == GPU_MALI) {
        __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Detected GPU: Mali (ARM/Tensor) [soc=%s, hw=%s]", soc.c_str(), hardware.c_str());
    } else {
        __android_log_print(ANDROID_LOG_WARN, AHB_TAG, "Unknown GPU vendor [soc=%s, hw=%s]", soc.c_str(), hardware.c_str());
    }
    
    return vendor;
}

bool AhbManager::checkOpenClAhbExtensions() {
    __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Checking OpenCL AHB extensions");
    
    // Try to load OpenCL library dynamically
    void* opencl_lib = dlopen("libOpenCL.so", RTLD_NOW | RTLD_LOCAL);
    if (!opencl_lib) {
        const char* paths[] = {
            "/system/vendor/lib64/libOpenCL.so",
            "/system/lib64/libOpenCL.so",
            "/vendor/lib64/libOpenCL.so",
            "/system/vendor/lib/libOpenCL.so",
            "/system/lib/libOpenCL.so"
        };
        
        for (const char* path : paths) {
            opencl_lib = dlopen(path, RTLD_NOW | RTLD_LOCAL);
            if (opencl_lib) {
                __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Loaded OpenCL from: %s", path);
                break;
            }
        }
    }
    
    if (!opencl_lib) {
        __android_log_print(ANDROID_LOG_WARN, AHB_TAG, "OpenCL library not available");
        return false;
    }
    
    // Define OpenCL types (avoid header dependency)
    typedef int32_t cl_int;
    typedef uint32_t cl_uint;
    typedef void* cl_platform_id;
    
    #define CL_SUCCESS 0
    #define CL_PLATFORM_EXTENSIONS 0x0900
    
    typedef cl_int (*clGetPlatformIDs_fn)(cl_uint, cl_platform_id*, cl_uint*);
    typedef cl_int (*clGetPlatformInfo_fn)(cl_platform_id, cl_uint, size_t, void*, size_t*);
    
    auto clGetPlatformIDs = (clGetPlatformIDs_fn)dlsym(opencl_lib, "clGetPlatformIDs");
    auto clGetPlatformInfo = (clGetPlatformInfo_fn)dlsym(opencl_lib, "clGetPlatformInfo");
    
    if (!clGetPlatformIDs || !clGetPlatformInfo) {
        __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, "Failed to load OpenCL functions");
        dlclose(opencl_lib);
        return false;
    }
    
    // Query platforms
    cl_uint num_platforms = 0;
    cl_int err = clGetPlatformIDs(0, nullptr, &num_platforms);
    if (err != CL_SUCCESS || num_platforms == 0) {
        __android_log_print(ANDROID_LOG_WARN, AHB_TAG, "No OpenCL platforms found");
        dlclose(opencl_lib);
        return false;
    }
    
    std::vector<cl_platform_id> platforms(num_platforms);
    clGetPlatformIDs(num_platforms, platforms.data(), nullptr);
    
    // Check extensions on first platform
    size_t ext_size = 0;
    clGetPlatformInfo(platforms[0], CL_PLATFORM_EXTENSIONS, 0, nullptr, &ext_size);
    
    std::vector<char> extensions(ext_size);
    clGetPlatformInfo(platforms[0], CL_PLATFORM_EXTENSIONS, ext_size, extensions.data(), nullptr);
    std::string ext_str(extensions.data());
    
    __android_log_print(ANDROID_LOG_DEBUG, AHB_TAG, "OpenCL Extensions: %s", ext_str.c_str());
    
    // Check vendor-specific extensions
    bool has_arm_import = ext_str.find("cl_arm_import_memory") != std::string::npos;
    bool has_arm_ahb = ext_str.find("cl_arm_import_memory_android_hardware_buffer") != std::string::npos;
    bool has_qcom_ahb = ext_str.find("cl_qcom_android_hardware_buffer_interop") != std::string::npos;
    
    __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "ARM Extensions: import=%d, ahb=%d", has_arm_import, has_arm_ahb);
    __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "QCOM Extension: ahb=%d", has_qcom_ahb);
    
    dlclose(opencl_lib);
    
    // ARM Mali requires both extensions, Qualcomm requires one
    bool result = (has_arm_import && has_arm_ahb) || has_qcom_ahb;
    
    __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "OpenCL AHB extensions: %s", result ? "AVAILABLE" : "NOT AVAILABLE");
    return result;
}

bool AhbManager::checkVulkanAhbExtension() {
    // VK_ANDROID_external_memory_android_hardware_buffer is available on API 26+
    // Since min SDK is 28, assume it's available (optimistic check)
    // Full verification would require vkEnumerateDeviceExtensionProperties during Vulkan init
    
    __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Vulkan AHB extension: AVAILABLE (assumed for API 28+)");
    return true;
}

AHardwareBuffer* AhbManager::exportVulkanBuffer(void* vk_device, void* vk_memory, size_t size) {
    __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Exporting Vulkan buffer to AHardwareBuffer (size=%zu)", size);
    
    // Load Vulkan library dynamically
    void* vulkan_lib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!vulkan_lib) {
        __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, "Failed to load libvulkan.so");
        return nullptr;
    }
    
    // Resolve vkGetMemoryAndroidHardwareBufferANDROID function
    auto vkGetMemoryAndroidHardwareBufferANDROID = 
        (vkGetMemoryAndroidHardwareBufferANDROID_fn)dlsym(vulkan_lib, "vkGetMemoryAndroidHardwareBufferANDROID");
    
    if (!vkGetMemoryAndroidHardwareBufferANDROID) {
        __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, 
                           "Failed to resolve vkGetMemoryAndroidHardwareBufferANDROID: %s", dlerror());
        dlclose(vulkan_lib);
        return nullptr;
    }
    
    // Prepare Vulkan AHB export structure
    VkMemoryGetAndroidHardwareBufferInfoANDROID export_info = {};
    export_info.sType = VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    export_info.pNext = nullptr;
    export_info.memory = (VkDeviceMemory)vk_memory;
    
    AHardwareBuffer* ahb = nullptr;
    VkResult result = vkGetMemoryAndroidHardwareBufferANDROID((VkDevice)vk_device, &export_info, &ahb);
    
    if (result != 0 || ahb == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, 
                           "vkGetMemoryAndroidHardwareBufferANDROID failed with result=%d", result);
        dlclose(vulkan_lib);
        return nullptr;
    }
    
    __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Successfully exported Vulkan buffer to AHB");
    dlclose(vulkan_lib);
    return ahb;
}

void* AhbManager::importOpenCLBuffer(void* cl_context, AHardwareBuffer* ahb, size_t size) {
    __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Importing AHardwareBuffer to OpenCL (size=%zu)", size);
    
    if (ahb == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, "AHardwareBuffer is null");
        return nullptr;
    }
    
    // Load OpenCL library dynamically
    void* opencl_lib = dlopen("libOpenCL.so", RTLD_NOW | RTLD_LOCAL);
    if (!opencl_lib) {
        __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, "Failed to load libOpenCL.so");
        return nullptr;
    }
    
    // OpenCL type definitions (minimal to avoid header dependency)
    typedef int32_t cl_int;
    typedef uint32_t cl_uint;
    typedef void* cl_context_t;
    typedef void* cl_mem;
    typedef uint64_t cl_mem_flags;
    
    #define CL_SUCCESS 0
    #define CL_MEM_READ_WRITE (1 << 0)
    #define CL_IMPORT_TYPE_ANDROID_HARDWARE_BUFFER_ARM 0x41E2
    
    // ARM import function signature
    typedef cl_mem (*clImportMemoryARM_fn)(
        cl_context_t context,
        cl_mem_flags flags,
        const void* properties,
        void* memory,
        size_t size,
        cl_int* errcode_ret
    );
    
    void* import_result = nullptr;
    
    // Try ARM path first (well-documented, proven on Mali GPUs)
    if (interop_type_ == AHB_PATH_ARM) {
        __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Using ARM Mali import path (clImportMemoryARM)");
        
        auto clImportMemoryARM = (clImportMemoryARM_fn)dlsym(opencl_lib, "clImportMemoryARM");
        if (!clImportMemoryARM) {
            __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, 
                               "Failed to resolve clImportMemoryARM: %s", dlerror());
            dlclose(opencl_lib);
            return nullptr;
        }
        
        // ARM import properties
        cl_uint import_properties[] = {
            CL_IMPORT_TYPE_ANDROID_HARDWARE_BUFFER_ARM,
            0  // Terminator
        };
        
        cl_int err = CL_SUCCESS;
        cl_mem mem = clImportMemoryARM(
            (cl_context_t)cl_context,
            CL_MEM_READ_WRITE,
            import_properties,
            ahb,
            size,
            &err
        );
        
        if (err != CL_SUCCESS || mem == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, 
                               "clImportMemoryARM failed with error=%d", err);
            dlclose(opencl_lib);
            return nullptr;
        }
        
        __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Successfully imported AHB via ARM path");
        import_result = mem;
        
    } else if (interop_type_ == AHB_PATH_QCOM) {
        // Qualcomm Adreno path (placeholder - requires vendor-specific API)
        __android_log_print(ANDROID_LOG_WARN, AHB_TAG, "QCOM path not fully implemented yet");
        __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Attempting QCOM import (speculative)...");
        
        // Speculative QCOM function signature (based on extension name)
        typedef cl_mem (*clCreateMemObjectFromAHardwareBufferQCOM_fn)(
            cl_context_t context,
            cl_mem_flags flags,
            AHardwareBuffer* buffer,
            cl_int* errcode_ret
        );
        
        auto clCreateMemObjectFromAHardwareBufferQCOM = 
            (clCreateMemObjectFromAHardwareBufferQCOM_fn)dlsym(opencl_lib, "clCreateMemObjectFromAHardwareBufferQCOM");
        
        if (!clCreateMemObjectFromAHardwareBufferQCOM) {
            __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, 
                               "Failed to resolve clCreateMemObjectFromAHardwareBufferQCOM: %s", dlerror());
            __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, 
                               "QCOM import path requires vendor documentation");
            dlclose(opencl_lib);
            return nullptr;
        }
        
        cl_int err = CL_SUCCESS;
        cl_mem mem = clCreateMemObjectFromAHardwareBufferQCOM(
            (cl_context_t)cl_context,
            CL_MEM_READ_WRITE,
            ahb,
            &err
        );
        
        if (err != CL_SUCCESS || mem == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, 
                               "clCreateMemObjectFromAHardwareBufferQCOM failed with error=%d", err);
            dlclose(opencl_lib);
            return nullptr;
        }
        
        __android_log_print(ANDROID_LOG_INFO, AHB_TAG, "Successfully imported AHB via QCOM path (speculative)");
        import_result = mem;
        
    } else {
        __android_log_print(ANDROID_LOG_ERROR, AHB_TAG, "No valid AHB interop path detected");
        dlclose(opencl_lib);
        return nullptr;
    }
    
    dlclose(opencl_lib);
    return import_result;
}

