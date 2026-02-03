package com.synapsenotes.ai.core.ai

import android.content.Context
import android.content.ServiceConnection
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.Build
import android.content.pm.PackageManager
import android.app.ActivityManager
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import com.synapsenotes.ai.core.ai.IGpuProbeService
import com.synapsenotes.ai.core.ai.probe.GpuProbeService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Default implementation of [HardwareCapabilityProvider] that queries
 * actual Android system capabilities.
 */
@Singleton
open class DefaultHardwareCapabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmContext: LlmContext,
    private val gpuInfoProvider: GpuInfoProvider
) : HardwareCapabilityProvider {

    init {
        checkPreviousCrash()
    }
    
    // ... (keep checkPreviousCrash and getters same)

    // ... (keep isVulkanSupported, getGpuName, getRecommendedBatchSize same)

    // ... (keep isMmapSafe, getRecommendedBackendOrder same)

    /**
     * Safely probes a backend using an isolated process service.
     * If the probe service crashes (Binder death), we detect it without killing the main app.
     */
    override fun probeBackend(backend: BackendType): Boolean {
        // CPU is always safe
        if (backend == BackendType.CPU) return true

        // If previously failed, skip
        if (getFailedBackends().contains(backend)) {
            android.util.Log.w(TAG, "Skipping $backend - previously marked as failed")
            return false
        }

        // Fast path: If already successfully loaded once, assume safe? 
        // No, better to verify again on cold start in case driver updated or instability is intermittent.
        
        android.util.Log.i(TAG, "Starting isolated probe for $backend...")
        
        // Map enum to int ID: 0=CPU, 1=Vulkan, 2=OpenCL
        val backendId = when(backend) {
            BackendType.VULKAN -> 1
            BackendType.OPENCL -> 2
            else -> 0
        }

        var isSuccess = false
        val probeIntent = Intent(context, GpuProbeService::class.java)
        
        try {
            runBlocking {
                try {
                    // Timeout after 5 seconds to prevent hangs
                    withTimeout(5000) {
                        isSuccess = bindAndProbe(probeIntent, backendId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Probe timed out or failed exception: ${e.message}")
                    isSuccess = false
                }
            }
        } catch (e: Exception) {
             android.util.Log.e(TAG, "Probe execution failed", e)
             isSuccess = false
        }

        if (isSuccess) {
            android.util.Log.i(TAG, "Probe SUCCESS for $backend")
            return true
        } else {
            android.util.Log.e(TAG, "Probe FAILED for $backend - marking as failed")
            markBackendFailed(backend)
            return false
        }
    }

    private suspend fun bindAndProbe(intent: Intent, backendId: Int): Boolean = suspendCancellableCoroutine { cont ->
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) {
                    if (cont.isActive) cont.resume(false)
                    return
                }
                
                try {
                    val api = IGpuProbeService.Stub.asInterface(service)
                    
                    // Register death recipient to detect crash
                    val deathRecipient = object : DeathRecipient {
                        override fun binderDied() {
                            android.util.Log.e(TAG, "Probe service died (CRASH DETECTED) during execution")
                            // Cannot resume continuation here easily if it's already running?
                            // Actually binderDied usually happens async.
                            // Ideally we handle this via try/catch in the call, but a hard crash kills the process.
                        }
                    }
                    service.linkToDeath(deathRecipient, 0)
                    
                    // Execute probe
                    val result = api.probeBackend(backendId)
                    
                    service.unlinkToDeath(deathRecipient, 0)
                    context.unbindService(this)
                    
                    if (cont.isActive) cont.resume(result)
                    
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Exception calling probe service (likely crash)", e)
                    try { context.unbindService(this) } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(false)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                android.util.Log.e(TAG, "Probe service disconnected unexpectedly (Crash)")
                try { context.unbindService(this) } catch (_: Exception) {}
                if (cont.isActive) cont.resume(false)
            }
            
            override fun onBindingDied(name: ComponentName?) {
                android.util.Log.e(TAG, "Probe binding died (Crash)")
                try { context.unbindService(this) } catch (_: Exception) {}
                if (cont.isActive) cont.resume(false)
            }

            override fun onNullBinding(name: ComponentName?) {
                android.util.Log.e(TAG, "Probe service returned null binding")
                try { context.unbindService(this) } catch (_: Exception) {}
                if (cont.isActive) cont.resume(false)
            }
        }

        val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        if (!bound) {
            android.util.Log.e(TAG, "Failed to bind to probe service")
            if (cont.isActive) cont.resume(false)
        }
    }

    // ... (keep remaining methods: detectBestBackend, markBackendFailed etc)

    private fun checkPreviousCrash() {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        
        // Check chat/general backend crash
        val attempting = prefs.getString("attempting_backend", null)
        if (attempting != null) {
            try {
                val backend = BackendType.valueOf(attempting)
                android.util.Log.e(TAG, "Previous run crashed while attempting $backend (Chat). Marking as failed.")
                markBackendFailed(backend)
                clearBackendAttempting()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error processing previous crash state", e)
            }
        }

        // Check embedding backend crash
        val attemptingEmbed = prefs.getString("attempting_embedding_backend", null)
        if (attemptingEmbed != null) {
            try {
                val backend = BackendType.valueOf(attemptingEmbed)
                android.util.Log.e(TAG, "Previous run crashed while attempting $backend (Embedding). Marking as failed.")
                markBackendFailed(backend)
                clearEmbeddingBackendAttempting()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error processing embedding crash state", e)
            }
        }
    }

    protected open fun getSdkInt(): Int = Build.VERSION.SDK_INT
    protected open fun getModel(): String = Build.MODEL ?: ""
    protected open fun getHardware(): String = Build.HARDWARE ?: ""
    protected open fun getSocModel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            ""
        }
    }
    protected open fun getBoard(): String = Build.BOARD ?: ""

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
        val renderer = gpuInfoProvider.getGpuRenderer()
        if (renderer != null) return renderer
        
        // Fallback
        return if (isVulkanSupported()) {
            "Vulkan GPU (Unknown)" 
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
        val soc = getSocModel().lowercase()
        val board = getBoard().lowercase()
        
        // --- 1. Known Crashers (Adreno 7xx, Xclipse 920) ---
        // Snapdragon 8 Gen 1 (sm8450), 8 Gen 2 (sm8550), 8 Elite (sm8750), and Exynos 2200 (s5e9925) 
        // These have severe Vulkan driver bugs with large batches.
        // SM6225 (Adreno 610) is also weak/buggy with compute.
        // Tensor G2 (gs201) and G3 (zuma) also unstable.
        
        // Check all hardware identifiers
        val ids = listOf(hardware, soc, board)
        val isProblematic = ids.any { id ->
            id.contains("sm8450") || id.contains("s5e9925") || 
            id.contains("sm8550") || id.contains("sm8750") ||
            id.contains("sm6225") || id.contains("gs201") || 
            id.contains("zuma")
        }

        if (isProblematic) {
             // Critical: Adreno 750 (8 Gen 3) crashes with batch >= 33 (GitHub #8743)
             // Adreno 730/740 also unstable.
             // Safe limit: 32
            return 32 
        }
        
        // --- 2. Adreno 6xx (Older Snapdragon) ---
        // Adreno 6xx (e.g., Pixel 4, 5, older Galaxies) tends to timeout on large compute shaders.
        // We detect this via hardware strings roughly (sm8150, sm8250, sm7250 etc or just checking GPU name later if we had it).
        // For now, safe default for mobile is conservative.
        
        val isExynos = hardware.contains("exynos") || hardware.contains("samsung")
        
        // Exynos Mali is generally OK with 256, but Adreno 6xx prefers 128-256.
        // Returning 256 is a safe "high performance" default for mobile.
        // 512 is aggressive for 8GB phones.
        return if (isExynos) 256 else 256
    }

    override fun getRecommendedEmbeddingBatchSize(modelSizeBytes: Long): Int {
        // Base batch from hardware capabilities
        var batch = getRecommendedBatchSize()
        val availRam = getAvailableRamGb()
        val isProblematic = batch <= 32 // From the check above

        // If explicitly problematic, stick to the low limit
        if (isProblematic) return 32

        // Heuristic: Reduce batch for large models or low RAM to prevent OOM/Stability issues
        // 500MB is a rough threshold for "medium" embedding models
        if (modelSizeBytes > 500 * 1024 * 1024) {
            batch = 256
        }
        // 1GB+ is "large"
        if (modelSizeBytes > 1024 * 1024 * 1024) {
            batch = 128
        }

        // RAM constraint
        if (availRam < 3.0) {
            batch = minOf(batch, 128)
        }

        return batch
    }

    override fun getRecommendedContextSize(backend: BackendType): Int {
        // Context size is primarily a RAM constraint.
        // Vulkan backend consumes ~1GB more VRAM/RAM than OpenCL/CPU during model loading.
        val totalRam = getTotalRamGb()
        
        // High RAM devices (12GB+) -> 4096 is safe
        if (totalRam > 11.0) return 4096
        
        // 8GB devices (approx 7.0 - 8.5 actual)
        if (totalRam > 6.5) {
            return if (backend == BackendType.VULKAN) {
                2048 // Vulkan RAM spike makes 4096 risky on 8GB
            } else {
                4096 // OpenCL/CPU might handle 4096 on 8GB
            }
        }
        
        // Low RAM (< 6GB) -> 2048 safe limit
        return 2048
    }

    override fun isMmapSafe(): Boolean {
        val hardware = getHardware().lowercase()
        val soc = getSocModel().lowercase()
        val board = getBoard().lowercase()
        
        val ids = listOf(hardware, soc, board)
        
        // Problematic Snapdragon/Exynos chips often crash with mmap enabled + Vulkan
        val isProblematic = ids.any { id ->
            id.contains("sm8450") || id.contains("s5e9925") || 
            id.contains("sm8550") || id.contains("sm8750") ||
            id.contains("gs201") || id.contains("zuma")
        }
        
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

    override fun getPreferredEmbeddingBackend(): BackendType {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("preferred_embedding_backend", null)
        if (saved != null) {
            try {
                val backend = BackendType.valueOf(saved)
                if (!getFailedBackends().contains(backend)) {
                    return backend
                }
            } catch (e: Exception) { }
        }
        // Default to same logic as general backend if no specific pref
        return detectBestBackend()
    }

    override fun setPreferredEmbeddingBackend(backend: BackendType) {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("preferred_embedding_backend", backend.name).apply()
    }

    // override fun probeBackend(backend: BackendType): Boolean {
    //     // If already marked as failed, skip
    //     if (getFailedBackends().contains(backend)) {
    //         android.util.Log.w(TAG, "Skipping $backend - previously marked as failed")
    //         return false
    //     }
    //     
    //     return try {
    //         when (backend) {
    //             BackendType.VULKAN -> isVulkanSupported() && !isKnownProblematicDevice()
    //             BackendType.OPENCL -> isOpenCLSupported()
    //             BackendType.CPU -> true
    //         }
    //     } catch (e: Exception) {
    //         android.util.Log.e(TAG, "Backend probe failed for $backend", e)
    //         markBackendFailed(backend)
    //         false
    //     }
    // }

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
        // Use commit() because we need this to be persisted IMMEDIATELY before the risky native call.
        // apply() is async and might not write to disk before a native crash kills the process.
        prefs.edit().putString("attempting_backend", backend.name).commit()
        android.util.Log.i(TAG, "Marked backend as attempting: $backend")
    }

    override fun clearBackendAttempting() {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("attempting_backend").apply() // clear can be async, we are safe now
        android.util.Log.i(TAG, "Cleared attempting backend")
    }

    override fun markEmbeddingBackendAttempting(backend: BackendType) {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        // Use commit() for safety against native crashes
        prefs.edit().putString("attempting_embedding_backend", backend.name).commit()
        android.util.Log.i(TAG, "Marked embedding backend as attempting: $backend")
    }

    override fun clearEmbeddingBackendAttempting() {
        val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("attempting_embedding_backend").apply()
        android.util.Log.i(TAG, "Cleared attempting embedding backend")
    }

    /**
     * Check if this is a known problematic device for GPU backends.
     * S22/S23 with Snapdragon 8 Gen 1/2 have severe Vulkan driver bugs.
     * Adreno 610 (SM6225 / Snapdragon 680) crashes on OpenCL init.
     */
    private fun isKnownProblematicDevice(): Boolean {
        val hardware = getHardware().lowercase()
        val soc = getSocModel().lowercase()
        val board = getBoard().lowercase()
        
        val model = getModel().lowercase()
        val ids = listOf(hardware, soc, board, model)
        
        // sm8450: Snapdragon 8 G1 (codenames: waipio, taro)
        // s5e9925: Exynos 2200 (S22 Europe)
        // sm8550: Snapdragon 8 G2 (kalama)
        // sm8750: Snapdragon 8 Elite (S25)
        // sm6225: Snapdragon 680 (Adreno 610)
        // gs201: Tensor G2, zuma: Tensor G3
        
        // S22 specific models (US/KR/China Snapdragon variants): SM-S901, SM-S906, SM-S908
        val isS22Snapdragon = model.startsWith("sm-s901") || model.startsWith("sm-s906") || model.startsWith("sm-s908")
        
        val isProblematic = ids.any { id ->
            id.contains("sm8450") || id.contains("waipio") || id.contains("taro") ||
            id.contains("s5e9925") || 
            id.contains("sm8550") || id.contains("kalama") ||
            id.contains("sm8750") || 
            id.contains("sm6225") ||
            id.contains("gs201") || id.contains("zuma")
        } || isS22Snapdragon
        
        android.util.Log.i(TAG, "Checking device compatibility: Hardware=$hardware, SoC=$soc, Board=$board, Model=$model. isProblematic=$isProblematic")
        
        return isProblematic
    }

    /**
     * Returns the recommended backend order for this specific device.
     * Used by LlmEngine to override the default detection logic.
     */
     override fun getRecommendedBackendOrder(): List<BackendType> {
         val hardware = getHardware().lowercase()
         val soc = getSocModel().lowercase()
         val board = getBoard().lowercase()
         val model = getModel().lowercase()
         
         val ids = listOf(hardware, soc, board)
         
         // Tensor (Pixel 7/8/9) -> Force CPU (Vulkan/OpenCL unstable on Mali G710/G715/G78MP20 in these implementations)
         // or at least prioritize Vulkan if we must.
         if (ids.any { it.contains("gs201") || it.contains("zuma") || it.contains("zuma_pro") }) {
             // For now, CPU is safest for Pixel Tensor until we verify Vulkan stability
             return listOf(BackendType.CPU) 
         }
         
         // Get actual GPU vendor from EGL
         val renderer = gpuInfoProvider.getGpuRenderer()?.lowercase() ?: ""
         
          // 1. Adreno (Qualcomm) -> CPU preferred
          // Research (Feb 2026) shows CPU + I8MM is 3x-5x faster than Adreno OpenCL for token generation.
          if (renderer.contains("adreno")) {
              return listOf(BackendType.CPU, BackendType.OPENCL, BackendType.VULKAN)
          }
          
          // 2. Mali (ARM) -> CPU preferred
          if (renderer.contains("mali")) {
              return listOf(BackendType.CPU, BackendType.VULKAN, BackendType.OPENCL)
          }
          
          // 3. Xclipse (Samsung/AMD) -> CPU preferred
          if (renderer.contains("xclipse") || renderer.contains("amd")) {
              return listOf(BackendType.CPU, BackendType.VULKAN, BackendType.OPENCL)
          }
         
         // Fallback/Legacy heuristics if EGL failed
         
         // Exynos 2200 (S22) -> Prefer OpenCL (Xclipse Vulkan unstable) - Wait, Xclipse is AMD. 
         // Research said Xclipse prefers Vulkan but drivers unstable. 
         // Previous code said OpenCL. Let's stick to the new Matrix: Xclipse -> Vulkan.
         // But to be safe vs regression, if we failed to get renderer string, we use legacy logic.
         
         if (ids.any { it.contains("s5e9925") }) { // Exynos 2200
             return listOf(BackendType.OPENCL, BackendType.CPU)
         }
         
         // Snapdragon 8 Elite (S25) -> Prefer Vulkan (Adreno 830) due to OpenCL instability
         // Covers SM8750 (chipset) and SM-S938 (S25 Ultra)
         if (ids.any { it.contains("sm8750") } || model.startsWith("sm-s938")) {
             return listOf(BackendType.VULKAN, BackendType.OPENCL, BackendType.CPU)
         }
         
         // Snapdragon 8 Gen 1 (S22 / SM8450) -> Prefer OpenCL
         val isS22Snapdragon = model.startsWith("sm-s901") || model.startsWith("sm-s906") || model.startsWith("sm-s908")
         if (isS22Snapdragon || ids.any { it.contains("sm8450") || it.contains("taro") || it.contains("waipio") }) {
             return listOf(BackendType.OPENCL, BackendType.VULKAN, BackendType.CPU)
         }
         
         // Default: Vulkan -> OpenCL -> CPU
         return listOf(BackendType.VULKAN, BackendType.OPENCL, BackendType.CPU)
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

