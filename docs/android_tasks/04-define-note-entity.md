# Task 04 - Define Note Entity

## Goal
Create the Room entity matching the React Native data model.

## Reference (React Native)
- `src/storage/NoteStorage.ts`

## Fields
- `id`: String (UUID)
- `title`: String?
- `content`: String?
- `createdAt`: Long
- `updatedAt`: Long
- `tags`: List<String>
- `embedding`: List<Float>?

## Steps
1. Create `NoteEntity` in `core/database`.
2. Annotate with `@Entity(tableName = "notes")`.
3. Add a primary key on `id`.
4. Create `TypeConverters` for:
   - `List<String>` (tags)
   - `List<Float>` (embedding)

## Output
- Room compiles with the NoteEntity and converters.

## Notes
Ensure converters handle empty/null values safely.
