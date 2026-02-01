# 05 - Resources Review

## Overview
This section covers `app/src/main/res` (static resources) and `app/src/main/java/com/synapsenotes/ai/ui/theme` (Compose resources).

## Strengths
*   **Compose Theming**: The app correctly uses Jetpack Compose logic (`Theme.kt`, `Color.kt`) for styling, keeping `themes.xml` minimal.
*   **Icons**: Uses standard vector assets or Material Icons (implied by dependencies).

## Areas for Improvement

### 1. Dangerous Backup Configuration
*   **Issue**: `backup_rules.xml` includes **everything** (`<include domain="root" path="." />`, etc.).
*   **Impact**: If LLM models (which can be 2GB-8GB) are stored in the app's private storage (`filesDir`), Android Auto Backup will attempt to upload them to Google Drive. This will:
    1.  Consume the user's data cap.
    2.  Fill the user's Drive quota.
    3.  Cause backup failures due to size limits.
*   **Recommendation**: Explicitly **exclude** the model directory from backups.
    ```xml
    <exclude domain="file" path="models/" />
    ```

### 2. Hardcoded Strings (No i18n)
*   **Issue**: `strings.xml` contains only `app_name`. All UI text is hardcoded in Kotlin Composables.
*   **Impact**: The app cannot be translated to other languages (e.g., Spanish, French, Turkish) without modifying code.
*   **Recommendation**: Extract all UI strings to `strings.xml` and use `stringResource(R.string.x)` in Compose.

## Action Plan
- [ ] **Critical**: Update `backup_rules.xml` to exclude the model storage directory.
- [ ] **Enhancement**: Extract hardcoded strings to `strings.xml`.
