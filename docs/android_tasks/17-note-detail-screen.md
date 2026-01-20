# Task 17 - Note Detail / Edit Screen

## Goal
Allow creating and editing note content.

## Reference
- `src/components/AIChat.tsx` (for AI button patterns)
- `src/storage/NoteStorage.ts`

## Steps
1. Create `NoteDetailScreen` with:
   - Title TextField
   - Content TextField
2. Add "Save" action (auto-save can be added later).
3. Add "Generate / Complete" button to use AI on selected text.
4. Save embeddings when content changes.

## Output
- User can create, edit, and save notes.

## Notes
Embedding generation should be debounced to avoid heavy work on every keystroke.
