# Implementation Plan: Fix Google Drive "Key Error" and Setup Issues

## Phase 1: Environment & Configuration Audit [checkpoint: 531f734]
- [x] Task: Audit Google Cloud Console Project
    - [x] Verify Google Drive API and Google Picker API are enabled.
    - [x] Confirm OAuth 2.0 Client IDs match the Android package name.
    - [x] Verify SHA-1 fingerprints (Debug and Release) are correctly registered.
- [x] Task: Validate Local Build Configuration
    - [x] Check `google-services.json` placement and content integrity.
    - [x] Verify `build.gradle.kts` has correct Google Services plugin and dependency versions.
    - [x] Ensure API keys are correctly referenced (not hardcoded if possible).
- [x] Task: Conductor - User Manual Verification 'Phase 1: Environment & Configuration Audit' (Protocol in workflow.md)

## Phase 2: Service & Authentication Fixes
- [ ] Task: Implement Robust Error Handling in `GoogleDriveService`
    - [ ] Write failing tests for common "Key Error" scenarios (missing keys, unauthorized).
    - [ ] Implement `try-catch` blocks and specific error logging for the auth flow.
    - [ ] Refactor service initialization to be more resilient.
- [ ] Task: Fix Google Sign-In Flow
    - [ ] Write failing tests for token retrieval and account selection.
    - [ ] Implement fixes for detected authentication bottlenecks or misconfigurations.
    - [ ] Verify successful token exchange with Google Drive scopes.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Service & Authentication Fixes' (Protocol in workflow.md)

## Phase 3: Verification & Monitoring
- [ ] Task: Create Instrumentation Tests for Drive Connection
    - [ ] Write a basic `AndroidJUnit4` test to verify `GoogleDriveService` connectivity.
    - [ ] Mock external dependencies where necessary to simulate success/failure.
- [ ] Task: Final Manual Verification & Logging Audit
    - [ ] Perform end-to-end sync test via the app UI.
    - [ ] Audit `logcat` to ensure no sensitive keys are leaked while success tokens are visible.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Verification & Monitoring' (Protocol in workflow.md)
