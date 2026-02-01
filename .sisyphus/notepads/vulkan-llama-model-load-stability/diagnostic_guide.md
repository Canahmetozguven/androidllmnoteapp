# Diagnostic Log Capture Procedure

## 1. Device Identification
Capture the exact System-on-Chip (SoC) and hardware identifiers to map against our blocklists.

Run these commands in your terminal:
```bash
echo "--- Device ID ---"
adb shell getprop ro.product.model
adb shell getprop ro.board.platform
adb shell getprop ro.hardware
adb shell getprop ro.soc.model
```

**Expected Output Examples:**
- S22 (Exynos): `s5e9925`
- Pixel 7a: `gs201`
- S25: `sm8750`

## 2. Log Capture
We need to capture the full model loading sequence, backend selection, and any potential native crash.

1.  **Clear buffer**:
    ```bash
    adb logcat -c
    ```

2.  **Start Logging**:
    ```bash
    # Filter for our AI engine and native bridge tags
    adb logcat -v color -s HardwareCapability:V LlmEngine:V LLM_JNI:V LLAMA_CPP:V AndroidRuntime:E
    ```

3.  **Reproduce**:
    - Open the App.
    - Go to Settings -> AI.
    - Select a model to load (or trigger the load).
    - Wait for the hang/crash or "Model loaded successfully".

## 3. What to Look For

**Backend Selection:**
Look for lines indicating which compute backend was chosen:
- `Auto-selected: OpenCL`
- `Selected backend: VULKAN`
- `Forcing CPU due to known SoC driver issues`

**Configuration:**
Check the parameters passed to the engine:
- `Batch: 32` (Safe Mode) vs `Batch: 512` (Default)
- `Mmap: false` (Safe Mode) vs `Mmap: true` (Default)

**Failures:**
- `Previous run crashed while attempting VULKAN`
- `Backend probe failed for OPENCL`
- `A/libc: Fatal signal 11 (SIGSEGV)` (Native Crash)

## 4. Reporting
Copy the relevant log section (from "Attempting to load..." to the error or success message) and paste it into the issue report.
