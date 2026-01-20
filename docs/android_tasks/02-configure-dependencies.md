# Task 02 - Configure Dependencies

## Goal
Set up all required libraries and versions in the Gradle dependency catalog.

## Why This Matters
Consistency and clarity in dependencies prevents build errors later and makes the project maintainable.

## Steps
1. Create or edit `gradle/libs.versions.toml`.
2. Add versions for:
   - Kotlin
   - Compose BOM
   - Room
   - Hilt
   - Coroutines
   - Navigation Compose
   - WorkManager
   - OkHttp / Retrofit
   - Markdown renderer for Compose
3. Add library entries and bundles.
4. Reference libraries in `app/build.gradle.kts`.
5. Sync Gradle and ensure no dependency conflicts.

## Output
- Project sync succeeds.
- All libraries appear in Gradle dependency graph.

## Notes
Prefer modern, stable versions and avoid alpha builds unless necessary.
