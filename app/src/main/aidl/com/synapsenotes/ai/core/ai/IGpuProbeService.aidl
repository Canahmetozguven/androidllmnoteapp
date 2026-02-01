package com.synapsenotes.ai.core.ai;

interface IGpuProbeService {
    /**
     * Attempts to initialize the specified backend.
     * Returns true if successful, false otherwise.
     * If the process crashes during this call, the Binder death recipient will trigger.
     *
     * @param backendId 0=CPU, 1=Vulkan, 2=OpenCL
     */
    boolean probeBackend(int backendId);
}