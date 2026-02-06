## Task 2: JNI Wrappers (Complete)

### Pattern Established
- **Forward declarations** go in the extern "C" block (lines 161-175)
- **Implementations** follow the existing `unload()` function pattern (after line 829)
- **Registration** requires 2 entries in JNI_OnLoad methods array:
  - Method name: `"unloadChat"`, signature: `"()V"`
  - Method name: `"unloadEmbedding"`, signature: `"()V"`

### Key Implementation Details
1. Both JNI functions call `get_session()` to retrieve the session pointer
2. Both call their respective `LlmSession` methods: `unloadChat()` and `unloadEmbedding()`
3. Both use try/catch blocks for exception safety
4. Logging includes "Chat model unloaded" and "Embedding model unloaded" success messages
5. Exception messages include the method name for debugging

### Verification Checklist
- ✅ unloadChat appears 6 times (declaration + implementation + logging + registration)
- ✅ unloadEmbedding appears 6 times (declaration + implementation + logging + registration)
- ✅ Both JNI signatures are correct: `()V` (void return, no parameters)
- ✅ Both functions have proper exception handling
- ✅ Commit created with detailed message

### Next Task
Task 3: Update Kotlin layer (LlmContext.kt and LlamaContext.kt interfaces)

## Task 5: Unit Tests (Complete)

### Test Implementation Patterns
1. **Argument Matchers**: Use specific matchers for clarity
   - `anyString()` for String parameters
   - `anyOrNull()` for nullable parameters
   - `anyInt()` for Int parameters
   - `any()` for complex types (BackendType, Boolean)
   - Example: `llmContext.loadModel(anyString(), anyOrNull(), anyInt(), anyInt(), any(), any())`

2. **Mock Setup Completeness**
   - Hardware capability mocks must be comprehensive (all backend-related methods)
   - Missing mocks cause silent failures in model loading
   - Key methods: `getPreferredBackend()`, `getRecommendedBackendOrder()`, `getFailedBackends()`, `isMmapSafe()`, `getRecommendedContextSize()`, `getRecommendedBatchSize()`
   - Embedding-specific: `getPreferredEmbeddingBackend()`, `getRecommendedEmbeddingBatchSize()`

3. **Test Isolation**
   - Avoid `reset()` between load operations in the same test - it can cause verification issues
   - Instead, design separate test instances for different scenarios

4. **Verification Patterns**
   - Use `verify(mock).method()` to confirm calls were made
   - Use `verify(mock, never()).method()` to confirm calls were NOT made
   - Use `verify(mock, times(n)).method()` for specific call counts

### Tests Added (All Passing)
✅ `loading chat model B after chat model A does not unload embedding()`
   - Verifies lifecycle separation: loading new chat model calls unloadChat() but NOT unloadEmbedding()
   
✅ `embed works when only embedding model loaded()`
   - Verifies embedding can work independently with just embedding model loaded
   
✅ `embed throws when embedding model not loaded()`
   - Verifies proper error handling when embedding unavailable
   
✅ `release unloads both models()`
   - Verifies release() properly unloads both chat and embedding models

### Integration Notes
- Tests use JUnit 5 + Mockito + kotlin-test
- All tests use `runTest {}` for coroutine context
- Follows existing test patterns in LlmEngineTest class
- Compatible with `./gradlew test` execution
