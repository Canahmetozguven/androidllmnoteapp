# Task 06 - Setup Room Database

## Goal
Configure the Room database with entities and DAOs.

## Steps
1. Create `AppDatabase` abstract class.
2. Register `NoteEntity` and `NoteDao`.
3. Add database version (start at 1).
4. Add `@TypeConverters` for tags and embeddings.
5. Create Hilt module to provide singleton instance.

## Output
- Room database builds and can be injected into repositories.

## Notes
No migration logic needed at version 1.
