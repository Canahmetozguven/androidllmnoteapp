# Fix Plan: Google Drive Settings + Notes Done + Drive Download

## TL;DR

> **Quick Summary**: Fix three bugs by preserving Drive connection state in SettingsViewModel, wiring the Notes “Done” button to save/exit edit mode and close the keyboard, and propagating Drive download errors instead of silently returning null—without touching any AI model code. All changes are TDD with existing JUnit5/MockK/Turbine test infrastructure.
> 
> **Deliverables**:
> - Stable Drive connection state in Settings (shows “Sync” after reopen)
> - Notes Done button saves, exits edit mode, hides keyboard
> - Drive download failures surfaced with actionable message + logs
> 
> **Estimated Effort**: Medium
> **Parallel Execution**: YES — 2 waves
> **Critical Path**: Task 1 → Task 2 → Task 3

---

## Context

### Original Request
Fix these issues in the app:
1) Settings Google Drive shows “Connect” after reopening app/settings (should show “Sync” if already connected)
2) Notes Done button not working — should save, close keyboard, and exit edit mode without leaving the screen
3) Can’t download from Google Drive; failure is silent
Do not change AI model code. Use TDD.

### Interview Summary
**Key Discussions**:
- Drive connection must persist across restarts; display “Sync” when already connected.
- Download failure is silent across all devices and file types.
- Done button should save and exit edit mode, close keyboard, stay on the same screen.
- It’s OK to add non‑AI logging around Drive download.
- After download, remain on the same screen (no new screen yet).
- Test infrastructure exists; use JUnit 5 + MockK/Turbine; Compose UI test if needed.

**Research Findings**:
- `SettingsViewModel.checkGoogleSignInStatus()` runs on init, but `refreshModelStatus()` resets `SettingsUiState` with default `isGoogleDriveConnected=false`.
- `AppPreferences` persists `isDriveConnected` and `driveEmail` correctly.
- `NoteDetailScreen` Done button in `FormattingToolbar` has no click handler.
- `GoogleDriveRepository.downloadFile()` catches exceptions and returns null; `FilesViewModel` shows a generic failure.

### Metis Review
**Identified Gaps (addressed in plan)**:
- Need explicit guardrails to avoid AI model changes, new screens, retry/offline logic.
- Need clear acceptance criteria for state persistence, Done behavior, and download error handling.
- Need decisions on error message UX and re-auth flow existence.

---

## Work Objectives

### Core Objective
Stabilize Google Drive connection state, fix Notes Done behavior, and surface Drive download failures—while keeping AI model code untouched and using TDD.

### Concrete Deliverables
- Settings Drive state persists and shows “Sync” after restart
- Done button saves, hides keyboard, exits edit mode
- Drive download returns structured error to UI with logging and user feedback

### Definition of Done
- [ ] Unit tests for settings state persistence pass
- [ ] Notes Done behavior verified via unit/UI tests
- [ ] Drive download errors surfaced (not silent) with a user-visible message
- [ ] No changes in AI model modules

### Must Have
- Preserve Drive connection state across app restart using existing preferences
- Done button saves + exits edit mode + hides keyboard
- Drive download errors propagated and logged

### Must NOT Have (Guardrails)
- No modifications to AI model code or llama-related components
- No new screens or navigation flows
- No retry/offline download feature additions
- Do not refactor unrelated settings/model lists

---

## Verification Strategy (TDD)

### Test Decision
- **Infrastructure exists**: YES
- **User wants tests**: YES (TDD)
- **Framework**: JUnit 5 + MockK/Turbine for unit tests; Compose UI Test only if necessary

### TDD Structure
Each task follows RED → GREEN → REFACTOR. Run unit tests via Gradle:

```bash
./gradlew testDebugUnitTest
```

---

## Execution Strategy

### Parallel Execution Waves

Wave 1 (Start Immediately):
├── Task 1: Settings Drive state persistence (tests + fix)
└── Task 2: Notes Done behavior (tests + fix)

Wave 2 (After Wave 1):
└── Task 3: Drive download error propagation & logging (tests + fix)

Critical Path: Task 1 → Task 3

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|----------------------|
| 1 | None | 3 | 2 |
| 2 | None | None | 1 |
| 3 | 1 | None | 2 |

---

## TODOs

> Implementation + Test = ONE Task. Every task must include references and acceptance criteria.

- [x] 1. **Persist Drive connection state in SettingsViewModel**

  **What to do**:
  - Write failing unit test asserting `isGoogleDriveConnected` remains true after init + refresh.
  - Update `SettingsViewModel.refreshModelStatus()` to preserve existing state via `.copy(...)` instead of overwriting defaults.
  - Ensure `checkGoogleSignInStatus()` result is not wiped by subsequent refresh.

  **Must NOT do**:
  - Do not alter sign-in flow or permissions logic
  - Do not change AI model status logic beyond state preservation

  **Recommended Agent Profile**:
  - **Category**: `business-logic`
    - Reason: State handling + ViewModel logic
  - **Skills**: `git-master` (optional), none required
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: not needed for ViewModel logic

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 2)
  - **Blocks**: Task 3
  - **Blocked By**: None

  **References**:
  - `app/src/main/java/com/synapsenotes/ai/feature/settings/SettingsViewModel.kt` — init flow + `refreshModelStatus()` logic that resets state
  - `app/src/main/java/com/synapsenotes/ai/core/preferences/AppPreferences.kt` — persisted `isDriveConnected`, `driveEmail`
  - `app/src/test/java/com/synapsenotes/ai/feature/chat/ChatViewModelTest.kt` — example of JUnit5 + MockK + coroutine testing pattern

  **Acceptance Criteria (TDD)**:
  - [ ] New unit test added for SettingsViewModel connection state persistence
  - [ ] Test fails before fix, passes after fix
  - [ ] `./gradlew testDebugUnitTest` passes

- [x] 2. **Fix Notes Done button: save + exit edit mode + hide keyboard**

  **What to do**:
  - Write failing test(s) verifying Done triggers save and exits edit mode state.
  - Add click handling for Done in `NoteDetailScreen` with focus/keyboard handling.
  - Call existing `NoteDetailViewModel.saveNote()` and toggle edit mode off (no navigation).

  **Must NOT do**:
  - No navigation away from note screen
  - No new confirmation dialogs
  - No changes to AI model-related UI

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose UI interactions and focus/keyboard handling
  - **Skills**: `frontend-ui-ux` (if visual behavior needs review)
  - **Skills Evaluated but Omitted**:
    - `git-master`: not required for UI change

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 1)
  - **Blocks**: None
  - **Blocked By**: None

  **References**:
  - `app/src/main/java/com/synapsenotes/ai/feature/notes/NoteDetailScreen.kt` — Done button in `FormattingToolbar`
  - `app/src/main/java/com/synapsenotes/ai/feature/notes/NoteDetailViewModel.kt` — `saveNote()` and edit mode state
  - `app/src/androidTest/java/com/synapsenotes/ai/feature/chat/ChatUiTest.kt` — Compose UI test pattern (if needed)

  **Acceptance Criteria (TDD)**:
  - [ ] Unit or UI test verifying Done triggers save and exits edit mode
  - [ ] Keyboard hide/focus clear is executed on Done
  - [ ] `./gradlew testDebugUnitTest` (and `connectedDebugAndroidTest` if UI test added) passes

- [x] 3. **Surface Drive download failures with logging and user feedback**

  **What to do**:
  - Write failing unit test for `GoogleDriveRepository.downloadFile()` behavior to propagate errors (no silent null).
  - Change repository to return a structured `Result`/sealed error (or rethrow) instead of null.
  - Update `FilesViewModel` to handle error and show a user-facing message.
  - Add non‑AI logging around download failure.

  **Must NOT do**:
  - No retry/offline logic
  - No new screens
  - No changes to AI model code

  **Recommended Agent Profile**:
  - **Category**: `business-logic`
    - Reason: Repository error propagation + ViewModel state updates
  - **Skills**: none required
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: only minimal user message handling

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (after Task 1)
  - **Blocks**: None
  - **Blocked By**: Task 1 (state consistency)

  **References**:
  - `app/src/main/java/com/synapsenotes/ai/core/data/repository/GoogleDriveRepository.kt` — `downloadFile()` currently returns null on exception
  - `app/src/main/java/com/synapsenotes/ai/feature/files/FilesViewModel.kt` — handles null result and shows generic failure
  - `app/src/test/java/com/synapsenotes/ai/feature/chat/ChatViewModelTest.kt` — testing pattern for ViewModel/state

  **Acceptance Criteria (TDD)**:
  - [ ] Repository tests fail before fix, pass after fix
  - [ ] Errors are no longer silently swallowed; user receives a message
  - [ ] Logging includes exception + context (fileId / filename)
  - [ ] `./gradlew testDebugUnitTest` passes

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1 | `fix(settings): preserve drive connection state` | SettingsViewModel.kt + tests | ./gradlew testDebugUnitTest |
| 2 | `fix(notes): wire done button save/exit edit` | NoteDetailScreen.kt + tests | ./gradlew testDebugUnitTest |
| 3 | `fix(drive): propagate download errors` | GoogleDriveRepository.kt, FilesViewModel.kt + tests | ./gradlew testDebugUnitTest |

---

## Success Criteria

### Verification Commands
```bash
./gradlew testDebugUnitTest
```

### Final Checklist
- [x] Settings shows “Sync” after reopen when Drive previously connected
- [x] Done button saves, exits edit mode, hides keyboard
- [x] Drive download errors surface and are logged
- [x] No AI model code changed

---

## Decisions Needed

- None. Confirmed: use Toast for download error UX; no auth-error re-connect flow needed (focus on Settings persistence issue).
