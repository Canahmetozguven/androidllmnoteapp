# Specification: Fix Google Drive "Key Error" and Setup Issues

## Overview
This track addresses a critical failure in the Google Drive integration where users encounter "key errors" or authentication failures. The goal is to audit the entire setup—from the Google Cloud Console configuration to the local Android app implementation—to ensure a robust and functional sync feature.

## Functional Requirements
- **Authentication Audit**: Verify that the Google Sign-In flow correctly handles credentials and tokens.
- **Configuration Validation**: Ensure `google-services.json` and API keys are correctly integrated into the build system.
- **Error Handling**: Implement specific error handling for common "Key Error" scenarios (missing JSON keys, invalid API keys, or unauthorized access).
- **Service Verification**: Ensure `GoogleDriveService` can successfully initialize and perform basic operations (list files/upload).

## Non-Functional Requirements
- **Security**: Maintain privacy-first principles by ensuring API keys are not exposed in logs or version control incorrectly.
- **Observability**: Add meaningful logging for the authentication and sync lifecycle.

## Acceptance Criteria
- Successful Google Sign-In within the app.
- Successful connection to Google Drive without "Key Error" crashes or popups.
- Verification via UI, `logcat` success tokens, and (if feasible) automated instrumentation tests.

## Out of Scope
- Implementing new sync features beyond fixing the existing connection.
- Modifying the AI/LLM inference logic.
