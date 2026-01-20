# Task 20 - Testing

## Goal
Validate core logic and data integrity.

## Steps
1. Unit tests:
   - `cosineSimilarity` math.
   - Note repository CRUD (using in-memory DB).
2. Instrumentation tests:
   - Room database read/write.
   - Migration validation (if version > 1).

## Output
- Tests pass in CI or local run.

## Notes
Focus on correctness in core logic before UI tests.
