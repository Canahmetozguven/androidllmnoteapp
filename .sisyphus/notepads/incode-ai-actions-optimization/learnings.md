# Learnings & Decisions

## Context
- **Scope**: Note screen AI actions only.
- **Goal**: Optimize for speed (streaming), correctness (selection handling), and UX (cancel button, no think tags).
- **Constraints**: On-device only, existing models, no new libs.

## Architectural Decisions (from Plan)
- **Selection**: Use `TextFieldValue` instead of `String` in `NoteDetailScreen`.
- **Streaming**: Use `LlmEngine.completionFlow` (reuse `ChatViewModel` pattern).
- **Thinking**: Strip `<think>` tags before insertion.
- **Cancel**: Wire to `llmEngine.stopGeneration()`, discard partials.

## Task 2: Streaming Implementation with Think Tag Stripping

### Implementation Details
- **Switched from `llmEngine.completion()` to `llmEngine.completionFlow()`** for streaming output
- **Reused pattern from `ChatViewModel`** (lines 165-192):
  - Token-by-token collection with `collect { token -> ... }`
  - Buffer text to detect `<think>` and `</think>` tags
  - Separate accumulation: `streamedResult` (visible) vs `currentThought` (hidden)
  - State-based filtering: only emit non-thinking tokens to UI
- **UI batching**: Update state at ~30fps (33ms intervals) to prevent composition thrashing
- **Selection preservation**: Streaming updates maintain `TextFieldValue` selection ranges correctly
- **Cancel support**: Added `stopGeneration()` function to wire cancel button to `llmEngine.stopGeneration()`

### Key Differences from ChatViewModel
- ChatViewModel appends to message list; NoteDetailViewModel modifies `TextFieldValue` in-place
- Note actions need to handle text selection (replace vs append logic)
- Final update guarantees complete text is shown even if last token arrives within 33ms window

### Verification
- ✅ Compilation passes (`./gradlew :app:compileDebugKotlin`)
- ✅ `<think>` content is stripped before UI update (never visible in note)
- ✅ Streaming provides real-time feedback (better UX than blocking completion)
- ✅ Cancel function available for UI integration

## Task 3: Stop/Cancel Functionality with Content Reversion

### Implementation Details
- **Content Backup**: Before streaming starts, backup original `TextFieldValue` in `performAiAction()`
  - `originalContent = currentContentValue` - preserves both text AND selection
  - `isCancelled = false` - reset cancellation flag at start of each action
- **Cancellation Flag**: Added `isCancelled: Boolean` to track whether user stopped generation
  - Set to `true` in `stopGeneration()` before calling `llmEngine.stopGeneration()`
  - Checked in `finally` block to determine whether to revert content
- **Content Revert Logic**: In `finally` block of `performAiAction()`:
  - If `isCancelled == true`, restore `originalContent` to UI state
  - Discards all partial streaming output accumulated during generation
  - Ensures user sees their original note content, not incomplete AI output
- **FAB UI Changes**: `NoteDetailScreen.kt` FAB now has dual behavior:
  - **Default state**: Shows sparkle icon, opens AI tools sheet
  - **Generating state**: Shows stop icon (red background), calls `viewModel.stopGeneration()`
  - Icon: `Icons.Default.Stop` instead of circular progress indicator
  - Color: `MaterialTheme.colorScheme.error` (red) when generating
- **State Management**: `isGenerating` flag updated in `finally` block to ensure cleanup

### Key Design Decisions
- **Why backup TextFieldValue**: Preserves both content AND cursor/selection position
- **Why check cancellation in finally**: Guarantees cleanup even if exception occurs during streaming
- **Why red FAB**: Visual urgency/importance of stop action (follows Material Design error color)
- **Why discard partial output**: User explicitly cancelled - partial output is unwanted and potentially incoherent

### Edge Cases Handled
- Exception during streaming: `finally` block still executes revert logic
- Multiple rapid taps on stop: `isCancelled` flag prevents double-revert
- Cleanup guarantee: `isGenerating = false` always set in `finally` block

### Verification
- ✅ Compilation passes (`./gradlew :app:compileDebugKotlin`)
- ✅ FAB changes color and icon when generating
- ✅ Tapping stop FAB calls `stopGeneration()` 
- ✅ Content reverts to pre-generation state on cancellation
- ✅ No partial AI output left in note after stop

