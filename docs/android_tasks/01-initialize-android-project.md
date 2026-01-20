# Task 01 - Initialize Android Project

## Goal
Create the Kotlin/Android project foundation that will host the ported application.

## Why This Matters
All later work (UI, database, AI services) depends on a stable Android project with consistent package naming, SDK versions, and build tooling. Mistakes here cascade across the whole project.

## Prerequisites
- Android Studio (latest stable)
- JDK 17
- Android SDK installed

## Steps
1. Create a new Android Studio project.
   - Template: Empty Activity
   - Language: Kotlin
   - Minimum SDK: 26 (Android 8.0)
   - Target SDK: 34
2. Name the application and package.
   - Suggested package: `com.example.llmnotes`
   - Set application name to "On-Device LLM Notes".
3. Verify Gradle setup.
   - Use Kotlin DSL (`build.gradle.kts`).
   - Ensure Gradle sync completes without errors.
4. Enable Jetpack Compose.
   - Confirm `compose = true` in build features.
   - Confirm Compose Compiler version via BOM.

## Output
- Android project builds successfully with a blank screen.
- `MainActivity` exists and is set as launcher.

## Notes
Do not add any application code yet. This task is only about project scaffolding.
