package com.synapsenotes.ai.core.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import android.util.Log

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackendCapabilitiesTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var hardwareCapabilityProvider: HardwareCapabilityProvider

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun logHardwareCapabilities() {
        val gpuName = hardwareCapabilityProvider.getGpuName()
        val totalRam = hardwareCapabilityProvider.getTotalRamGb()
        val recommendedOrder = hardwareCapabilityProvider.getRecommendedBackendOrder()
        val availableBackends = hardwareCapabilityProvider.getAvailableBackends()

        Log.i("CAPABILITIES", "GPU Name: $gpuName")
        Log.i("CAPABILITIES", "Total RAM: $totalRam GB")
        Log.i("CAPABILITIES", "Recommended Order: $recommendedOrder")
        Log.i("CAPABILITIES", "Available Backends: $availableBackends")

        for (backend in availableBackends) {
            val ctxSize = hardwareCapabilityProvider.getRecommendedContextSize(backend)
            Log.i("CAPABILITIES", "Backend: $backend -> Recommended Context: $ctxSize")
        }
    }
}
