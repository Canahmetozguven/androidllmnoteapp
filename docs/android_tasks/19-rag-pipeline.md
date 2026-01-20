# Task 19 - RAG Pipeline

## Goal
Combine vector search results with AI prompt for context-aware responses.

## Reference
- `src/services/VectorSearchService.ts`
- `src/services/LocalLLMService.ts`

## Steps
1. When user sends a message, call `VectorSearchUseCase`.
2. Collect top K notes and format into a prompt block.
3. Combine prompt + user message into final input.
4. Send to `LlmEngine.completion()`.
5. Stream response to UI.

## Output
- AI replies with context from relevant notes.

## Notes
Limit context size to avoid exceeding model context window.
