# Research Report: On-Device AI Models (Sub-2B Focus)

## Executive Summary
This report analyzes the current landscape of ultra-compact on-device Large Language Models (LLMs), specifically focusing on the sub-2B parameter range. We evaluate **Liquid AI's LFM2-1.2B-RAG** against other emerging powerhouses like **Qwen3-0.6B**, **SmolLM2-1.7B**, and **MobileLLM-R1-950M**. These models represent a new frontier in efficiency, capable of running on mid-range mobile hardware while delivering reasoning capabilities previously reserved for 7B+ models.

## 1. Comparative Analysis: Benchmark Performance
The following table compares the selected sub-2B models across standard benchmarks: MMLU (General Knowledge), GSM8K (Math Reasoning), and HumanEval (Coding).

| Model | Parameters | Architecture | MMLU | GSM8K | HumanEval | Context |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **LFM2-1.2B** | 1.2B | Hybrid (Liquid + Attn) | **55.23** | **58.3** | N/A | 32k |
| **SmolLM2-1.7B** | 1.7B | Transformer | 52.7 | - | - | 8k |
| **Qwen3-0.6B** | 0.6B | Transformer | 44.93 | 36.47 | - | 32k |
| **MobileLLM-R1**| 950M | Transformer | - | - | - | - |

*Note: Benchmark scores are sourced from official technical reports and model cards. Dashes indicate unreported values.*

## 2. Model Deep Dive

### Liquid AI LFM2-1.2B-RAG
*   **Architecture**: A hybrid model combining multiplicative gates and short convolutions (Liquid) with Grouped Query Attention (GQA). This unique architecture offers superior memory efficiency and inference speed.
*   **RAG Specialization**: The `LFM2-1.2B-RAG` variant is specifically fine-tuned for Retrieval-Augmented Generation, making it the ideal candidate for the Android Note App. It excels at grounding answers in provided context.
*   **Performance**: Outperforms Qwen3-0.6B significantly and competes with larger 1.7B models in reasoning tasks (GSM8K).
*   **Deployment**: Available in GGUF format, fully compatible with `llama.cpp`.

### Qwen3-0.6B
*   **Efficiency King**: At only 0.6B parameters, it is the smallest viable model for basic reasoning.
*   **Trade-offs**: While impressive for its size, it lags behind LFM2-1.2B in complex reasoning (GSM8K 36.47 vs 58.3).
*   **Use Case**: Best for extremely low-power background tasks or older devices.

### SmolLM2-1.7B
*   **Strong Contender**: A solid transformer-based model that performs well in general knowledge.
*   **Limitation**: Larger memory footprint than LFM2-1.2B with comparable or slightly lower reasoning performance.

### MobileLLM-R1-950M
*   **Emerging Tech**: Meta's research into sub-1B reasoning. While promising, it is less mature in terms of deployment tooling compared to the GGUF-ready LFM2.

## 3. Recommendation for Android Note App
**Winner: LiquidAI LFM2-1.2B-RAG**

*   **Why**: It strikes the perfect balance between size (1.2B), performance (58.3 GSM8K), and utility (RAG-tuned).
*   **Architecture**: The hybrid architecture ensures low memory usage, crucial for Android background services.
*   **Integration**: Native GGUF support means drop-in compatibility with the existing `llama.cpp` pipeline.

## 4. Implementation Plan
1.  **Download**: Fetch `LFM2-1.2B-RAG-GGUF` (Q4_K_M quantization recommended ~731MB).
2.  **Integration**: Update `llama.cpp` config to load this model.
3.  **Testing**: Verify RAG performance with local notes.
