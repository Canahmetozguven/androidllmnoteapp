#ifndef AHB_MANAGER_H
#define AHB_MANAGER_H

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string>
#include <android/hardware_buffer.h>

#define AHB_TAG "AHB_MANAGER"

// AHB Interop Path Selection
enum AhbInteropType {
    AHB_PATH_NONE = 0,    // No AHB support or unsupported vendor
    AHB_PATH_ARM = 1,     // ARM Mali path (cl_arm_import_memory extensions)
    AHB_PATH_QCOM = 2     // Qualcomm Adreno path (cl_qcom_android_hardware_buffer_interop)
};

// GPU Vendor Detection (reuse from native-lib.cpp)
enum GPUVendor {
    GPU_ADRENO,      // Qualcomm
    GPU_MALI,        // ARM
    GPU_POWERVR,     // Imagination
    GPU_UNKNOWN
};

/**
 * AhbManager: Vendor-aware AHB interop abstraction
 * 
 * Responsibilities:
 * - Detect GPU vendor (ARM Mali vs Qualcomm Adreno vs Unsupported)
 * - Select appropriate AHB import path based on OpenCL extensions
 * - Provide interop type for downstream import/export logic
 * 
 * Usage:
 *   AhbManager manager;
 *   manager.init(env);
 *   AhbInteropType type = manager.getInteropType();
 */
class AhbManager {
public:
    AhbManager();
    ~AhbManager();
    
    /**
     * Initialize AHB manager - detect vendor and check extensions
     * @param env JNI environment (reserved for future use)
     * @return true if initialization successful, false otherwise
     */
    bool init(JNIEnv* env);
    
    /**
     * Get the detected AHB interop path
     * @return AhbInteropType enum indicating supported path
     */
    AhbInteropType getInteropType() const;
    
    /**
     * Get human-readable string of current interop type
     * @return String representation for logging
     */
    const char* getInteropTypeString() const;
    
    /**
     * Export Vulkan buffer to AHardwareBuffer
     * @param vk_device Vulkan device handle
     * @param vk_memory Vulkan device memory handle
     * @param size Buffer size in bytes
     * @return AHardwareBuffer pointer on success, nullptr on failure
     */
    AHardwareBuffer* exportVulkanBuffer(void* vk_device, void* vk_memory, size_t size);
    
    /**
     * Import AHardwareBuffer into OpenCL memory object
     * @param cl_context OpenCL context handle
     * @param ahb AHardwareBuffer to import
     * @param size Buffer size in bytes
     * @return OpenCL memory object handle on success, nullptr on failure
     */
    void* importOpenCLBuffer(void* cl_context, AHardwareBuffer* ahb, size_t size);
    
private:
    AhbInteropType interop_type_;
    GPUVendor gpu_vendor_;
    
    // Internal detection methods
    GPUVendor detectGpuVendor();
    bool checkOpenClAhbExtensions();
    bool checkVulkanAhbExtension();
};

#endif // AHB_MANAGER_H
