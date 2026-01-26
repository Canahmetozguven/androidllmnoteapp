package com.synapsenotes.ai.core.ai

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [HardwareCapabilityProvider] that queries
 * actual Android system capabilities.
 */
@Singleton
open class DefaultHardwareCapabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmContext: LlmContext
) : HardwareCapabilityProvider {

    protected open fun getSdkInt(): Int = Build.VERSION.SDK_INT

    override fun isVulkanSupported(): Boolean {
        val pm = context.packageManager
        // Check for Vulkan 1.1 hardware level (required for ggml Vulkan backend)
        return if (getSdkInt() >= Build.VERSION_CODES.N) {
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1) ||
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00401000) // Vulkan 1.1
        } else {
            false
        }
    }

    override fun getGpuName(): String? {
        // GPU name is not directly available via standard Android APIs.
        // We return a generic description based on available info.
        // In a real implementation, this could query OpenGL/Vulkan extensions.
        return if (isVulkanSupported()) {
            "Vulkan GPU" // Placeholder - actual name requires native query
        } else {
            null
        }
    }

    override fun getTotalRamGb(): Double {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024 * 1024.0)
    }

    override fun getAvailableRamGb(): Double {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024 * 1024.0)
    }

    override fun getRecommendedBatchSize(): Int {
        val hardware = (Build.HARDWARE ?: "").lowercase()
        val model = (Build.MODEL ?: "").lowercase()
        
        // S22 Ultra (Snapdragon 8 Gen 1 / Adreno 730) has severe Vulkan driver bugs with large batches.
        // GitHub issues confirm batch sizes > 32 cause immediate DeviceLostError.
        val isSnapdragon8Gen1 = hardware.contains("sm8450") || hardware.contains("qcom")
        val isS22 = model.contains("sm-s90")

        if (isSnapdragon8Gen1 || isS22) {
            return 32 // Critical limit for Adreno 730 stability
        }
        
        val isExynos = hardware.contains("exynos") || hardware.contains("samsung")
        return if (isExynos) 256 else 512
    }

    override fun getRecommendedContextSize(): Int {
        // Context size is primarily a RAM constraint.
        // 8GB is "enough" for 4096 context IF the model is small (<4GB).
        // But for standard 7B models (~5GB), 8GB total RAM is very tight (OS + App + Model).
        // 2048 context saves ~300MB compared to 4096, which can be the difference between stability and OOM.
        val totalRam = getTotalRamGb()
        return if (totalRam > 8.5) 4096 else 2048
    }

    override fun isMmapSafe(): Boolean {
        val hardware = (Build.HARDWARE ?: "").lowercase()
        val model = (Build.MODEL ?: "").lowercase()
        
        // S22 Ultra / S23 (Snapdragon 8 Gen 1/2) often crash with mmap enabled + Vulkan
        val isSnapdragon = hardware.contains("sm8450") || hardware.contains("qcom")
        val isS22 = model.contains("sm-s90")
        
        // If it's a problematic Samsung/Snapdragon device, disable mmap
        return !(isSnapdragon || isS22)
    }

    override fun getAvailableBackends(): List<BackendType> {
        val backends = mutableListOf<BackendType>()
        if (isVulkanSupported()) backends.add(BackendType.VULKAN)
        if (isOpenCLSupported()) backends.add(BackendType.OPENCL)
        backends.add(BackendType.CPU)
        return backends
    }

    private fun isOpenCLSupported(): Boolean {
        return llmContext.isOpenCLAvailable()
    }

    override fun getPreferredBackend(): BackendType {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("preferred_backend", null)
        if (saved != null) {
            try {
                return BackendType.valueOf(saved)
            } catch (e: IllegalArgumentException) {
                // Invalid value, fall through to detection
            }
        }
        return detectBestBackend()
    }

    override fun setPreferredBackend(backend: BackendType) {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("preferred_backend", backend.name).apply()
    }

    private fun detectBestBackend(): BackendType {
        val hardware = (Build.HARDWARE ?: "").lowercase()
        val model = (Build.MODEL ?: "").lowercase()

        // S22 Ultra (Adreno 730) / Snapdragon 8 Gen 1 Blacklist
        // Vulkan is extremely unstable on these devices despite being "supported".
        // Force OpenCL if available, otherwise CPU.
        val isSnapdragon8Gen1 = hardware.contains("sm8450") || hardware.contains("qcom")
        val isS22 = model.contains("sm-s90")

        if ((isSnapdragon8Gen1 || isS22) && isOpenCLSupported()) {
            return BackendType.OPENCL
        }

        if (isVulkanSupported()) return BackendType.VULKAN
        if (isOpenCLSupported()) return BackendType.OPENCL
        return BackendType.CPU
    }
}
