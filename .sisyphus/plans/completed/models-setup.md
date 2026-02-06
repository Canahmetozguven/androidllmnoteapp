# Model Setup Fixes: Meta + Liquid (Non‑RAG) + Docs

## TL;DR

> **Quick Summary**: Fix Meta (MobileLLM‑R1‑950M) bad output and JNI null crash by aligning templates/stop sequences and guarding invalid UTF‑8. Replace Liquid LFM2 RAG model with the **normal (non‑RAG)** LFM2 model. Update all markdown docs (README + docs + AGENTS) and document CPU‑only backend behavior (build.sh).
>
> **Deliverables**:
> - Liquid **non‑RAG** model entry in `SettingsViewModel.kt`:
>   - ID: `lfm2-1.2b`
>   - URL: `https://huggingface.co/LiquidAI/LFM2-1.2B-GGUF/resolve/main/LFM2-1.2B-Q4_K_M.gguf`
>   - filename: `LFM2-1.2B-Q4_K_M.gguf`
>   - size: ~731 MB
> - Meta stop‑sequence + template alignment updates (Kotlin + JNI guard)
> - RAG bypass behavior for Liquid non‑RAG model
> - TDD tests for prompt/RAG logic and model metadata
> - Docs updates across README, docs/**, and all AGENTS.md files
>
> **Estimated Effort**: Medium
> **Parallel Execution**: YES — 2 waves
> **Critical Path**: Task 1 → Task 2 → Task 3

---

## Context

### Original Request
- Qwen3 models are working.
- Meta (MobileLLM‑R1‑950M) and Liquid model are not working; bad output (gibberish/Arabic‑like).
- Switch Liquid from RAG GGUF to **normal (non‑RAG)** LFM2 model.
- Check setup/restrictions for Meta mobile model.
- Update **all markdown docs**, root README, and **AGENTS.md** files.
- Review `build.sh` and document CPU‑only build behavior.
- Devices: **Samsung S25 FE** and **Samsung S22**; backend **CPU only**.
- Test strategy: **TDD**.

### Interview Summary
- Meta model confirmed: `facebook.MobileLLM-R1-950M` in `SettingsViewModel.kt`.
- Liquid model currently `lfm2-1.2b-rag` (RAG GGUF URL). User wants **non‑RAG** variant.
- Error noted: “parameter specified as non null is null … parameter v1 … meta error” (approximate).

### Research Findings
- **Model list** in `app/src/main/java/com/synapsenotes/ai/feature/settings/SettingsViewModel.kt` (`AVAILABLE_MODELS`).
- **RAG pipeline**: `ChatViewModel.kt` → `VectorSearchUseCase.kt` → `PromptBuilder.kt`.
- **Stop sequences** in `LlmEngine.kt` (defaults appear tuned to ChatML/Qwen).
- **JNI generation loop** in `native-lib.cpp` uses `NewStringUTF` without null guard.
- **build.sh** supports `static`, `dynamic`, `cpu_ultimate` with CPU‑only flags.
- **Liquid non‑RAG GGUF**: `LiquidAI/LFM2-1.2B-GGUF` with `LFM2-1.2B-Q4_K_M.gguf` (official HuggingFace).

### Metis Review (Gaps Addressed)
- Root cause found: invalid UTF‑8 token pieces from Meta/Liquid cause `NewStringUTF` to return null, crashing Kotlin non‑null callback.
- Template mismatch: Qwen uses ChatML; Meta/Liquid need Llama‑style template. Code falls back to ChatML.
- Missing stop sequences for Meta (`<|eot_id|>`, `<|end_of_text|>`).

---

## Work Objectives

### Core Objective
Stabilize Meta and Liquid model output (no JNI null crashes, correct stop sequences/templates), replace Liquid RAG model with **normal** LFM2 model, and document all changes with TDD coverage.

### Concrete Deliverables
- Updated `ModelInfo` entry for Liquid **non‑RAG** model (ID/URL/filename/size).
- JNI guard for invalid UTF‑8 token pieces.
- Expanded stop sequences for Meta/Llama‑style models.
- RAG bypass for non‑RAG Liquid model.
- Updated README + docs/** + AGENTS.md documentation.

### Definition of Done
- Meta and Liquid no longer crash with Kotlin non‑null parameter errors.
- Meta output is coherent (no persistent gibberish/Arabic) on CPU backend.
- Liquid model loads using **non‑RAG** GGUF.
- All tests pass (`./gradlew test` and any added androidTest).
- Docs updated across markdown files and AGENTS.

### Must Have
- No JNI null crash from `NewStringUTF`.
- Correct stop sequences for Meta/Llama‑style models.
- Liquid model switched to non‑RAG GGUF.

### Must NOT Have (Guardrails)
- Do **not** modify vendored `llama.cpp` sources.
- Do **not** hardcode device‑specific paths for models.
- Do **not** add GPU backend changes (CPU‑only is current scope).

---

## Verification Strategy (MANDATORY)

> **UNIVERSAL RULE: ZERO HUMAN INTERVENTION**
>
> All tasks must be verifiable via automated commands or agent‑executed QA. No manual testing required.

### Test Decision
- **Infrastructure exists**: YES (`app/src/test` and `app/src/androidTest` present)
- **Automated tests**: **TDD**
- **Framework**: JUnit (Kotlin unit tests) + androidTest where needed

### TDD Workflow
Each task with code changes must include RED → GREEN → REFACTOR steps.

### Agent‑Executed QA Scenarios (MANDATORY)
Each task includes explicit QA scenarios with commands and expected outputs.

---

## Execution Strategy

### Parallel Execution Waves

**Wave 1 (Start Immediately):**
- Task 1 (Discover Liquid non‑RAG GGUF + confirm token/stop sequences)
- Task 2 (TDD: Prompt/RAG behavior tests + model metadata tests)

**Wave 2 (After Wave 1):**
- Task 3 (Implement model metadata + RAG bypass + stop sequences)
- Task 4 (JNI null guard + logs)
- Task 5 (Docs: README + docs/** + AGENTS)

Critical Path: Task 1 → Task 3 → Task 4

---

## TODOs

> Implementation + Test = ONE Task. Every task includes references, acceptance criteria, and QA scenarios.

### 1) Identify Liquid **non‑RAG** LFM2 model + stop tokens

**What to do**:
- Confirm the **non‑RAG** Liquid LFM2 GGUF model metadata:
  - ID: `lfm2-1.2b`
  - URL: `https://huggingface.co/LiquidAI/LFM2-1.2B-GGUF/resolve/main/LFM2-1.2B-Q4_K_M.gguf`
  - filename: `LFM2-1.2B-Q4_K_M.gguf`
  - size: ~731 MB
- Identify any Liquid/Llama stop sequences and preferred chat template (if published in GGUF metadata or docs).

**Must NOT do**:
- Don’t assume the RAG model is “normal” — must use non‑RAG variant.

**Recommended Agent Profile**:
- **Category**: `unspecified-low`
- **Skills**: none

**Parallelization**:
- **Can Run In Parallel**: YES (Wave 1)
- **Blocks**: Task 3

**References**:
- `app/src/main/java/com/synapsenotes/ai/feature/settings/SettingsViewModel.kt:56-112` — current Liquid model entry.
- External (LiquidAI model registry / HuggingFace) — source of non‑RAG GGUF.

**Acceptance Criteria (TDD not applicable)**:
- [x] Non‑RAG Liquid model ID/URL/filename/size confirmed and recorded for Task 3.

**Agent‑Executed QA Scenario**:
```
Scenario: Confirm Liquid non‑RAG GGUF metadata
  Tool: Bash (curl)
  Preconditions: Internet access
  Steps:
    1. curl -I https://huggingface.co/LiquidAI/LFM2-1.2B-GGUF/resolve/main/LFM2-1.2B-Q4_K_M.gguf
    2. Assert HTTP status is 200
    3. Record filename and content-length
  Expected Result: URL exists and metadata captured
  Evidence: HTTP headers captured in .sisyphus/evidence/task-1-liquid-gguf-head.txt
```

---

### 2) TDD: Prompt/RAG behavior + model metadata tests

**What to do**:
- Add unit tests for `PromptBuilder` and `ChatViewModel` verifying RAG bypass when model is **non‑RAG Liquid**.
- Add unit tests for model metadata (Liquid non‑RAG entry and Meta entry) to ensure IDs/URLs are correct.

**Must NOT do**:
- Don’t write production code before tests (TDD).

**Recommended Agent Profile**:
- **Category**: `quick`
- **Skills**: none

**Parallelization**:
- **Can Run In Parallel**: YES (Wave 1)
- **Blocks**: Task 3

**References**:
- `app/src/main/java/com/synapsenotes/ai/core/ai/PromptBuilder.kt` — context injection logic.
- `app/src/main/java/com/synapsenotes/ai/feature/chat/ChatViewModel.kt:101-131` — RAG selection.
- `app/src/main/java/com/synapsenotes/ai/feature/settings/SettingsViewModel.kt:56-112` — model catalog.
- `app/src/test/java/...` — existing test patterns (e.g., `SettingsViewModelTest.kt`, `ChatViewModelTest.kt`).

**Acceptance Criteria (TDD)**:
- [x] RED: New unit tests fail before implementation.
- [ ] GREEN: `./gradlew test` passes with new tests.

**Agent‑Executed QA Scenario**:
```
Scenario: Unit tests pass
  Tool: Bash
  Preconditions: JDK/Gradle configured
  Steps:
    1. ./gradlew test
    2. Assert: BUILD SUCCESSFUL
  Expected Result: All tests pass
  Evidence: .sisyphus/evidence/task-2-gradle-test.txt
```

---

### 3) Implement model metadata updates + RAG bypass

**What to do**:
- Update `ModelInfo` entry for Liquid to **non‑RAG** model:
  - ID: `lfm2-1.2b`
  - URL: `https://huggingface.co/LiquidAI/LFM2-1.2B-GGUF/resolve/main/LFM2-1.2B-Q4_K_M.gguf`
  - filename: `LFM2-1.2B-Q4_K_M.gguf`
  - size: ~731 MB
- Add a flag (e.g., `requiresRag: Boolean`) to `ModelInfo` and enforce RAG bypass in `ChatViewModel`.
- Ensure `PromptBuilder` is used only when RAG is enabled.

**Must NOT do**:
- Don’t remove RAG for other models.

**Recommended Agent Profile**:
- **Category**: `unspecified-low`
- **Skills**: none

**Parallelization**:
- **Can Run In Parallel**: YES (Wave 2)
- **Blocked By**: Task 1, Task 2

**References**:
- `SettingsViewModel.kt:41-112` — `ModelInfo` and `AVAILABLE_MODELS`.
- `ChatViewModel.kt:101-131` — RAG decision branch.
- `PromptBuilder.kt:14-20` — context injection behavior.

**Acceptance Criteria (TDD)**:
- [x] Tests from Task 2 pass.
- [x] Liquid model entry points to non‑RAG GGUF.
- [x] RAG bypass occurs when `requiresRag=false` for Liquid model.

**Agent‑Executed QA Scenario**:
```
Scenario: Liquid non‑RAG uses direct prompt
  Tool: Bash (adb logcat)
  Preconditions: App running on device (CPU backend), Liquid model selected
  Steps:
    1. Clear logcat: adb logcat -c
    2. Send a prompt without selected notes
    3. Capture logcat entries showing prompt composition
    4. Assert: No "Context:" prefix in prompt log
  Expected Result: Prompt is user-only (no RAG context)
  Evidence: .sisyphus/evidence/task-3-liquid-nonrag-logcat.txt
```

---

### 4) Fix JNI null crash + Meta stop sequences/templates

**What to do**:
- Add **null guard** around `NewStringUTF` in `native-lib.cpp` to skip invalid UTF‑8 pieces and log a warning.
- Expand stop sequences in `LlmEngine.kt` for Meta/Llama‑style tokens (`<|eot_id|>`, `<|end_of_text|>`, etc.).
- Ensure model templates are applied (or logged) for Meta; avoid ChatML fallback for Llama‑style models.

**Must NOT do**:
- Don’t modify vendored `llama.cpp` sources.

**Recommended Agent Profile**:
- **Category**: `unspecified-high`
- **Skills**: none

**Parallelization**:
- **Can Run In Parallel**: YES (Wave 2)
- **Blocked By**: Task 2

**References**:
- `app/src/main/cpp/native-lib.cpp:653-669` — `NewStringUTF` call and callback.
- `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt:32-36` — stop sequences.
- `app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt` — template passing (verify).

**Acceptance Criteria (TDD)**:
- [x] RED: Add a failing test that asserts token handling does not crash when invalid UTF‑8 is encountered (androidTest or mocked JNI wrapper).
- [x] GREEN: JNI guard prevents null from reaching Kotlin callback.
- [x] Stop sequences list includes Meta/Llama tokens.

**Agent‑Executed QA Scenario**:
```
Scenario: Meta model generation stops without crash
  Tool: Bash (adb logcat)
  Preconditions: Meta model loaded on CPU backend
  Steps:
    1. adb logcat -c
    2. Send prompt "Hello"
    3. Observe logcat for "Invalid UTF-8 token skipped" (if any)
    4. Assert: No Kotlin NPE "parameter specified as non-null is null"
  Expected Result: Generation completes without NPE; stop sequence applied
  Evidence: .sisyphus/evidence/task-4-meta-no-npe-logcat.txt
```

---

### 5) Update documentation (README + docs/** + AGENTS.md)

**What to do**:
- Update **root README** + all markdown docs + AGENTS.md to reflect:
  - Liquid non‑RAG model change
  - Meta stop sequences/templates + CPU backend behavior
  - build.sh modes (static/dynamic/cpu_ultimate)
  - Known constraints for S25 FE / S22 (CPU‑only)

**Must NOT do**:
- Don’t remove unrelated content; update only relevant sections.

**Recommended Agent Profile**:
- **Category**: `writing`
- **Skills**: none

**Parallelization**:
- **Can Run In Parallel**: YES (Wave 2)
- **Blocked By**: Task 3 and 4 (for accurate details)

**References**:
- `README.md` (root)
- `docs/**.md` (all markdown docs)
- `**/AGENTS.md` (all agent knowledge files)
- `build.sh` (CPU‑only path: `cpu_ultimate`)

**Acceptance Criteria**:
- [x] All markdown docs updated consistently.
- [x] build.sh behavior documented.

**Agent‑Executed QA Scenario**:
```
Scenario: Docs updated list
  Tool: Bash
  Preconditions: git available
  Steps:
    1. git status -s
    2. Assert: all modified files are .md
  Expected Result: Only markdown files updated
  Evidence: .sisyphus/evidence/task-5-docs-status.txt
```

---

## Commit Strategy

| After Task | Message | Files | Verification |
|---|---|---|---|
| 2+3+4 | `fix(ai): stabilize meta/liquid models` | Kotlin + native | `./gradlew test` |
| 5 | `docs(ai): update model/setup docs` | markdown + AGENTS | none |

---

## Success Criteria

### Verification Commands
```bash
./gradlew test
```

### Final Checklist
- [ ] Liquid model entry points to **non‑RAG** GGUF
- [ ] Meta generation no longer triggers Kotlin non‑null crash
- [ ] Stop sequences include Llama‑style tokens
- [ ] RAG bypass works for Liquid non‑RAG model
- [ ] All markdown docs + AGENTS updated

---

## Decisions Needed

- None.
