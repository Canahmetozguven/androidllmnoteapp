# Fix Native Build Error in native-lib.cpp

## Problem
The build failed with `native-lib.cpp:383:1: error: conflicting types for 'Java_com_synapsenotes_ai_core_ai_LlamaContext_completion'`.
This was caused by a mismatch between the forward declaration (line 144) and the actual implementation (line 383) of the JNI function `Java_com_synapsenotes_ai_core_ai_LlamaContext_completion`.

- **Forward Declaration (Old):** Missing `system_prompt` and `stop_sequences` arguments.
- **Implementation:** Included these arguments to match the Java `native` method signature.

## Solution
Updated the forward declaration in `app/src/main/cpp/native-lib.cpp` to match the implementation signature.

### Changes
- **File:** `app/src/main/cpp/native-lib.cpp`
- **Line 144:**
  ```cpp
  // Old
  JNIEXPORT jstring JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_completion(JNIEnv* env, jobject, jstring prompt, jobject callback);
  
  // New
  JNIEXPORT jstring JNICALL Java_com_synapsenotes_ai_core_ai_LlamaContext_completion(JNIEnv* env, jobject, jstring prompt, jstring system_prompt, jobjectArray stop_sequences_array, jobject callback);
  ```

## Verification
1.  **Manual Inspection:** Verified that the new forward declaration argument list matches the implementation at line 383 and the `RegisterNatives` signature at line 171.
    - Implementation: `(JNIEnv* env, jobject, jstring prompt, jstring system_prompt, jobjectArray stop_sequences_array, jobject callback)`
    - RegisterNatives: `(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Lcom/synapsenotes/ai/core/ai/LlmCallback;)Ljava/lang/String;` matches the 4 Java args + 2 JNI args.

## Next Steps
- Run the build script in WSL to confirm the compilation error is resolved.
- `wsl bash -c "./build_vulkan.sh"`
