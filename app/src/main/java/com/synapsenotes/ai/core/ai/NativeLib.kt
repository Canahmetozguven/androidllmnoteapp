package com.synapsenotes.ai.core.ai

object NativeLib {
    init {
        try {
            System.loadLibrary("llm_notes_cpp")
        } catch (e: UnsatisfiedLinkError) {
            // Already handled in LlmEngine or potentially running in probe service
        }
    }

    /**
     * Initializes the specified backend to check for crashes.
     * @param backendId 0=CPU, 1=Vulkan, 2=OpenCL
     * @return true if initialization succeeded, false otherwise.
     */
    external fun probeBackendNative(backendId: Int): Boolean

    /**
     * Test function for verifying backend selection logic with mock device strings.
     * @return 0=CPU, 1=Vulkan, 2=OpenCL
     */
    external fun testSelectionLogicNative(soc: String, hardware: String): Int
}
