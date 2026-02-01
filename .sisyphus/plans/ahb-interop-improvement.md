# Vulkan/OpenCL AHB Interop Improvement Plan

## TL;DR

> **Quick Summary**: Add Android Hardware Buffer (AHB) interoperability to enable safe Vulkan/OpenCL memory sharing where supported, with strict extension checks, vendor-specific paths (ARM vs Qualcomm), and graceful fallback to current single-backend behavior.
>
> **Deliverables**:
> - Capability detection (Vulkan + OpenCL AHB extension checks)
> - Vendor-aware AHB import/export layer (ARM and/or Qualcomm)
> - Synchronization primitives (safe, minimal v1)
> - Kotlin/JNI wiring for enablement and fallback
> - Agent-executable verification checklist
>
> **Estimated Effort**: Large
> **Parallel Execution**: YES - 2–3 waves
> **Critical Path**: Capability detection → AHB interop core → Sync + fallback → Kotlin/JNI integration → Verification

---

## Context

### Original Request
User provided `VULKAN_OPENCL_AHB_INTEROP_REFERENCE.md` and requested a comprehensive improvement plan based on that reference and the current codebase.

### Interview Summary
- The reference document includes production-ready Vulkan/OpenCL interoperability using Android Hardware Buffers (AHB).
- Current codebase already builds Vulkan + OpenCL and has backend selection/fallback logic.
- Must avoid device crashes and preserve current fallback behavior.

### Research Findings
- AHB interop uses Vulkan extension `VK_ANDROID_external_memory_android_hardware_buffer` and OpenCL extensions `cl_arm_import_memory` + `cl_arm_import_memory_android_hardware_buffer` (ARM Mali).
- Qualcomm Adreno uses different extensions (e.g., `cl_qcom_android_hardware_buffer_interop`).
- The reference sample uses explicit synchronization and AHB import/export steps.

### Metis Review (Gaps Addressed)
Critical gaps identified and captured as **Decisions Needed**:
- Confirm whether AHB interop is **hybrid compute (Vulkan + OpenCL simultaneously)** or **single-backend memory optimization**.
- Define **device scope** (ARM Mali only vs include Qualcomm Adreno).
- Confirm if **vendored llama.cpp** can be modified (currently **DO NOT TOUCH** per AGENTS.md).
- Confirm **minimum Android API level** for AHB (28+ recommended).
- Define performance target (no regression vs measurable improvement).

---

## Work Objectives

### Core Objective
Integrate Vulkan/OpenCL AHB interoperability in a safe, vendor-aware way while preserving current stability and fallback logic.

### Concrete Deliverables
- AHB capability detection (Vulkan + OpenCL extension checks) with logs
- AHB interop layer (export from Vulkan, import into OpenCL, or vice versa)
- Synchronization strategy (initially conservative: fence + clFinish)
- Kotlin/JNI wiring to enable/disable AHB path based on capability checks
- Device verification checklist (ADB + logcat, no manual-only steps)

### Definition of Done
- [x] AHB extension availability is logged per device and cached
- [x] If extensions are missing, app uses existing single-backend code paths
- [x] AHB path does not modify vendored llama.cpp unless explicitly approved
- [x] Verification steps are automated and device-specific

### Must Have
- Safe fallback to current Vulkan/OpenCL/CPU behavior
- No mandatory changes to `app/src/main/cpp/llama/` unless approved
- Extension checks before any AHB usage

### Must NOT Have (Guardrails)
- No UI changes (unless explicitly requested)
- No blanket performance degradation on all devices
- No reliance on emulator-only verification for AHB (device required)

---

## Decisions Needed (CRITICAL)

1. **Goal**: Is AHB for **hybrid Vulkan+OpenCL interop** (shared buffers) or for **single-backend memory optimization**?
2. **Device Scope**: ARM Mali only (cl_arm_import_memory) or include Qualcomm Adreno (cl_qcom_android_hardware_buffer_interop)?
3. **Vendored Code**: Are changes to `app/src/main/cpp/llama/` allowed? (Current policy: **NO**.)
4. **Min Android API**: Is API 28+ acceptable for enabling AHB path?
5. **Performance Target**: “No regression” or “measurable improvement” (define metric)?

These will be represented as `[DECISION NEEDED: ...]` placeholders in the TODOs below.

---

## Verification Strategy (MANDATORY)

### Test Decision
- **Infrastructure exists**: YES (JUnit + adb)
- **User wants tests**: NO explicit request → **Manual-only but agent-executable**
- **Framework**: adb/logcat verification

### Automated Verification (Agent-Executable Only)

**Device Capability Checks**
```bash
# Vulkan extension check (logged by app):
adb logcat -d | grep -E "AHB.*Vulkan.*(available|unavailable)"

# OpenCL extension check (logged by app):
adb logcat -d | grep -E "AHB.*OpenCL.*(available|unavailable)"
```

**Fallback Behavior**
```bash
adb logcat -d | grep -E "AHB interop not supported|fallback to standard backend"
```

**AHB Path Enabled**
```bash
adb logcat -d | grep -E "AHB interop enabled|Using AHB import/export"
```

**Device Matrix (example)**
```bash
adb shell getprop ro.board.platform
adb shell getprop ro.hardware
adb shell getprop ro.soc.model
```

---

## Execution Strategy

### Parallel Execution Waves

**Wave 1 (Start Immediately)**
- Task 1: Capability detection + logging (Vulkan/OpenCL extensions)
- Task 2: Vendor detection + interop abstraction (ARM vs Qualcomm)

**Wave 2 (After Wave 1)**
- Task 3: AHB interop core (export/import path)
- Task 4: Synchronization strategy (fence + clFinish)

**Wave 3 (After Wave 2)**
- Task 5: Kotlin/JNI integration and feature gating
- Task 6: Verification checklist + device matrix

Critical Path: Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6

---

## TODOs

> Implementation + verification in each task. Every task includes references for the executor.

- [ ] 1. **Add AHB capability detection (Vulkan + OpenCL extensions)**

  **What to do**:
  - Add Vulkan extension checks for `VK_ANDROID_external_memory_android_hardware_buffer` and related requirements.
  - Add OpenCL extension checks for `cl_arm_import_memory` and `cl_arm_import_memory_android_hardware_buffer`.
  - Log availability for each extension and cache results.

  **Must NOT do**:
  - Do not enable AHB path without checks.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: cross-API capability detection and JNI logging
  - **Skills**: `git-master`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 3, Task 5
  - **Blocked By**: None

  **References**:
  - `app/src/main/cpp/native-lib.cpp` — existing dynamic OpenCL load checks and logging patterns
  - `app/src/main/cpp/external/vulkan/*` — Vulkan extension headers
  - `app/src/main/cpp/external/CL/cl_ext.h` — OpenCL extension definitions
  - `VULKAN_OPENCL_AHB_INTEROP_REFERENCE.md` — required extensions section

  **Acceptance Criteria**:
  - [ ] logcat contains "AHB Vulkan extension: available/unavailable"
  - [ ] logcat contains "AHB OpenCL extension: available/unavailable"

---

- [x] 2. **Introduce vendor-aware AHB interop abstraction**

  **What to do**:
  - Implement abstraction for ARM Mali path (cl_arm_import_memory).
  - If Qualcomm in scope: add Qualcomm-specific path (`cl_qcom_android_hardware_buffer_interop`).
  - [DECISION NEEDED: include Qualcomm path in v1?]

  **Must NOT do**:
  - Do not modify vendored `app/src/main/cpp/llama/` unless explicitly approved.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: `git-master`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 3
  - **Blocked By**: None

  **References**:
  - `VULKAN_OPENCL_AHB_INTEROP_REFERENCE.md` — OpenCL import example + extension list
  - `app/src/main/cpp/native-lib.cpp` — GPU vendor detection
  - `ANDROID_GPU_COMPUTE_STRATEGY.md` — note about zero-copy missing

  **Acceptance Criteria**:
  - [x] Vendor path selection logged (ARM vs Qualcomm vs unsupported)
  - [x] Unsupported vendors fall back to standard path

---

- [x] 3. **Implement AHB interop core (export/import path)**

  **What to do**:
  - Implement Vulkan AHB export (`vkGetMemoryAndroidHardwareBufferANDROID`).
  - Implement OpenCL import (`clImportMemoryARM`) for supported devices.
  - Ensure image/buffer tiling and allocation semantics per reference.
  - [DECISION NEEDED: use AHB for buffers only or include textures/images?]

  **Must NOT do**:
  - No modifications to llama.cpp internals unless approved.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: `git-master`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 4, Task 5
  - **Blocked By**: Task 1, Task 2

  **References**:
  - `VULKAN_OPENCL_AHB_INTEROP_REFERENCE.md` — Vulkan AHB export and OpenCL import code sections
  - `app/src/main/cpp/CMakeLists.txt` — ensure `android` lib linked for AHB

  **Acceptance Criteria**:
  - [x] Logs confirm successful AHB export/import on supported device
  - [x] On unsupported device, path is bypassed cleanly

---

- [x] 4. **Add synchronization strategy (safe v1)**

  **What to do**:
  - Implement conservative sync: Vulkan fence + OpenCL `clFinish()`.
  - Document potential upgrade path to external semaphores.

  **Must NOT do**:
  - Do not introduce complex multi-buffer pipelines in v1.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: `git-master`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 5
  - **Blocked By**: Task 3

  **References**:
  - `VULKAN_OPENCL_AHB_INTEROP_REFERENCE.md` — synchronization pattern

  **Acceptance Criteria**:
  - [x] Logs show synchronization steps for AHB path

---

- [x] 5. **Kotlin/JNI integration and feature gating**

  **What to do**:
  - Expose AHB capability to Kotlin (`HardwareCapabilityProvider`).
  - Gate AHB path behind capability + vendor checks.
  - Ensure fallback to existing behavior on failure.

  **Must NOT do**:
  - Do not alter UI unless requested.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: `git-master`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 6
  - **Blocked By**: Task 4

  **References**:
  - `app/src/main/java/com/synapsenotes/ai/core/ai/HardwareCapabilityProvider.kt`
  - `app/src/main/java/com/synapsenotes/ai/core/ai/DefaultHardwareCapabilityProvider.kt`
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt`
  - `app/src/main/cpp/native-lib.cpp`

  **Acceptance Criteria**:
  - [x] Backend selection logs mention whether AHB path was used
  - [x] Fallback path works when AHB is unavailable

---

- [x] 6. **Verification checklist + device matrix**

  **What to do**:
  - Provide adb/logcat commands for extension checks and AHB usage logs.
  - Create device matrix: at least 1 ARM Mali device, 1 Qualcomm device, 1 emulator.
  - Define expected log patterns per device.

  **Must NOT do**:
  - No manual-only steps.

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3
  - **Blocked By**: Task 5

  **References**:
  - `VULKAN_OPENCL_AHB_INTEROP_REFERENCE.md` — extension checks
  - `app/src/main/cpp/native-lib.cpp` — log tags (`LLM_JNI`, `LLAMA_CPP`)

  **Acceptance Criteria**:
  - [x] adb commands listed with expected patterns
  - [x] device matrix present with expected outcomes

---

## Commit Strategy

No commits requested in this plan.

---

## Success Criteria

### Verification Commands
```bash
adb logcat -d | grep -E "AHB|interop|OpenCL|Vulkan"
adb shell getprop ro.board.platform
adb shell getprop ro.hardware
adb shell getprop ro.soc.model
```

### Final Checklist
- [x] AHB capability checks logged
- [x] AHB path enabled only on supported devices
- [x] Fallback to standard backend confirmed
- [x] No changes to vendored llama.cpp unless approved
