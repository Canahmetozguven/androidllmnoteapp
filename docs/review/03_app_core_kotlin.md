# 03 - App Core (Kotlin) Review

## Overview
This section covers `app/src/main/java/com/synapsenotes/ai`, focusing on `core/ai` (LLM integration) and `feature/chat`.

## Strengths
*   **Architecture**: The app follows a clear **MVVM + Clean Architecture** pattern with Hilt dependency injection.
*   **Robustness**: `DefaultHardwareCapabilityProvider` implements a sophisticated fallback mechanism (`Vulkan` -> `OpenCL` -> `CPU`) and handles specific SoC quirks (e.g., limiting batch size on Snapdragon 8 Gen 1).
*   **Defensive Programming**: `mainViewModel` uses a "Safe Mode" flag in `SharedPreferences` to detect crash loops during model loading and disable auto-load on the next run.
*   **Modern Concurrency**: `LlmEngine` uses Kotlin Coroutines, `Mutex` for thread safety, and `callbackFlow` for streaming responses.

## Areas for Improvement

### 1. Manual Prompt Construction in ViewModel
*   **Issue**: `ChatViewModel` constructs the prompt via string concatenation (`$contextString\n$text`).
*   **Impact**: Hard to maintain, hard to test, and inflexible for supporting different model templates (e.g., Llama 3 vs Mistral vs ChatML).
*   **Recommendation**: Extract prompt logic into a `PromptManager` or `ModelTemplate` class in the Domain layer.

### 2. Primitive Context Truncation
*   **Issue**: Context is truncated using `take(10000)` (character count).
*   **Impact**: This effectively cuts off the context effectively at an arbitrary point, potentially splitting words or logic. It assumes ~2.5 chars/token, but it's inexact.
*   **Recommendation**: Implement a smarter truncation (at least to the nearest newline or paragraph) or expose a `tokenize` method from JNI to count real tokens.

### 3. Navigation Logic
*   **Issue**: Navigation graph definition is monolithic inside `MainActivity`.
*   **Recommendation**: Move the `NavHost` definition to a separate Composable (`AppNavigation`) to improve readability of `MainActivity`.

## Action Plan
- [ ] **Refactor**: Move prompt construction out of `ChatViewModel` into a `PromptBuilder` class.
- [ ] **Enhancement**: Improve context truncation logic (avoid cutting words).
- [ ] **Refactor**: Extract `NavHost` from `MainActivity`.
