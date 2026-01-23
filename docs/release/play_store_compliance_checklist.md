# Play Store Compliance Checklist

Use this checklist to prepare the app for Play Store review and ensure policy compliance.

## 1) Policy and disclosure
- Privacy policy published on a public URL (required if any data collection or account usage).
- Data Safety form completed (analytics, Drive sync, local storage behavior).
- If AI content is present, add a clear in-app disclosure and store listing mention.
- If targeting children or mixed audience, confirm compliance with Families policy.

## 2) Permissions
- INTERNET and ACCESS_NETWORK_STATE justified in store listing.
- Remove any unused permissions before release.
- Confirm Drive integration uses OAuth with minimal scopes.

## 3) App integrity
- Target SDK 34 (already configured) and use latest Play policies.
- Release build uses app signing and non-debuggable build.
- No embedded test credentials, API keys, or sample accounts.

## 4) Store listing assets
- High-res icon (512x512) and feature graphic (1024x500).
- At least 2-8 screenshots for phone; add tablet if supported.
- Short description (<= 80 chars) and full description (<= 4000 chars).
- Privacy policy URL attached to listing.

## 5) Functional testing
- Install release AAB via internal testing track.
- Verify login/Drive sync, model downloads, and chat inference.
- Validate offline behavior and edge cases (no network, no storage).

## 6) Data handling details (for Data Safety form)
- Collected: analytics events (Firebase), Drive auth email, sync metadata.
- Stored: notes content locally, optional Drive backup if enabled.
- Shared: confirm whether any data is shared with third parties.
- Data deletion: document how users can delete local data and Drive backups.

## 7) Release checks
- Version code incremented; version name updated.
- App bundle size reviewed; large assets justified.
- Signed AAB built via `./gradlew bundleRelease`.
