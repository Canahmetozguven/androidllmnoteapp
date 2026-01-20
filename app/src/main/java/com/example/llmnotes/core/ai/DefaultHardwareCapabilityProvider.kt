package com.example.llmnotes.core.ai

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
class DefaultHardwareCapabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : HardwareCapabilityProvider {

    override fun isVulkanSupported(): Boolean {
        val pm = context.packageManager
        // Check for Vulkan 1.1 hardware level (required for ggml Vulkan backend)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
}
