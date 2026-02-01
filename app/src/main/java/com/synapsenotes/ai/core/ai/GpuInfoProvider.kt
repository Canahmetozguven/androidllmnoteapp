package com.synapsenotes.ai.core.ai

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides information about the GPU using standard Android EGL APIs.
 * This is used to detect the GPU vendor (Adreno, Mali, etc.) without
 * relying on potentially unsafe native probing or heavyweight Vulkan initialization.
 */
@Singleton
class GpuInfoProvider @Inject constructor() {

    private var cachedRenderer: String? = null

    /**
     * Returns the GL_RENDERER string (e.g., "Adreno (TM) 740" or "Mali-G710").
     * This operation is cached after the first call.
     */
    fun getGpuRenderer(): String? {
        if (cachedRenderer != null) {
            return cachedRenderer
        }

        synchronized(this) {
            if (cachedRenderer != null) return cachedRenderer

            cachedRenderer = queryGlRenderer()
            return cachedRenderer
        }
    }

    private fun queryGlRenderer(): String? {
        var renderer: String? = null
        try {
            // 1. Initialize EGL
            val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            if (!EGL14.eglInitialize(dpy, version, 0, version, 1)) {
                return null
            }

            // 2. Choose Config
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(dpy, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
                EGL14.eglTerminate(dpy)
                return null
            }

            // 3. Create Context
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val ctx = EGL14.eglCreateContext(dpy, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (ctx == EGL14.EGL_NO_CONTEXT) {
                EGL14.eglTerminate(dpy)
                return null
            }

            // 4. Create Pbuffer Surface (we need a surface to make context current)
            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val surface = EGL14.eglCreatePbufferSurface(dpy, configs[0], surfaceAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroyContext(dpy, ctx)
                EGL14.eglTerminate(dpy)
                return null
            }

            // 5. Make Current
            if (EGL14.eglMakeCurrent(dpy, surface, surface, ctx)) {
                // 6. Query String
                renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            }

            // 7. Cleanup
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surface)
            EGL14.eglDestroyContext(dpy, ctx)
            EGL14.eglTerminate(dpy)

        } catch (e: Exception) {
            // Log error in a real app, for now just safe return null
            android.util.Log.e("GpuInfoProvider", "Failed to query GL_RENDERER", e)
        }

        return renderer
    }
}
