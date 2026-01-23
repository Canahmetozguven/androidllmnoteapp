# Release Polish Plan

This plan focuses on finishing touches for UI/UX, stability, and release readiness before Play Store submission.

## Phase 1: UI/UX polish (highest impact)
- Unify typography and visual identity: pick a custom font family and apply to `Typography` so the app is distinct.
- Harmonize spacing and surfaces across screens: standardize section spacing, card padding, and list gaps.
- Ensure consistent empty states and error states for notes, chat, files, and model downloads.
- Audit iconography: use consistent icon style (filled/outlined) and align to 24dp for touch targets.
- Verify contrast and accessibility across light/dark themes.

## Phase 2: Product clarity
- Add short onboarding copy explaining on-device processing and model size expectations.
- Add in-app privacy and data handling summary (e.g., local notes + optional Drive sync).
- Surface current model status in Chat and Settings (active model, fallback status).
- Add explicit download/storage warnings for large GGUF model files.

## Phase 3: Reliability and edge cases
- Confirm Vulkan fallback behavior and user messaging when unsupported.
- Handle Google Drive auth errors and sign-out in Settings.
- Verify download retry behavior and network error handling.
- Ensure all long tasks show progress and allow cancellation where possible.

## Phase 4: Build + release configuration
- Confirm release signing config and ensure `keystore.properties` is excluded from VCS.
- Enable release shrinking when ready (minify + resource shrink) and test.
- Verify no debug-only dependencies or example assets are packaged.

## Phase 5: QA sweep
- Run lint + unit tests + smoke test on physical device.
- Validate first-run onboarding, note creation, model download, chat flow, and backup.
- Test on at least one device without Vulkan 1.3 to confirm fallback path.

## Outputs
- Updated UI/UX polish changes merged.
- Play Store assets and compliance items completed.
- Release candidate AAB built and installed for verification.
