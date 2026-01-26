# PROJECT KNOWLEDGE BASE: FEATURE LAYER

**Location:** `app/src/main/java/com/synapsenotes/ai/feature`
**Context:** Android UI / Presentation Layer (MVVM + Jetpack Compose)

## OVERVIEW
Implementation of user-facing screens and state management using Jetpack Compose and Hilt-injected ViewModels.

## STRUCTURE
- **chat/**: `ChatScreen` / `ChatViewModel` - On-device LLM interface with RAG support.
- **notes/**: `NoteList` & `NoteDetail` / `ViewModels` - CRUD operations and AI tool integration.
- **files/**: `FilesScreen` / `FilesViewModel` - Management of local documents and models.
- **settings/**: `SettingsScreen` / `SettingsViewModel` - App configuration and privacy controls.
- **onboarding/**: `OnboardingScreen` / `OnboardingViewModel` - User intro and permission flow.

## WHERE TO LOOK
- **Entry Points**: Every feature folder contains a `*Screen.kt` (UI) and `*ViewModel.kt` (Logic).
- **State Observation**: Search for `collectAsState()` in screens to see where ViewModel data enters the UI.
- **Navigation**: Lambda parameters in Screens (e.g., `onBack: () -> Unit`) define navigation boundaries.
- **Common Components**: Shared UI elements are in `com.synapsenotes.ai.ui.components`.

## CONVENTIONS
- **State Hoisting**: Screens should be stateless; pass state in and events (lambdas) out.
- **DI**: Always use `@HiltViewModel` for ViewModels and `@Inject constructor` for dependencies.
- **Asynchronous Work**: All business logic must run in `viewModelScope` using Coroutines.
- **UI State**: Use `StateFlow` in ViewModels to expose observable state to Compose.
- **Themes**: Use `MaterialTheme.colorScheme` and `MaterialTheme.typography` for consistent styling.

## TESTING STRATEGY
- **ViewModels**: Use `JUnit 5`, `MockK`, and `CoroutineTestExtension`.
  - Mock Repositories and UseCases.
  - Verify `StateFlow` updates using `runTest` and `Turbine` (optional) or `value` check.
- **UI**: Use `ComposeTestRule` in `androidTest` for critical flows.

## ANTI-PATTERNS
- **Logic in Composables**: NEVER perform repo calls or complex logic inside a `@Composable`.
- **ViewModel Passing**: NEVER pass a ViewModel instance into sub-composables; pass data/lambdas.
- **MutableState in VM**: Prefer `MutableStateFlow` (Kotlin) over `MutableState` (Compose) in ViewModels.
- **Blocking Calls**: NEVER run heavy operations on the Main thread; use `Dispatchers.IO` via Repo/UseCase.
- **Hardcoded Strings**: Avoid hardcoded text; use `stringResource(R.string...)` where applicable.
