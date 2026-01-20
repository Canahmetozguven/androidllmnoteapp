# Task 12 - Vector Search

## Goal
Replicate the vector search behavior using cosine similarity on embeddings.

## Reference
- `src/services/VectorSearchService.ts`

## Steps
1. Create `VectorSearchUseCase` in domain layer.
2. Fetch notes with embeddings from `NoteRepository`.
3. Call `LlmEngine.embed(query)`.
4. Compute cosine similarity for each note embedding.
5. Filter by `minSimilarity` and return top K results.

## Output
- Search returns relevant notes based on embedding similarity.

## Notes
All compute must run in background thread to avoid UI freezes.
