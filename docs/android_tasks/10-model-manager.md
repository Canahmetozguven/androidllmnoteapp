# Task 10 - Model Manager

## Goal
Handle discovery of downloaded model files and metadata.

## Reference
- `src/services/ModelManager.ts`

## Steps
1. Create `ModelManager` in `core/ai` or `core/data`.
2. Provide method `isModelAvailable(modelId)`.
3. Store models under `/files/models/` directory.
4. Validate file existence and size.

## Output
- Model availability can be checked before inference.

## Notes
The RN app checks for `.gguf` files by name. Replicate this logic.
