# Task 21 - Performance Tuning

## Goal
Ensure the app is stable and responsive under heavy AI workloads.

## Steps
1. Profile memory usage with model loaded.
2. Measure cold start time.
3. Optimize vector search to run off main thread.
4. Add caching for embeddings if needed.

## Output
- App remains usable without freezes or crashes.

## Notes
If OOM occurs, reduce model size or use lower quantization.
