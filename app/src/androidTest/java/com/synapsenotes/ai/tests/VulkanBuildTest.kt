package com.synapsenotes.ai.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class VulkanBuildTest {

    @Test
    fun verifyVulkanLibraryInApk() {
        // This test runs ON THE DEVICE/EMULATOR, so it can inspect the installed package.
        // However, a simpler unit test to verify the APK structure *post-build* might be better on the host.
        // For an "easy and fast" check, we can just inspect the build output on the host side.
        // But if you want to verify the *installed* app has it:
        
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        
        // Check for libllama.so (or whatever your shared lib is named)
        val libFile = File(nativeLibDir, "libnative-lib.so") // Adjust name if needed
        assertTrue("Native library not found at $nativeLibDir", libFile.exists())
        
        // We can't easily check for "Vulkan enabled" binary symbols from Kotlin easily without more tools,
        // but presence is a good start.
    }
}
