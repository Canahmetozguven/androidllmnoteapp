# Task 05 - Create Note DAO

## Goal
Provide Room queries for note CRUD operations.

## Reference
- `src/storage/NoteStorage.ts`

## Required Methods
- `getAll()` — returns notes ordered by updatedAt DESC.
- `getById(id)` — return single note.
- `insert(note)` — insert or replace.
- `delete(id)` — delete note.

## Steps
1. Create `NoteDao` interface under `core/database`.
2. Annotate with `@Dao`.
3. Write SQL queries to match the existing ordering and behavior.

## Output
- DAO compiles and exposes required queries.

## Notes
Use `@Insert(onConflict = REPLACE)` to match JS behavior.
