# Vulkan/OpenCL Llama.cpp Model Load Stability (Android 16)

## TL;DR

> **Quick Summary**: Diagnose device-specific Vulkan/OpenCL model load failures (Pixel 7 FE hang, S22 crash) and define safe, data-driven configuration overrides (mmap, batch/ctx, backend order) with automated verification steps.
>
> **Deliverables**:
> - Diagnostic log collection procedure (adb-based)
> - Device-specific backend override rules (S22/S25 + Tensor devices)
> - Load-time timeout + fallback strategy
> - Verification checklist with concrete commands
>
> **Estimated Effort**: Medium
> **Parallel Execution**: YES - 2 waves
> **Critical Path**: Device log capture → SoC detection validation → Config override plan → Verification

---

## Context

### Original Request
Check current Vulkan/OpenCL llama.cpp setup and find best configuration for model loading. Emulator loads models, but phones fail (Pixel 7 FE hangs, S22 crashes). Create a comprehensive plan.

### Interview Summary
**Key Discussions**:
- Devices are on **Android 16**. Failures affect **all models including embeddings**.
- **S22/S25** require **device-specific backend order overrides**.
- **mmap disabled by default** on S22/S25 for stability.
- Proceed with plan now and append late librarian findings if needed.

**Research Findings**:
- Default load params: **nBatch=512, nCtx=2048, useMmap=true** (`LlmContext`).
- Known problematic Vulkan SoCs flagged in native code: **Exynos 2200 (s5e9925)**, **Snapdragon 8 Elite (sm8750)**.
- LlmEngine fallback order: Preferred → Vulkan → OpenCL → CPU; crash tracking via SharedPreferences.
- Logging tags: **LLM_JNI**, **LLAMA_CPP**, Kotlin tags **LlmEngine**, **HardwareCapability**, **MainViewModel**.
- External sources show Android Vulkan/OpenCL are device/driver sensitive; **OpenCL official support limited to Snapdragon 8 Gen 3 / 8 Elite (Adreno 750/830)**.

**Pending Research**:
- Librarian results may add device-specific constraints; append if new findings arrive.

### Metis Review (Gaps Addressed)
- **Critical Gap**: No explicit **load timeout** to escape hangs.
- **Likely missing SoC blocklist**: Tensor devices (Pixel 7 FE) not detected in current lists.
- Need explicit **device log capture** and validation of SoC detection values.
- Require **agent-executable acceptance criteria** (adb-based).

---

## Work Objectives

### Core Objective
Stabilize model loading on real Android devices by validating SoC detection, enforcing device-specific backend order overrides, disabling mmap on problematic devices, and adding a timeout + fallback for hung loads.

### Concrete Deliverables
- Diagnostic procedure (adb commands + expected log patterns)
- Device-specific backend rules (S22/S25 + Tensor)
- Timeout-based load protection plan
- Verification plan for emulator + device

### Definition of Done
- [x] Device SoC IDs are captured via adb and mapped to backend rules.
- [x] Device-specific overrides defined for S22/S25 and Tensor devices.
- [x] Load-time timeout behavior specified with fallback behavior.
- [x] Automated verification steps are provided (adb/logcat based).

### Must Have
- Explicit device log capture step before changes.
- CPU fallback guaranteed when GPU backends fail.
- Clear rules for S22/S25 and Tensor devices.

### Must NOT Have (Guardrails)
- No changes to vendored `llama.cpp` without evidence.
- No global lowering of performance defaults for all devices.
- No manual-only verification steps.

---

## Verification Strategy (MANDATORY)

### Test Decision
- **Infrastructure exists**: YES
- **User wants tests**: NO explicit tests requested → **Manual-only but agent-executable verification**
- **Framework**: adb/logcat verification

### Automated Verification (Agent-Executable Only)

**Device Identification**
```bash
adb shell getprop ro.board.platform
adb shell getprop ro.hardware
adb shell getprop ro.soc.model
# Assert: values recorded and mapped to SoC rules
```

**Backend Selection Logging**
```bash
adb logcat -s HardwareCapability:* LlmEngine:* LLM_JNI:* LLAMA_CPP:* | head -200
# Assert: contains lines indicating backend selection and mmap flag
# Example: "Selected backend: CPU", "Mmap: false", "Batch: 32"
```

**Safe Mode / Crash Recovery**
```bash
adb shell run-as com.synapsenotes.ai cat shared_prefs/ai_prefs.xml | grep safeMode
# Assert: safeMode toggles true after crash, false after success
```

**CPU Fallback Success**
```bash
adb logcat -s LlmEngine:* | grep "Model loaded successfully"
# Assert: Active Backend: CPU when GPU fails
```

---

## Execution Strategy

### Parallel Execution Waves

**Wave 1 (Start Immediately)**
- Task 1: Diagnostic log capture plan
- Task 2: SoC detection mapping and backend override matrix

**Wave 2 (After Wave 1)**
- Task 3: Timeout + fallback strategy
- Task 4: Verification checklist

Critical Path: Task 1 → Task 2 → Task 3 → Task 4

---

## TODOs

- [x] 0. **Append late librarian findings (if any)**

  **What to do**:
  - Review pending librarian research and add any new device-specific constraints.
  - Update SoC→backend matrix if new incompatibilities are found.

  **Must NOT do**:
  - Do not introduce new constraints without sources.

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 2 (only if new findings alter matrix)
  - **Blocked By**: None

  **Acceptance Criteria**:
  - [x] Any new constraints are cited with links
  - [x] Matrix updated if needed


- [x] 1. **Define diagnostic log capture procedure**

  **What to do**:
  - Provide adb commands for SoC ID capture (ro.board.platform, ro.hardware, ro.soc.model).
  - Specify logcat filters for LLM tags and backend selection.
  - Define expected log markers for backend/mmap/Batch/Ctx.

  **Must NOT do**:
  - No manual instructions like "user checks".
  - No changes to code in this task.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Documentation/procedure definition.
  - **Skills**: none
  - **Skills Evaluated but Omitted**:
    - `playwright`: Not needed (no browser tasks).

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 3, Task 4
  - **Blocked By**: None

  **References**:
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt` — log messages for backend attempts and success.
  - `app/src/main/cpp/native-lib.cpp` — native log tags and backend init logging.
  - `app/src/main/java/com/synapsenotes/ai/core/ai/DefaultHardwareCapabilityProvider.kt` — detection logic references for SoC names.

  **Acceptance Criteria (adb/logcat)**:
  - [x] adb commands listed for SoC detection
  - [x] logcat filter command defined
  - [x] log patterns for backend/mmap/batch/ctx provided

---

- [x] 2. **Create SoC→backend override matrix (S22/S25 + Tensor)**

  **What to do**:
  - Enumerate known problematic SoCs from native + Kotlin detection (s5e9925, sm8750, sm8450, sm8550).
  - Add Tensor SoCs to override plan (gs201, gs101, zuma) with rationale (Pixel 7 FE hang).
  - Define backend order overrides for S22/S25 (e.g., CPU-first or OpenCL-first).
  - Ensure OpenCL only considered on Snapdragon 8 Gen 3/Elite per official docs.

  **Must NOT do**:
  - Do not remove existing blocklist entries.
  - Do not force Vulkan on any flagged device.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Device-specific logic definition and policy.
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 3, Task 4
  - **Blocked By**: None

  **References**:
  - `app/src/main/cpp/native-lib.cpp` — `is_problematic_vulkan_device()` and detection logic.
  - `app/src/main/java/com/synapsenotes/ai/core/ai/DefaultHardwareCapabilityProvider.kt` — SoC detection and backend probe logic.
  - OPENCL support: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md

  **Acceptance Criteria**:
  - [x] Matrix includes S22/S25 + Tensor SoCs
  - [x] Backend order override defined for S22/S25
  - [x] OpenCL usage restricted to supported SoCs

---

- [x] 3. **Define load-time timeout + fallback strategy**

  **What to do**:
  - Specify timeout guard for `loadModel` and `loadEmbeddingModel` (e.g., 60s).
  - Define behavior on timeout (mark backend failed, retry with next backend).
  - Ensure safe mode and crash detection remain intact.

  **Must NOT do**:
  - No changes to Safe Mode logic; do not remove backend attempt flags.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (after Wave 1)
  - **Blocks**: Task 4
  - **Blocked By**: Task 1, Task 2

  **References**:
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt` — loadModel / loadEmbeddingModel entry points.
  - `app/src/main/java/com/synapsenotes/ai/MainViewModel.kt` — Safe Mode behavior.

  **Acceptance Criteria**:
  - [x] Timeout duration documented
  - [x] Backend failure + fallback behavior defined
  - [x] Safe Mode compatibility noted

---

- [x] 4. **Create verification checklist (emulator + devices)**

  **What to do**:
  - Provide adb/logcat commands to confirm backend selection and mmap/batch/ctx values.
  - Include device-specific success criteria for S22, S25 FE, and Pixel 7 FE.
  - Include emulator sanity check.

  **Must NOT do**:
  - No manual testing steps.

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2
  - **Blocks**: None (final)
  - **Blocked By**: Task 3

  **References**:
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt` — success log lines.
  - `app/src/androidTest/java/com/synapsenotes/ai/tests/VulkanBuildTest.kt` — native lib presence check.
  - `app/src/androidTest/java/com/synapsenotes/ai/core/ai/HardwareCapabilityInstrumentedTest.kt` — on-device hardware checks.

  **Acceptance Criteria**:
  - [x] Each device has explicit adb commands and expected log patterns
  - [x] Emulator sanity check defined

---

## Commit Strategy

No commits in planning phase.

---

## Success Criteria

### Verification Commands
```bash
adb shell getprop ro.board.platform
adb logcat -s HardwareCapability:* LlmEngine:* LLM_JNI:* LLAMA_CPP:* | head -200
```

### Final Checklist
- [x] Diagnostic log capture procedure included
- [x] SoC override matrix defined for S22/S25 + Tensor devices
- [x] Timeout + fallback strategy documented
- [x] Device verification checklist complete
