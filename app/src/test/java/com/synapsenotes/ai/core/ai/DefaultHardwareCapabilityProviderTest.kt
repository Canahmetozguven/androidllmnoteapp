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
        setStaticField(Build::class.java, "HARDWARE", "generic")
        setStaticField(Build::class.java, "MODEL", "generic")

        every { context.packageManager } returns packageManager
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns prefsEditor
        every { prefsEditor.putString(any(), any()) } returns prefsEditor
        every { prefsEditor.apply() } returns Unit
        
        // Default: No preference saved
        every { sharedPreferences.getString("preferred_backend", null) } returns null

        val realProvider = DefaultHardwareCapabilityProvider(context, llmContext)
        provider = spyk(realProvider)
        every { provider["getSdkInt"]() } returns 30
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any) {
        try {
            val field = clazz.getField(fieldName)
            field.isAccessible = true
            
            val modifiersField = Field::class.java.getDeclaredField("modifiers")
            modifiersField.isAccessible = true
            modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
            
            field.set(null, value)
        } catch (e: Exception) {
            // Ignore reflection errors in newer JDKs if modules block access
            // This is a best-effort for unit testing legacy static fields
            println("Warning: Failed to set static field $fieldName: ${e.message}")
        }
    }

    @Test
    fun `getPreferredBackend returns VULKAN when supported`() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1) } returns true
        every { llmContext.isOpenCLAvailable() } returns true
        
        val result = provider.getPreferredBackend()
        
        assertEquals(BackendType.VULKAN, result)
    }

    @Test
    fun `getPreferredBackend returns OPENCL when Vulkan missing but OpenCL present`() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1) } returns false
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, any()) } returns false
        every { llmContext.isOpenCLAvailable() } returns true
        
        val result = provider.getPreferredBackend()
        
        assertEquals(BackendType.OPENCL, result)
    }

    @Test
    fun `getPreferredBackend returns CPU when both missing`() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1) } returns false
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, any()) } returns false
        every { llmContext.isOpenCLAvailable() } returns false
        
        val result = provider.getPreferredBackend()
        
        assertEquals(BackendType.CPU, result)
    }
}