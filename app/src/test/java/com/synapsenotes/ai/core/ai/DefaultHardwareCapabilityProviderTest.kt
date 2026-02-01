package com.synapsenotes.ai.core.ai

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.synapsenotes.ai.test.CoroutineTestExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import android.os.Build
import android.app.ActivityManager
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class DefaultHardwareCapabilityProviderTest {

    @JvmField
    @RegisterExtension
    val coroutineExtension = CoroutineTestExtension()

    private lateinit var provider: DefaultHardwareCapabilityProvider
    private val context: Context = mockk(relaxed = true)
    private val llmContext: LlmContext = mockk(relaxed = true)
    private val gpuInfoProvider: GpuInfoProvider = mockk(relaxed = true)
    private val packageManager: PackageManager = mockk(relaxed = true)
    private val sharedPreferences: SharedPreferences = mockk(relaxed = true)
    private val prefsEditor: SharedPreferences.Editor = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        every { context.packageManager } returns packageManager
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns prefsEditor
        every { prefsEditor.putString(any(), any()) } returns prefsEditor
        every { prefsEditor.apply() } returns Unit
        
        // Default: No preference saved
        every { sharedPreferences.getString("preferred_backend", null) } returns null
        // Default: No failed backends
        every { sharedPreferences.getStringSet("failed_backends", emptySet()) } returns emptySet()
        // Default: No backend being attempted (no previous crash)
        every { sharedPreferences.getString("attempting_backend", null) } returns null
        every { prefsEditor.remove(any()) } returns prefsEditor

        val realProvider = DefaultHardwareCapabilityProvider(context, llmContext, gpuInfoProvider)
        provider = spyk(realProvider)
        every { provider["getSdkInt"]() } returns 30
        every { provider["getModel"]() } returns "generic"
        every { provider["getHardware"]() } returns "generic"
        every { provider["getSocModel"]() } returns "generic"
        every { provider["getBoard"]() } returns "generic"
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any) {
        // ... kept for potential other use or can be removed
    }

    @Test
    fun `getRecommendedBackendOrder prioritizes OpenCL for Adreno`() {
        every { gpuInfoProvider.getGpuRenderer() } returns "Adreno (TM) 740"
        
        val order = provider.getRecommendedBackendOrder()
        
        assertEquals(BackendType.OPENCL, order[0])
        assertEquals(BackendType.VULKAN, order[1])
        assertEquals(BackendType.CPU, order[2])
    }

    @Test
    fun `getRecommendedBackendOrder prioritizes Vulkan for Mali`() {
        every { gpuInfoProvider.getGpuRenderer() } returns "Mali-G710 MC10"
        
        val order = provider.getRecommendedBackendOrder()
        
        assertEquals(BackendType.VULKAN, order[0])
        assertEquals(BackendType.OPENCL, order[1])
        assertEquals(BackendType.CPU, order[2])
    }

    @Test
    fun `getRecommendedBackendOrder prioritizes Vulkan for Xclipse`() {
        every { gpuInfoProvider.getGpuRenderer() } returns "Samsung Xclipse 920"
        
        val order = provider.getRecommendedBackendOrder()
        
        assertEquals(BackendType.VULKAN, order[0])
        assertEquals(BackendType.OPENCL, order[1])
        assertEquals(BackendType.CPU, order[2])
    }

    @Test
    fun `getRecommendedBackendOrder falls back to CPU for Tensor (Pixel)`() {
        // Mock hardware strings for Tensor G2
        every { provider["getHardware"]() } returns "gs201" 
        every { gpuInfoProvider.getGpuRenderer() } returns "Mali-G710" // Even if renderer is Mali, hardware check should override
        
        val order = provider.getRecommendedBackendOrder()
        
        assertEquals(BackendType.CPU, order[0])
        assertEquals(1, order.size)
    }

    @Test
    fun `getPreferredBackend respects saved preference`() {
        every { sharedPreferences.getString("preferred_backend", null) } returns "CPU"
        
        val result = provider.getPreferredBackend()
        
        assertEquals(BackendType.CPU, result)
    }

    @Test
    fun `getRecommendedContextSize handles 8GB device correctly`() {
        val actManager = mockk<ActivityManager>(relaxed = true)
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns actManager
        
        // Mock 7.8GB RAM
        every { actManager.getMemoryInfo(any()) } answers { 
            val arg = arg<ActivityManager.MemoryInfo>(0)
            arg.totalMem = (7.8 * 1024 * 1024 * 1024).toLong()
        }
        
        assertEquals(2048, provider.getRecommendedContextSize(BackendType.VULKAN))
        assertEquals(4096, provider.getRecommendedContextSize(BackendType.OPENCL))
    }
}