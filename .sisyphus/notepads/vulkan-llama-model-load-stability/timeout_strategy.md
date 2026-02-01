# Load-Time Timeout & Fallback Strategy

## Problem
Native model loading (`llama_model_load_from_file`) can hang indefinitely due to:
- Vulkan driver locks (common on Adreno).
- Shader compilation stalls.
- Memory fragmentation stalls.

Currently, `LlmEngine` waits forever. The `attempting_backend` flag only catches *crashes* (process death), not hangs.

## Proposed Logic

### 1. Timeout Guard
Wrap the blocking JNI call in `withTimeout`:

```kotlin
// LlmEngine.kt

// 60 seconds should be enough even for large models on slow devices
val LOAD_TIMEOUT_MS = 60_000L 

try {
    withTimeout(LOAD_TIMEOUT_MS) {
        llmContext.loadModel(...)
    }
} catch (e: TimeoutCancellationException) {
    Log.e(TAG, "Backend $backend timed out after ${LOAD_TIMEOUT_MS}ms")
    // Treat as failure
    throw e
}
```

### 2. Failure Handling
When a timeout occurs:
1.  **Mark Failed**: Call `hardwareCapabilityProvider.markBackendFailed(backend)`.
2.  **Persistence**: Do *NOT* clear `attempting_backend`.
    -   Rationale: The native thread is likely still stuck in the driver. We cannot safely "cancel" it.
    -   If the user kills the app (or ANR watchdog kills it), we want the next boot to see "attempting_backend" and blacklist it.
3.  **Fallback**: The loop in `LlmEngine` catches the exception and proceeds to the next backend in `backendsToTry`.

### 3. Risk Mitigation (Zombie Threads)
Since we cannot kill the stuck native thread:
- The app might become unstable (resource leaks).
- **Recommendation**: If a timeout occurs, prompt the user to restart the app?
- **Decision**: For now, try fallback. If the driver is locked, the next backend (CPU) might work (no GPU driver dependency). If the GPU driver lock freezes the whole system, the OS will handle it (ANR).

### 4. Implementation Plan
- Modify `LlmEngine.kt` to import `withTimeout`.
- Apply to `loadModel` and `loadEmbeddingModel`.
