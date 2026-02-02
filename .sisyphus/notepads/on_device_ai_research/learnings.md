## Sub-2B model benchmark comparison (MMLU / GSM8K / HumanEval)

Source: https://huggingface.co/facebook/MobileLLM-R1-950M (Evaluation table, base models; GSM8K=8-shot EM, HumanEval=0-shot pass@1, MMLU=5-shot accuracy)

| Model | Params | MMLU (5-shot acc) | GSM8K (8-shot EM) | HumanEval (0-shot pass@1) |
| --- | --- | --- | --- | --- |
| Qwen3-0.6B-base | 596M | 52.4 | 60.9 | 30.5 |
| SmolLM2-1.7B-base | 1.71B | 50.0 | 31.8 | 0.6 |
| MobileLLM-R1-950M-base | 949M | 47.4 | 61.6 | 46.3 |

Notes:
- The only publicly listed MMLU/GSM8K/HumanEval values found for these exact sizes in a single source are from the MobileLLM-R1 model card’s base-model comparison table.
- If you need instruct/post-trained scores instead, we’ll need to switch sources (those benchmarks are not reported together there).
