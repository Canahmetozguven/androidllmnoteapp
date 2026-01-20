# Task 15 - Settings / Model Manager Screen

## Goal
Allow users to download, manage, and load AI models.

## Reference
- `src/components/SettingsScreen.tsx`
- `src/components/ModelDownloadScreen.tsx`
- `src/services/ModelDownloadService.ts`

## Steps
1. Create `SettingsScreen` in Compose.
2. Show available models (hardcoded list from config).
3. Add download button per model.
4. Display progress using WorkManager updates.
5. Add "Load Model" button after download.

## Output
- User can download models and initialize LLM.

## Notes
Model download size is large; ensure UI is clear about storage.
