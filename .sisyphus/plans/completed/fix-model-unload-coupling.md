# Fix Chat/Embedding Model Unload Coupling

## TL;DR

> **Quick Summary**: Decouple chat and embedding model lifecycles so switching chat models doesn't unload the embedding model. Add granular unload methods and separate state tracking.
> 
> **Deliverables**:
> - Granular `unloadChat()` and `unloadEmbedding()` methods at all layers (C++/JNI/Kotlin)
> - Separate state tracking: `isChatLoaded` and `isEmbeddingLoaded`
> - Fixed `embed()` guard to check embedding state, not chat state
> - Bug fix: removing redundant global unload call
> 
> **Estimated Effort**: Medium
> **Parallel Execution**: YES - 2 waves (C++/JNI in Wave 1, Kotlin in Wave 2)
> **Critical Path**: Task 1 → Task 3 → Task 4

---

## Context

### Original Request
> When we change the chat model it also offloads the embedding model and it causing error every time we load the model. Check if the other loaded or not and then load the latest one or keep the embedding model consistent and separated from chat model.

### Interview Summary
**Key Findings**:
- Bug caused by tight coupling at 3 layers: LlmEngine.kt, LlmSession.h, native-lib.cpp
- `LlmEngine.loadModel()` calls `llmContext.unload()` which destroys BOTH models
- Native layer's individual load functions are correct (only unload their respective model)
- Single `isLoaded` boolean incorrectly tracks both model types together
- `embed()` function checks `isLoaded` (chat state) but uses embedding model

**Research Findings**:
- `loadModelNative` (lines 382-389): Only unloads chat model - CORRECT
- `loadEmbeddingModelNative` (lines 290-297): Only unloads embedding model - CORRECT
- `unload()` (lines 819-829): Destroys everything - intended for full cleanup
- Problem: Kotlin layer calls global `unload()` before loading new chat model

### Metis Review
**Identified Gaps** (addressed):
- `embed()` guard bug: Checks `isLoaded` (chat) but needs embedding model - **FIXED in plan**
- Need granular unload APIs for future flexibility - **INCLUDED in plan**
- Missing acceptance criteria - **ADDED with adb/logcat verification**
- `release()` semantics unclear - **DECISION: Keep as "unload all"**

---

## Work Objectives

### Core Objective
Fix the model lifecycle coupling so that loading a new chat model does not affect the embedding model, and vice versa.

### Concrete Deliverables
- `app/src/main/cpp/LlmSession.h`: Add `unloadChat()` and `unloadEmbedding()` methods
- `app/src/main/cpp/native-lib.cpp`: Add JNI wrappers for granular unload
- `app/src/main/java/com/synapsenotes/ai/core/ai/LlamaContext.kt`: Declare native methods
- `app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt`: Add interface + implementation
- `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt`: Split state, fix guards, selective unload

### Definition of Done
- [ ] Loading chat model B after chat model A + embedding loaded: embedding NOT reloaded
- [ ] `embed()` works when only embedding model is loaded (no chat model)
- [ ] `completion()` works when only chat model is loaded (no embedding model)
- [ ] All existing functionality preserved

### Must Have
- Granular `unloadChat()` method at all layers
- Granular `unloadEmbedding()` method at all layers
- Separate `isChatLoaded` and `isEmbeddingLoaded` state flags
- Fixed `embed()` to check embedding state, not chat state
- Fixed `completion()`/`completionFlow()` to check chat state

### Must NOT Have (Guardrails)
- DO NOT modify `loadModelNative()` in C++ - it's already correct
- DO NOT modify `loadEmbeddingModelNative()` in C++ - it's already correct
- DO NOT change the existing `unload()` semantics (keep as "unload all")
- DO NOT add new state management patterns or classes
- DO NOT touch `HardwareCapabilityProvider`
- DO NOT add memory optimization or backend selection changes
- DO NOT add model status callbacks or events (scope creep)
- DO NOT create a unified ModelManager class (scope creep)

---

## Verification Strategy (MANDATORY)

> **UNIVERSAL RULE: ZERO HUMAN INTERVENTION**
>
> ALL tasks in this plan MUST be verifiable WITHOUT any human action.

### Test Decision
- **Infrastructure exists**: YES (project has test structure)
- **Automated tests**: YES (tests-after) - verify new behavior with unit tests
- **Framework**: JUnit 5 + Mockito (per AGENTS.md convention)

### Agent-Executed QA Scenarios (MANDATORY - ALL tasks)

These describe how the executing agent DIRECTLY verifies the deliverable.

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately):
├── Task 1: Add granular unload methods to C++ LlmSession.h
└── Task 2: Add JNI wrappers in native-lib.cpp (can start after Task 1 header is done)

Wave 2 (After Wave 1):
├── Task 3: Update Kotlin LlmContext interface and LlamaContext implementation
└── Task 4: Refactor LlmEngine state and guards (depends on Task 3)

Wave 3 (After Wave 2):
└── Task 5: Add unit tests for new behavior
```

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|---------------------|
| 1 | None | 2, 3 | None |
| 2 | 1 | 3 | None |
| 3 | 2 | 4 | None |
| 4 | 3 | 5 | None |
| 5 | 4 | None | None (final) |

### Agent Dispatch Summary

| Wave | Tasks | Recommended Agents |
|------|-------|-------------------|
| 1 | 1, 2 | Sequential execution (tight dependency) |
| 2 | 3, 4 | Sequential execution (tight dependency) |
| 3 | 5 | Can run after Wave 2 completes |

---

## TODOs

- [x] 1. Add granular unload methods to LlmSession.h

  **What to do**:
  - Add `unloadChat()` method that only frees `model` and `context` (chat model)
  - Add `unloadEmbedding()` method that only frees `model_embed` and `context_embed`
  - Keep existing `unload()` method unchanged (it's the "unload everything" escape hatch)

  **Must NOT do**:
  - DO NOT modify the existing `unload()` method
  - DO NOT change the member variable declarations

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Single file, clear pattern to follow (copy from existing unload())
  - **Skills**: []
    - No special skills needed - straightforward C++ modification

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 1 - Sequential start
  - **Blocks**: Task 2, Task 3
  - **Blocked By**: None (can start immediately)

  **References** (CRITICAL):

  **Pattern References**:
  - `app/src/main/cpp/LlmSession.h:23-43` - Existing `unload()` method pattern to follow

  **Implementation Details**:
  ```cpp
  // Add after line 43, before line 44 (Chat Model State comment)
  void unloadChat() {
      std::lock_guard<std::mutex> lock(session_mutex);
      if (context) {
          llama_free(context);
          context = nullptr;
      }
      if (model) {
          llama_model_free(model);
          model = nullptr;
      }
      gpu_enabled = false;
      chat_template.clear();
  }

  void unloadEmbedding() {
      std::lock_guard<std::mutex> lock(session_mutex);
      if (context_embed) {
          llama_free(context_embed);
          context_embed = nullptr;
      }
      if (model_embed) {
          llama_model_free(model_embed);
          model_embed = nullptr;
      }
  }
  ```

  **Acceptance Criteria**:
  - [ ] `unloadChat()` method added to LlmSession class
  - [ ] `unloadEmbedding()` method added to LlmSession class
  - [ ] Both methods use `std::lock_guard<std::mutex> lock(session_mutex)`
  - [ ] `unloadChat()` sets `context=nullptr`, `model=nullptr`, `gpu_enabled=false`, clears `chat_template`
  - [ ] `unloadEmbedding()` sets `context_embed=nullptr`, `model_embed=nullptr`
  - [ ] Existing `unload()` method unchanged

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify LlmSession.h contains new methods
    Tool: Bash (grep)
    Preconditions: File exists at app/src/main/cpp/LlmSession.h
    Steps:
      1. grep -n "void unloadChat()" app/src/main/cpp/LlmSession.h
      2. Assert: Output contains line number and method signature
      3. grep -n "void unloadEmbedding()" app/src/main/cpp/LlmSession.h
      4. Assert: Output contains line number and method signature
      5. grep -c "void unload()" app/src/main/cpp/LlmSession.h
      6. Assert: Count is exactly 1 (original unchanged)
    Expected Result: Both new methods exist, original unload() preserved
    Evidence: Grep output captured
  ```

  **Commit**: YES
  - Message: `fix(ai): add granular unloadChat and unloadEmbedding methods to LlmSession`
  - Files: `app/src/main/cpp/LlmSession.h`
  - Pre-commit: N/A (header file, no standalone compilation)

---

- [x] 2. Add JNI wrappers for granular unload in native-lib.cpp

  **What to do**:
  - Add `Java_com_synapsenotes_ai_core_ai_LlamaContext_unloadChat` JNI function
  - Add `Java_com_synapsenotes_ai_core_ai_LlamaContext_unloadEmbedding` JNI function
  - Register both new methods in `JNI_OnLoad` alongside existing methods
  - Follow exact pattern of existing `unload()` JNI wrapper

  **Must NOT do**:
  - DO NOT modify existing `unload()` JNI function
  - DO NOT modify `loadModelNative` or `loadEmbeddingModelNative`
  - DO NOT change any backend selection logic

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Pattern-based addition, copy existing unload() wrapper
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 1 - After Task 1
  - **Blocks**: Task 3
  - **Blocked By**: Task 1

  **References** (CRITICAL):

  **Pattern References**:
  - `app/src/main/cpp/native-lib.cpp:819-829` - Existing `unload()` JNI wrapper pattern
  - `app/src/main/cpp/native-lib.cpp:187-195` - JNI method registration pattern in JNI_OnLoad

  **Implementation Details**:
  
  Add after line 168 (before `JNIEXPORT jboolean JNICALL Java_com_synapsenotes_ai_core_ai_NativeLib_probeBackendNative`):
  ```cpp
  JNIEXPORT void JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_unloadChat(JNIEnv* env, jobject);
  JNIEXPORT void JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_unloadEmbedding(JNIEnv* env, jobject);
  ```

  Add implementations after line 829 (after existing unload):
  ```cpp
  extern "C" JNIEXPORT void JNICALL
  Java_com_synapsenotes_ai_core_ai_LlamaContext_unloadChat(JNIEnv* env, jobject) {
      try {
          LlmSession* session = get_session();
          session->unloadChat();
          __android_log_print(ANDROID_LOG_INFO, TAG, "Chat model unloaded");
      } catch (const std::exception& e) {
          __android_log_print(ANDROID_LOG_ERROR, TAG, "Exception in unloadChat: %s", e.what());
      } catch (...) {
          __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown exception in unloadChat");
      }
  }

  extern "C" JNIEXPORT void JNICALL
  Java_com_synapsenotes_ai_core_ai_LlamaContext_unloadEmbedding(JNIEnv* env, jobject) {
      try {
          LlmSession* session = get_session();
          session->unloadEmbedding();
          __android_log_print(ANDROID_LOG_INFO, TAG, "Embedding model unloaded");
      } catch (const std::exception& e) {
          __android_log_print(ANDROID_LOG_ERROR, TAG, "Exception in unloadEmbedding: %s", e.what());
      } catch (...) {
          __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown exception in unloadEmbedding");
      }
  }
  ```

  Update JNI_OnLoad methods array (add to existing array around line 187-195):
  ```cpp
  {"unloadChat", "()V", (void*)Java_com_synapsenotes_ai_core_ai_LlamaContext_unloadChat},
  {"unloadEmbedding", "()V", (void*)Java_com_synapsenotes_ai_core_ai_LlamaContext_unloadEmbedding}
  ```

  **Acceptance Criteria**:
  - [ ] `unloadChat` JNI function implemented
  - [ ] `unloadEmbedding` JNI function implemented
  - [ ] Both registered in JNI_OnLoad methods array
  - [ ] Both log their action with `__android_log_print`
  - [ ] Both have proper exception handling (try/catch)
  - [ ] Forward declarations added for both functions

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify JNI functions exist in native-lib.cpp
    Tool: Bash (grep)
    Preconditions: File exists at app/src/main/cpp/native-lib.cpp
    Steps:
      1. grep -n "unloadChat" app/src/main/cpp/native-lib.cpp
      2. Assert: At least 3 matches (declaration, implementation, registration)
      3. grep -n "unloadEmbedding" app/src/main/cpp/native-lib.cpp
      4. Assert: At least 3 matches (declaration, implementation, registration)
    Expected Result: Both functions fully implemented and registered
    Evidence: Grep output captured

  Scenario: Verify JNI registration includes new methods
    Tool: Bash (grep)
    Steps:
      1. grep -A2 '"unloadChat"' app/src/main/cpp/native-lib.cpp
      2. Assert: Shows method signature "()V"
      3. grep -A2 '"unloadEmbedding"' app/src/main/cpp/native-lib.cpp
      4. Assert: Shows method signature "()V"
    Expected Result: Both methods registered with correct JNI signature
    Evidence: Grep output captured
  ```

  **Commit**: YES (groups with Task 1)
  - Message: `fix(ai): add JNI wrappers for granular unload methods`
  - Files: `app/src/main/cpp/native-lib.cpp`
  - Pre-commit: N/A (requires full NDK build)

---

- [x] 3. Update Kotlin LlmContext interface and implementations

  **What to do**:
  - Add `unloadChat()` and `unloadEmbedding()` to `LlmContext` interface
  - Add native method declarations to `LlamaContext` class
  - Implement interface methods in `DefaultLlmContext` class

  **Must NOT do**:
  - DO NOT modify existing `unload()` method
  - DO NOT add any business logic - just delegate to native

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Interface + implementation additions, pattern-based
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 - After Task 2
  - **Blocks**: Task 4
  - **Blocked By**: Task 2

  **References** (CRITICAL):

  **Pattern References**:
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt:15` - Existing `unload()` interface method
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt:60-64` - Existing `unload()` implementation

  **LlamaContext.kt**: `app/src/main/java/com/synapsenotes/ai/core/ai/LlamaContext.kt:24` - Existing `unload()` native declaration

  **Implementation Details**:

  In `LlmContext.kt`, add to interface (after line 15, `fun unload()`):
  ```kotlin
  fun unloadChat()
  fun unloadEmbedding()
  ```

  In `DefaultLlmContext` class, add implementations (after line 64):
  ```kotlin
  override fun unloadChat() {
      if (isLibraryLoaded()) {
          nativeContext.unloadChat()
      }
  }

  override fun unloadEmbedding() {
      if (isLibraryLoaded()) {
          nativeContext.unloadEmbedding()
      }
  }
  ```

  In `LlamaContext.kt` (need to find this file), add native method declarations:
  ```kotlin
  external fun unloadChat()
  external fun unloadEmbedding()
  ```

  **Acceptance Criteria**:
  - [ ] `LlmContext` interface has `unloadChat()` method
  - [ ] `LlmContext` interface has `unloadEmbedding()` method
  - [ ] `DefaultLlmContext` implements both methods
  - [ ] Both implementations check `isLibraryLoaded()` before calling native
  - [ ] `LlamaContext` has both `external fun` declarations
  - [ ] No LSP errors in modified files

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify LlmContext interface has new methods
    Tool: Bash (grep)
    Steps:
      1. grep -n "fun unloadChat()" app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt
      2. Assert: Method exists in interface section (before line 18)
      3. grep -n "fun unloadEmbedding()" app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt
      4. Assert: Method exists in interface section
      5. grep -c "override fun unloadChat()" app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt
      6. Assert: Count is 1 (implementation exists)
    Expected Result: Interface and implementation both updated
    Evidence: Grep output captured

  Scenario: Verify Kotlin compiles without errors
    Tool: Bash (lsp_diagnostics alternative: just check grep patterns work)
    Steps:
      1. Check file exists and has expected structure
    Expected Result: File structure is correct
    Evidence: File contents verified
  ```

  **Commit**: YES
  - Message: `fix(ai): add unloadChat and unloadEmbedding to Kotlin layer`
  - Files: `app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt`, `app/src/main/java/com/synapsenotes/ai/core/ai/LlamaContext.kt`
  - Pre-commit: N/A

---

- [x] 4. Refactor LlmEngine state management and guards

  **What to do**:
  - Replace `isLoaded: Boolean` with `isChatLoaded: Boolean` and `isEmbeddingLoaded: Boolean`
  - Update `loadModel()` to:
    - Check `isChatLoaded` instead of `isLoaded`
    - Call `llmContext.unloadChat()` instead of `llmContext.unload()`
    - Set `isChatLoaded = true` on success (NOT isLoaded)
  - Update `loadEmbeddingModel()` to:
    - Set `isEmbeddingLoaded = true` on success (add this!)
  - Fix `embed()` to check `isEmbeddingLoaded`, not `isLoaded`
  - Fix `completion()` and `completionFlow()` to check `isChatLoaded`
  - Update `release()` to call both `unloadChat()` and `unloadEmbedding()`, reset both flags

  **Must NOT do**:
  - DO NOT change the mutex pattern
  - DO NOT add new abstraction layers
  - DO NOT modify hardware/backend selection logic

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Multiple changes in one file, but all straightforward refactoring
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 - After Task 3
  - **Blocks**: Task 5
  - **Blocked By**: Task 3

  **References** (CRITICAL):

  **Pattern References**:
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt:40` - Current `isLoaded` declaration
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt:60-162` - `loadModel()` function
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt:164-244` - `loadEmbeddingModel()` function
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt:246-273` - `completionFlow()` function
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt:279-284` - `completion()` function
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt:286-291` - `embed()` function - **BUG HERE**
  - `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt:293-300` - `release()` function

  **Implementation Details**:

  Line 40 - Replace:
  ```kotlin
  private var isLoaded = false
  ```
  With:
  ```kotlin
  private var isChatLoaded = false
  private var isEmbeddingLoaded = false
  ```

  Lines 62-65 - Replace:
  ```kotlin
  if (isLoaded) {
      llmContext.unload()
      isLoaded = false
  }
  ```
  With:
  ```kotlin
  if (isChatLoaded) {
      llmContext.unloadChat()
      isChatLoaded = false
  }
  ```

  Line 134 - Replace:
  ```kotlin
  isLoaded = true
  ```
  With:
  ```kotlin
  isChatLoaded = true
  ```

  After line 218 (in loadEmbeddingModel success path) - Add:
  ```kotlin
  isEmbeddingLoaded = true
  ```

  Lines 249-251 (in completionFlow) - Replace:
  ```kotlin
  if (!isLoaded) {
  ```
  With:
  ```kotlin
  if (!isChatLoaded) {
  ```

  Line 281 (in completion) - Replace:
  ```kotlin
  if (!isLoaded) throw IllegalStateException("Model not loaded")
  ```
  With:
  ```kotlin
  if (!isChatLoaded) throw IllegalStateException("Chat model not loaded")
  ```

  Lines 287-288 (in embed) - Replace:
  ```kotlin
  if (!isLoaded) throw IllegalStateException("Model not loaded")
  ```
  With:
  ```kotlin
  if (!isEmbeddingLoaded) throw IllegalStateException("Embedding model not loaded")
  ```

  Lines 294-298 (in release) - Replace:
  ```kotlin
  if (isLoaded) {
      llmContext.unload()
      isLoaded = false
  }
  ```
  With:
  ```kotlin
  if (isChatLoaded) {
      llmContext.unloadChat()
      isChatLoaded = false
  }
  if (isEmbeddingLoaded) {
      llmContext.unloadEmbedding()
      isEmbeddingLoaded = false
  }
  ```

  **Acceptance Criteria**:
  - [ ] `isLoaded` replaced with `isChatLoaded` and `isEmbeddingLoaded`
  - [ ] `loadModel()` calls `unloadChat()` not `unload()`
  - [ ] `loadModel()` sets `isChatLoaded = true` on success
  - [ ] `loadEmbeddingModel()` sets `isEmbeddingLoaded = true` on success
  - [ ] `embed()` checks `isEmbeddingLoaded`
  - [ ] `completion()` and `completionFlow()` check `isChatLoaded`
  - [ ] `release()` unloads both and resets both flags
  - [ ] No references to old `isLoaded` variable remain

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify isLoaded is completely replaced
    Tool: Bash (grep)
    Steps:
      1. grep -c "isLoaded" app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt
      2. Assert: Count is 0 (completely replaced)
      3. grep -c "isChatLoaded" app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt
      4. Assert: Count >= 4 (declaration + usages)
      5. grep -c "isEmbeddingLoaded" app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt
      6. Assert: Count >= 3 (declaration + usages)
    Expected Result: Old variable gone, new variables present
    Evidence: Grep output captured

  Scenario: Verify embed() checks embedding state
    Tool: Bash (grep)
    Steps:
      1. grep -A3 "suspend fun embed" app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt
      2. Assert: Contains "isEmbeddingLoaded" in the guard check
    Expected Result: embed() uses correct state variable
    Evidence: Grep output captured

  Scenario: Verify loadModel uses unloadChat
    Tool: Bash (grep)
    Steps:
      1. grep "unloadChat" app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt
      2. Assert: At least 1 match in loadModel function
      3. grep "llmContext.unload()" app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt
      4. Assert: Count is 0 (removed from loadModel)
    Expected Result: loadModel uses granular unload
    Evidence: Grep output captured
  ```

  **Commit**: YES
  - Message: `fix(ai): separate chat/embedding model lifecycle in LlmEngine`
  - Files: `app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt`
  - Pre-commit: N/A

---

- [x] 5. Add unit tests for new behavior

  **What to do**:
  - Add test: loading new chat model does not call unloadEmbedding
  - Add test: embed() works when only embedding model loaded
  - Add test: completion() works when only chat model loaded
  - Add test: release() unloads both models
  - Mock `LlmContext` as per AGENTS.md testing strategy

  **Must NOT do**:
  - DO NOT test native layer (C++) - that's in llama.cpp tests
  - DO NOT add integration tests requiring actual model files
  - DO NOT modify production code in this task

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Standard unit test patterns, mockito-based
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3 - Final task
  - **Blocks**: None (final)
  - **Blocked By**: Task 4

  **References** (CRITICAL):

  **Pattern References**:
  - `app/src/main/java/com/synapsenotes/ai/core/AGENTS.md` - Testing strategy: "Test LlmEngine logic using JUnit 5 + Mockito. Mock LlmContext"

  **Test File Location**:
  - **EXISTING FILE**: `app/src/test/java/com/synapsenotes/ai/core/ai/LlmEngineTest.kt` - Extend this file with new tests (Momus verified it already exists)
  - Add new test methods to the existing class, do not create a duplicate class

  **Implementation Details**:

  ```kotlin
  package com.synapsenotes.ai.core.ai

  import kotlinx.coroutines.test.runTest
  import org.junit.jupiter.api.BeforeEach
  import org.junit.jupiter.api.Test
  import org.mockito.kotlin.*
  import kotlin.test.assertEquals
  import kotlin.test.assertFailsWith

  class LlmEngineTest {
      private lateinit var mockLlmContext: LlmContext
      private lateinit var mockHardwareProvider: HardwareCapabilityProvider
      private lateinit var engine: LlmEngine

      @BeforeEach
      fun setup() {
          mockLlmContext = mock()
          mockHardwareProvider = mock()
          // Setup default mock behavior
          whenever(mockHardwareProvider.getPreferredBackend()).thenReturn(BackendType.CPU)
          whenever(mockHardwareProvider.getRecommendedBackendOrder()).thenReturn(listOf(BackendType.CPU))
          whenever(mockHardwareProvider.getFailedBackends()).thenReturn(emptySet())
          whenever(mockHardwareProvider.getRecommendedBatchSize()).thenReturn(512)
          whenever(mockHardwareProvider.getRecommendedContextSize(any())).thenReturn(2048)
          whenever(mockHardwareProvider.isMmapSafe()).thenReturn(true)
          
          engine = LlmEngine(mockHardwareProvider, mockLlmContext)
      }

      @Test
      fun `loading chat model B after chat model A does not unload embedding`() = runTest {
          // Given: Chat model A is loaded, embedding is loaded
          whenever(mockLlmContext.loadModel(any(), any(), any(), any(), any(), any())).thenReturn(true)
          whenever(mockLlmContext.loadEmbeddingModel(any(), any(), any(), any(), any())).thenReturn(true)
          
          engine.loadModel("modelA.gguf")
          engine.loadEmbeddingModel("embed.gguf")
          
          reset(mockLlmContext) // Clear interaction history
          whenever(mockLlmContext.loadModel(any(), any(), any(), any(), any(), any())).thenReturn(true)
          
          // When: Load chat model B
          engine.loadModel("modelB.gguf")
          
          // Then: unloadChat called, unloadEmbedding NOT called
          verify(mockLlmContext).unloadChat()
          verify(mockLlmContext, never()).unloadEmbedding()
          verify(mockLlmContext, never()).unload()
      }

      @Test
      fun `embed works when only embedding model loaded`() = runTest {
          // Given: Only embedding model loaded
          whenever(mockLlmContext.loadEmbeddingModel(any(), any(), any(), any(), any())).thenReturn(true)
          whenever(mockLlmContext.embed(any())).thenReturn(floatArrayOf(0.1f, 0.2f))
          
          engine.loadEmbeddingModel("embed.gguf")
          
          // When/Then: embed() should not throw
          val result = engine.embed("test text")
          assertEquals(2, result.size)
      }

      @Test
      fun `embed throws when embedding model not loaded`() = runTest {
          // Given: No embedding model loaded (only chat)
          whenever(mockLlmContext.loadModel(any(), any(), any(), any(), any(), any())).thenReturn(true)
          engine.loadModel("chat.gguf")
          
          // When/Then: embed() should throw
          assertFailsWith<IllegalStateException> {
              engine.embed("test text")
          }
      }

      @Test
      fun `release unloads both models`() = runTest {
          // Given: Both models loaded
          whenever(mockLlmContext.loadModel(any(), any(), any(), any(), any(), any())).thenReturn(true)
          whenever(mockLlmContext.loadEmbeddingModel(any(), any(), any(), any(), any())).thenReturn(true)
          
          engine.loadModel("chat.gguf")
          engine.loadEmbeddingModel("embed.gguf")
          
          // When
          engine.release()
          
          // Then
          verify(mockLlmContext).unloadChat()
          verify(mockLlmContext).unloadEmbedding()
      }
  }
  ```

  **Acceptance Criteria**:
  - [ ] Test file created at correct location
  - [ ] Test: loading chat B after A + embedding doesn't call unloadEmbedding
  - [ ] Test: embed() works with only embedding loaded
  - [ ] Test: embed() throws when embedding not loaded
  - [ ] Test: release() calls both granular unload methods
  - [ ] All tests pass: `./gradlew test --tests "LlmEngineTest"`

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Run unit tests and verify pass
    Tool: Bash
    Preconditions: Project has gradle wrapper
    Steps:
      1. ./gradlew test --tests "*.LlmEngineTest" --info
      2. Assert: Exit code is 0
      3. Assert: Output contains "4 tests completed"
    Expected Result: All 4 tests pass
    Evidence: Gradle test output captured

  Scenario: Verify test file exists with expected tests
    Tool: Bash (grep)
    Steps:
      1. Find test file location
      2. grep -c "@Test" [test-file-path]
      3. Assert: Count >= 4
    Expected Result: Test file has at least 4 test methods
    Evidence: Grep output captured
  ```

  **Commit**: YES
  - Message: `test(ai): add unit tests for model lifecycle separation`
  - Files: `app/src/test/java/com/synapsenotes/ai/core/ai/LlmEngineTest.kt`
  - Pre-commit: `./gradlew test --tests "*.LlmEngineTest"`

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1 | `fix(ai): add granular unloadChat and unloadEmbedding methods to LlmSession` | LlmSession.h | grep verification |
| 2 | `fix(ai): add JNI wrappers for granular unload methods` | native-lib.cpp | grep verification |
| 3 | `fix(ai): add unloadChat and unloadEmbedding to Kotlin layer` | LlmContext.kt, LlamaContext.kt | grep verification |
| 4 | `fix(ai): separate chat/embedding model lifecycle in LlmEngine` | LlmEngine.kt | grep verification |
| 5 | `test(ai): add unit tests for model lifecycle separation` | LlmEngineTest.kt | ./gradlew test |

---

## Success Criteria

### Verification Commands
```bash
# Build succeeds
./gradlew assembleDebug  # Expected: BUILD SUCCESSFUL

# Unit tests pass
./gradlew test --tests "*.LlmEngineTest"  # Expected: 4 tests passed

# No references to old isLoaded
grep -r "isLoaded" app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt  # Expected: 0 matches

# New methods exist
grep "unloadChat" app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt  # Expected: 2+ matches
grep "unloadEmbedding" app/src/main/java/com/synapsenotes/ai/core/ai/LlmContext.kt  # Expected: 2+ matches
```

### Final Checklist
- [ ] All "Must Have" present (granular unload methods, separate state, fixed guards)
- [ ] All "Must NOT Have" absent (no changes to native load functions, no new abstractions)
- [ ] All tests pass
- [ ] Loading chat model B after A + embedding: embedding NOT reloaded (verified by log)
- [ ] `embed()` works when only embedding loaded
- [ ] `completion()` works when only chat loaded
