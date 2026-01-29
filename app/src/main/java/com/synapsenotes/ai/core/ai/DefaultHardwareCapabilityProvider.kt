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

    init {
        checkPreviousCrash()
    }

    private fun checkPreviousCrash() {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        val attempting = prefs.getString("attempting_backend", null)
        if (attempting != null) {
            try {
                val backend = BackendType.valueOf(attempting)
                android.util.Log.e(TAG, "Available backends check: Previous run crashed while attempting $backend. Marking as failed.")
                markBackendFailed(backend)
                // Clear the attempting flag so we don't loop if the failure logic itself crashes (unlikely)
                clearBackendAttempting()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error processing previous crash state", e)
            }
        }
    }

    protected open fun getSdkInt(): Int = Build.VERSION.SDK_INT
    protected open fun getModel(): String = Build.MODEL ?: ""
    protected open fun getHardware(): String = Build.HARDWARE ?: ""

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
        val hardware = getHardware().lowercase()
        val model = getModel().lowercase()
        
        // Snapdragon 8 Gen 1 (sm8450), 8 Gen 2 (sm8550), and Exynos 2200 (s5e9925) have severe Vulkan driver bugs with large batches.
        val isProblematic = hardware.contains("sm8450") || hardware.contains("s5e9925") || hardware.contains("sm8550")

        if (isProblematic) {
            return 32 // Critical limit for stability on these Adreno/Xclipse GPUs
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
        val hardware = getHardware().lowercase()
        val model = getModel().lowercase()
        
        // Problematic Snapdragon/Exynos chips often crash with mmap enabled + Vulkan
        val isProblematic = hardware.contains("sm8450") || hardware.contains("s5e9925") || hardware.contains("sm8550")
        
        // If it's a problematic SoC, disable mmap for stability
        return !isProblematic
    }

    override fun getAvailableBackends(): List<BackendType> {
        val backends = mutableListOf<BackendType>()
        if (isVulkanSupported() && !getFailedBackends().contains(BackendType.VULKAN)) {
            backends.add(BackendType.VULKAN)
        }
        if (isOpenCLSupported() && !getFailedBackends().contains(BackendType.OPENCL)) {
            backends.add(BackendType.OPENCL)
        }
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
                val backend = BackendType.valueOf(saved)
                // If the saved backend has failed, detect a new one
                if (!getFailedBackends().contains(backend)) {
                    return backend
                }
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

    override fun probeBackend(backend: BackendType): Boolean {
        // If already marked as failed, skip
        if (getFailedBackends().contains(backend)) {
            android.util.Log.w(TAG, "Skipping $backend - previously marked as failed")
            return false
        }
        
        return try {
            when (backend) {
                BackendType.VULKAN -> isVulkanSupported() && !isKnownProblematicDevice()
                BackendType.OPENCL -> isOpenCLSupported()
                BackendType.CPU -> true
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Backend probe failed for $backend", e)
            markBackendFailed(backend)
            false
        }
    }

    override fun getFailedBackends(): Set<BackendType> {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        val savedFailed = prefs.getStringSet("failed_backends", emptySet()) ?: emptySet()
        return savedFailed.mapNotNull { 
            try { BackendType.valueOf(it) } catch (_: Exception) { null }
        }.toSet()
    }

    override fun markBackendFailed(backend: BackendType) {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        val currentFailed = getFailedBackends().toMutableSet()
        currentFailed.add(backend)
        prefs.edit().putStringSet("failed_backends", currentFailed.map { it.name }.toSet()).apply()
        android.util.Log.w(TAG, "Marked backend as failed: $backend. Failed list: $currentFailed")
    }

    override fun clearFailedBackends() {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("failed_backends").apply()
        android.util.Log.i(TAG, "Cleared failed backends list")
    }

    override fun markBackendAttempting(backend: BackendType) {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("attempting_backend", backend.name).apply()
        android.util.Log.i(TAG, "Marked backend as attempting: $backend")
    }

    override fun clearBackendAttempting() {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("attempting_backend").apply()
        android.util.Log.i(TAG, "Cleared attempting backend")
    }

    /**
     * Check if this is a known problematic device for GPU backends.
     * S22/S23 with Snapdragon 8 Gen 1/2 have severe Vulkan driver bugs.
     */
    private fun isKnownProblematicDevice(): Boolean {
        val hardware = getHardware().lowercase()
        
        // sm8450: Snapdragon 8 G1, s5e9925: Exynos 2200, sm8550: Snapdragon 8 G2
        val isProblematic = hardware.contains("sm8450") || hardware.contains("s5e9925") || hardware.contains("sm8550")
        
        android.util.Log.i(TAG, "Checking device compatibility: Hardware=$hardware. isProblematic=$isProblematic")
        
        return isProblematic
    }

    private fun detectBestBackend(): BackendType {
        val failedBackends = getFailedBackends()
        
        // Log current state for debugging
        android.util.Log.i(TAG, "Detecting best backend. Failed backends: $failedBackends")
        
        // Ordered fallback chain: VULKAN -> OPENCL -> CPU
        val fallbackOrder = listOf(BackendType.VULKAN, BackendType.OPENCL, BackendType.CPU)
        
        for (backend in fallbackOrder) {
            if (failedBackends.contains(backend)) {
                android.util.Log.w(TAG, "Skipping $backend - previously failed")
                continue
            }
            
            if (probeBackend(backend)) {
                android.util.Log.i(TAG, "Selected backend: $backend")
                return backend
            }
        }
        
        // CPU is always the final fallback (should never fail)
        android.util.Log.w(TAG, "All GPU backends failed, using CPU")
        return BackendType.CPU
    }

    companion object {
        private const val TAG = "HardwareCapability"
    }
}

