package com.synapsenotes.ai.core.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test to verify the C++ backend selection logic in native-lib.cpp.
 * Uses mock SoC and Hardware strings to ensure the tiered selection works as expected.
 */
@RunWith(AndroidJUnit4::class)
class NativeBackendSelectionTest {

    private val CPU = 0
    private val VULKAN = 1
    private val OPENCL = 2

    @Test
    fun testAdrenoSelection() {
        // Typical Qualcomm Adreno strings
        assertEquals("Should select OpenCL for Adreno (sm8350)", OPENCL, NativeLib.testSelectionLogicNative("sm8350", "qcom"))
        assertEquals("Should select OpenCL for Adreno (msm8998)", OPENCL, NativeLib.testSelectionLogicNative("msm8998", "qcom"))
    }

    @Test
    fun testMaliSelection() {
        // Typical ARM Mali strings
        assertEquals("Should select Vulkan for Mali (mt6893)", VULKAN, NativeLib.testSelectionLogicNative("mt6893", "mt6893"))
        assertEquals("Should select Vulkan for Mali (exynos2100)", VULKAN, NativeLib.testSelectionLogicNative("exynos2100", "exynos2100"))
    }

    @Test
    fun testTensorSelection() {
        // Google Tensor (Mali-based but currently Vulkan preferred in C++)
        assertEquals("Should select Vulkan for Tensor G2", VULKAN, NativeLib.testSelectionLogicNative("gs201", "gs201"))
        assertEquals("Should select Vulkan for Tensor G3", VULKAN, NativeLib.testSelectionLogicNative("zuma", "zuma"))
    }

    @Test
    fun testProblematicSocFallback() {
        // Known problematic SoCs should fallback to CPU (0)
        assertEquals("Snapdragon 8 Gen 1 should fallback to CPU", CPU, NativeLib.testSelectionLogicNative("sm8450", "qcom"))
        assertEquals("Exynos 2200 should fallback to CPU", CPU, NativeLib.testSelectionLogicNative("s5e9925", "exynos2200"))
        assertEquals("Snapdragon 8 Gen 2 should fallback to CPU", CPU, NativeLib.testSelectionLogicNative("sm8550", "qcom"))
    }

    @Test
    fun testUnknownFallback() {
        assertEquals("Unknown device should fallback to CPU", CPU, NativeLib.testSelectionLogicNative("unknown_soc", "unknown_hw"))
    }
}
