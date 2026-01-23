# Release Audit Notes

This file summarizes app-specific review notes from the current codebase.

## Quick findings
- `SettingsScreen` shows a hardcoded app version string; updated to `BuildConfig.VERSION_NAME`.
- Theme uses a default sans-serif font and purple-centered palette; consider custom branding for store readiness.
- Manifest includes Vulkan 1.3 requirement; verify fallback path and messaging for non-Vulkan devices.
- Release build has `isMinifyEnabled = false`; assess enabling shrinker after validating rules.

## Recommended polish areas
- UI branding: custom typography, consistent icon set, stronger visual identity.
- Accessibility: verify color contrast and content descriptions for icons.
- UX copy: explain on-device processing and model sizes in onboarding/settings.
- Error handling: ensure network/download failure surfaces in UI.
- Drive sync: ensure sign-in errors and revoked permissions are handled gracefully.

## Release artifacts to prepare
- Privacy policy URL and Data Safety answers.
- Store listing assets (icon, screenshots, feature graphic).
- Internal testing build via Play Console.
