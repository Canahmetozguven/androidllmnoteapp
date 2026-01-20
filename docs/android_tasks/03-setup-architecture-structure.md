# Task 03 - Setup Architecture Structure

## Goal
Create the base package structure and wire up dependency injection.

## Why This Matters
A clean, predictable module structure keeps feature work isolated and avoids long-term technical debt.

## Steps
1. Create package hierarchy:
   - `core` (data, database, ai, network, di, util)
   - `domain` (model, usecase)
   - `feature` (notes, chat, settings, onboarding)
   - `ui` (theme)
2. Add Hilt setup:
   - Add `@HiltAndroidApp` Application class.
   - Register in `AndroidManifest.xml`.
3. Add base DI module placeholder under `core/di`.

## Output
- Project compiles with Hilt enabled.
- Package structure visible in Android Studio.

## Notes
No functional logic yet, just scaffolding.
