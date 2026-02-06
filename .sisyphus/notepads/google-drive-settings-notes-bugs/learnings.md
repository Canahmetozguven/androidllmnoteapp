
## Task 3: Drive Download Error Surfacing - Phase 1 (RED)

### Discovery: DriveError doesn't propagate message properly
- `DriveError` sealed class extends `Throwable()` without passing message
- `DriveError.UnknownError` stores details in `details` property, NOT in `Throwable.message`
- Result: `exception?.message` returns `null` instead of error details

### Test Design
- Test: `downloadFile returns failure Result with error details when Drive API throws exception`
- Uses MockK `spyk` to intercept private `getDriveService()` method
- Simulates IOException("Network unreachable") during download
- Asserts that `exception?.message` should contain the error details

### RED State Confirmed
- Test fails with: `Error message should preserve original exception details, got: null`
- This proves the bug: error messages are NOT being surfaced to callers
- Fix (GREEN phase): Update `DriveError` to pass message to `Throwable()` constructor

### File Modified
- `app/src/test/java/com/synapsenotes/ai/core/data/repository/GoogleDriveRepositoryTest.kt`

## Task 3: Drive Download Error Surfacing - Phase 2 (GREEN)

### Fix Applied
- `DriveError.UnknownError` now overrides `message` property to return `details`
- Syntax: `override val message: String get() = details`

### Why This Approach
- Alternative was passing `details` to `Throwable(details)` superclass constructor
- But sealed class syntax `DriveError()` would need refactoring for all subclasses
- Overriding `message` getter is minimal, surgical fix that preserves class structure

### Existing Code Already Correct
- `GoogleDriveRepository.downloadFile` already returned `Result.failure(DriveError.UnknownError(...))`
- `FilesViewModel.importFile` already handled `onFailure` with logging and UI state updates
- Only missing piece was `Throwable.message` propagation

### GREEN State Confirmed
- `./gradlew testDebugUnitTest --tests "GoogleDriveRepositoryTest"` - BUILD SUCCESSFUL
- All 5 tests pass including the new error details assertion
