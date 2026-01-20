# Task 11 - LLM Engine

## Goal
Expose a singleton Kotlin API for model loading and inference.

## Reference
- `src/services/DeepSeekInference.ts`
- `src/services/LocalLLMService.ts`

## Steps
1. Create `LlmEngine` singleton class.
2. Implement methods:
   - `loadModel(path)`
   - `completion(prompt)`
   - `embed(text)`
   - `release()`
3. Ensure all work runs on background threads (Dispatchers.IO).
4. Add safeguards against multiple model loads.

## Output
- Kotlin app can run inference and embeddings.

## Notes
Use proper cancellation support to avoid memory leaks.
