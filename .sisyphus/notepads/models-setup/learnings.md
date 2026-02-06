# Learnings - Models Setup

## Project Context
- **Backend**: CPU-only focus for S25 FE / S22.
- **Models**: Qwen3 (working), Meta MobileLLM (bad output/crash), Liquid LFM2 (switching to non-RAG).
- **Crash**: JNI `NewStringUTF` returning null on invalid UTF-8 (likely from Meta/Liquid generation).
- **Output**: Bad output on Meta/Liquid due to template mismatch (ChatML fallback vs Llama-3 style) and missing stop sequences.

## Conventions
- **TDD**: Write tests first (Red -> Green).
- **Docs**: Update all markdown docs and AGENTS.md.

## Liquid LFM2-1.2B Model Metadata (Non-RAG)

### Model Information
- **Model Name**: LFM2-1.2B
- **Type**: Hybrid model (non-RAG) - Generalist language model
- **Architecture**: 16 layers (10 conv + 6 attention blocks with GQA)
- **Parameters**: 1,170,340,608 (1.2B)
- **Context Length**: 32,768 tokens
- **Vocabulary Size**: 65,536 tokens
- **Precision**: bfloat16

### GGUF Quantized Version
- **URL**: `https://huggingface.co/LiquidAI/LFM2-1.2B-GGUF/resolve/main/LFM2-1.2B-Q4_K_M.gguf`
- **Filename**: `LFM2-1.2B-Q4_K_M.gguf`
- **Quantization**: Q4_K_M (4-bit)
- **Size**: 730,893,248 bytes (731 MB / ~697 MiB)
- **HTTP Status**: 200 (OK) - Verified accessible
- **Content-Disposition**: `inline; filename*=UTF-8''LFM2-1.2B-Q4_K_M.gguf; filename="LFM2-1.2B-Q4_K_M.gguf";`

### Chat Template & Stop Tokens
- **Format**: ChatML-like template
- **Special Tokens**:
  - Start: `<|startoftext|>`
  - Turn markers: `<|im_start|>`, `<|im_end|>`
  - Roles: `system`, `user`, `assistant`, `tool`
  - Tool tokens: `<|tool_list_start|>`, `<|tool_list_end|>`, `<|tool_call_start|>`, `<|tool_call_end|>`, `<|tool_response_start|>`, `<|tool_response_end|>`
- **Stop Tokens** (inferred from chat template):
  - `<|im_end|>` - Primary turn/message end token
  - `<|endoftext|>` - End of sequence (standard)
  
### Recommended Generation Parameters
- **Temperature**: 0.3
- **Min_p**: 0.15
- **Repetition Penalty**: 1.05
- **Max Tokens**: 512 (default recommendation)

### Supported Languages
English, Arabic, Chinese, French, German, Japanese, Korean, Spanish

### Use Cases
Best for: agentic tasks, data extraction, RAG, creative writing, multi-turn conversations
Not recommended for: knowledge-intensive tasks, programming

### Verification Date
February 5, 2026 - URL accessible, metadata validated via curl headers

## TDD Test Implementation (Task 2)

### Test Files
- **ModelMetadataTest.kt**: 6 tests validating model metadata
- **ChatViewModelRagTest.kt**: 4 tests validating RAG bypass behavior

### RED State Tests (Currently Failing)

#### 1. ModelMetadataTest - Non-RAG Validation
**Test**: `AVAILABLE_MODELS Liquid LFM2 variant does NOT require RAG()`
- **Current State**: FAILING ✗
- **Expected**: `requiresRag = false` for lfm2-1.2b
- **Actual**: `requiresRag = true` (default in ModelInfo)
- **Error**: `expected: <false> but was: <true>`

#### 2. ChatViewModelRagTest - RAG Bypass Verification
**Test**: `sendMessage with non-RAG model Liquid bypasses vector search()`
- **Current State**: FAILING ✗
- **Expected**: vectorSearchUseCase NOT called for requiresRag=false models
- **Actual**: vectorSearchUseCase is called (ChatViewModel always calls it)
- **Error**: `VectorSearchUseCase.invoke(Tell me about yourself) should not be called`

### Test Strategy
1. **TDD Approach**: Write tests first, make them fail (RED), then implement logic
2. **ModelInfo**: Already has `requiresRag: Boolean = true` property
3. **ChatViewModel**: Already has RAG bypass logic (lines 123-134) checking `model?.requiresRag`
4. **Current Issue**: AVAILABLE_MODELS.lfm2-1.2b still has `requiresRag = true`

### Next Steps (Task 3)
1. Update AVAILABLE_MODELS to set `requiresRag = false` for lfm2-1.2b
2. Verify tests pass (GREEN state)
3. Consider other models (qwen3, mobilellm remain RAG-enabled)


## RAG Bypass Implementation (Task 3)

### Implementation Details
- Added `requiresRag: Boolean = true` property to `ModelInfo` data class in `SettingsViewModel.kt`
- Updated `lfm2-1.2b` model entry with `requiresRag = false` to disable RAG for this model
- Implemented conditional RAG logic in `ChatViewModel.sendMessage()`:
  - Checks active model's `requiresRag` property via `AVAILABLE_MODELS.find { it.id == activeModelId }`
  - Defaults to `true` if model not found (safe fallback)
  - Only calls `vectorSearchUseCase()` when `shouldRag == true` and no manually selected notes
  - Passes empty notes list to `promptBuilder` when RAG is bypassed

### Test Coverage
- `ModelMetadataTest`: Verifies `requiresRag` property exists and is correctly set
- `ChatViewModelRagTest`: 
  - `sendMessage with non-RAG model Liquid bypasses vector search`: Verifies no vectorSearch call for lfm2-1.2b
  - `sendMessage with RAG-enabled model calls vector search`: Verifies vectorSearch is called for qwen3-0.6b
  - Fixed outdated test that expected lfm2 to use RAG (was incorrect based on requirements)

### Key Patterns
- Feature flag pattern: Model metadata drives runtime behavior without code changes
- Safe defaults: Unknown models default to RAG-enabled (safer for most use cases)
- Manual override: User-selected notes always take precedence over RAG

## JNI Null Guard and Meta Stop Sequences - 2026-02-06

### Changes Made
1. **JNI Null Guard**: Verified `jPiece == nullptr` check already exists in `native-lib.cpp` at line 664. This guards against crashes when `NewStringUTF` returns null due to invalid UTF-8 or allocation failures.

2. **Meta Llama Stop Sequences**: Added three critical stop tokens to `LlmEngine.kt`:
   - `<|start_header_id|>`: Llama 3 system/user/assistant role delimiter (opening)
   - `<|end_header_id|>`: Llama 3 role delimiter (closing)
   - `<|python_tag|>`: Tool calling marker used in Llama 3 models

### Rationale
- **Null Guard**: Essential for production stability. The existing check at line 664 prevents native crashes when tokenization produces malformed output.
- **Stop Tokens**: Meta Llama 3 models use special header tokens for role separation. Without these in the stop sequence list, the model can leak role markers into generated text or fail to stop at proper boundaries.
- **`<|eot_id|>` and `<|end_of_text|>`**: Already present. These are the primary EOS tokens for Llama 3.

### Verification
- JNI guard confirmed via `grep "jPiece == nullptr" app/src/main/cpp/native-lib.cpp`
- Stop sequences confirmed via `grep "<|start_header_id|>" app/src/main/java/com/synapsenotes/ai/core/ai/LlmEngine.kt`

### Impact
- Prevents role marker leakage in Llama 3 completions
- Maintains backward compatibility (other models ignore unknown stop tokens)
- No performance impact (stop sequence checks are O(n) over a small constant set)
