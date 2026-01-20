# Task 16 - Note List Screen

## Goal
Display all notes and entry points for search and creation.

## Reference
- `src/components/SearchModal.tsx`
- `src/storage/NoteStorage.ts`

## Steps
1. Create `NoteListScreen` in Compose.
2. Display notes in `LazyColumn` sorted by updatedAt desc.
3. Add search bar for text search.
4. Add optional toggle for vector search.
5. Add FloatingActionButton to create a new note.

## Output
- User can browse notes and start a new note.

## Notes
Text search can be implemented locally in ViewModel initially.
