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
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class DefaultHardwareCapabilityProviderTest {

    @JvmField
    @RegisterExtension
    val coroutineExtension = CoroutineTestExtension()

    private lateinit var provider: DefaultHardwareCapabilityProvider
    private val context: Context = mockk(relaxed = true)
    private val llmContext: LlmContext = mockk(relaxed = true)
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

        val realProvider = DefaultHardwareCapabilityProvider(context, llmContext)
        provider = spyk(realProvider)
        every { provider["getSdkInt"]() } returns 30
        every { provider["getModel"]() } returns "generic"
        every { provider["getHardware"]() } returns "generic"
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any) {
        // ... kept for potential other use or can be removed
    }

    @Test
    fun `getPreferredBackend returns VULKAN when supported`() {
        // ... (existing)
    }

    // ...

    @Test
    fun `getPreferredBackend returns OPENCL on S22 even if Vulkan is supported`() {
        every { provider["getModel"]() } returns "SM-S901B" // S22
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1) } returns true
        every { llmContext.isOpenCLAvailable() } returns true
        
        val result = provider.getPreferredBackend()
        
        assertEquals(BackendType.OPENCL, result)
    }

    @Test
    fun `getPreferredBackend respects saved preference`() {
        every { sharedPreferences.getString("preferred_backend", null) } returns "CPU"
        
        val result = provider.getPreferredBackend()
        
        assertEquals(BackendType.CPU, result)
    }
}