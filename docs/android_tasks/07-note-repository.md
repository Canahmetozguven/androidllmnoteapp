# Task 07 - Note Repository

## Goal
Create a repository interface and implementation for note operations.

## Reference
- `src/storage/NoteStorage.ts`

## Steps
1. Define interface `NoteRepository` in `domain`.
2. Implement `NoteRepositoryImpl` in `core/data`.
3. Map between `NoteEntity` and `Note` domain model.
4. Expose Flow or suspend functions.

## Output
- Domain layer is independent of Room/Android classes.

## Notes
Keep mapping logic centralized to prevent duplication.
