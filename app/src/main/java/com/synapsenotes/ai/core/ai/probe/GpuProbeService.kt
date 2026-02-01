package com.synapsenotes.ai.core.ai.probe

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.synapsenotes.ai.core.ai.BackendType
import com.synapsenotes.ai.core.ai.IGpuProbeService
import com.synapsenotes.ai.core.ai.NativeLib

class GpuProbeService : Service() {

    companion object {
        private const val TAG = "GpuProbeService"
        init {
            try {
                System.loadLibrary("llm_notes_cpp")
                Log.i(TAG, "Native library loaded in probe process")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library in probe process", e)
            }
        }
    }

    private val binder = object : IGpuProbeService.Stub() {
        override fun probeBackend(backendId: Int): Boolean {
            Log.i(TAG, "Probing backend ID: $backendId")
            return try {
                // Map integer ID to BackendType enum if needed, or pass int directly to JNI
                // Assuming NativeLib has a method 'probeBackendNative(int)'
                // 0=CPU, 1=Vulkan, 2=OpenCL
                NativeLib.probeBackendNative(backendId)
            } catch (e: Throwable) {
                Log.e(TAG, "Probe failed with exception", e)
                false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}