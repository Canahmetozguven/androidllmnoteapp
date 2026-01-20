# Task 08 - Note Use Cases

## Goal
Encapsulate note operations as use cases in the domain layer.

## Use Cases
- `GetNotesUseCase`
- `SaveNoteUseCase`
- `DeleteNoteUseCase`

## Steps
1. Create use case classes under `domain/usecase`.
2. Inject `NoteRepository` into each use case.
3. Define clear input/output types.

## Output
- Reusable business logic classes, ready to be called from ViewModels.

## Notes
Keep use cases small and single-purpose.
