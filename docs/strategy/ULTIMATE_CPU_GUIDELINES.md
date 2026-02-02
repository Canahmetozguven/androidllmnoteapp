# Android LLM Ultimate Build Guidelines (Feb 2026)

## 🚀 The Paradigm Shift: CPU Beats GPU
Recent benchmarks on high-end Android SoCs (Snapdragon 8 Elite, Dimensity 9400) have debunked the myth that GPU is always faster for LLM inference. Due to massive driver overhead and memory bandwidth saturation, a **highly optimized CPU path** using ARMv8.6+ instructions significantly outperforms both OpenCL and Vulkan.

### Key Research Findings
| Metric | CPU (Ultimate Build) | GPU (OpenCL/Vulkan) |
| :--- | :--- | :--- |
| **Throughput (LFM2-8B)** | **15.3 tok/s** | 4.8 tok/s |
| **Overhead** | Minimal (Direct Cache Access) | High (Host-to-Device Copy) |
| **Stability** | 100% (Native Logic) | 70% (Driver Hangs/Lost Device) |
| **Battery Drain** | Moderate | High |

---

## 🏗️ The "Ultimate" Build Setup
To achieve these speeds, you must use the **I8MM (Int8 Matrix Multiply)** and **BF16** instruction sets introduced in ARMv8.6-A.

### 1. Mandatory Compiler Flags
These flags enable the "Jet Engine" for quantized LLMs:
```bash
-march=armv8.6-a+i8mm+bf16+dotprod -O3 -flto=thin
```
- **i8mm**: Accelerates 8-bit integer matrix multiplication (used in Q4_K_M, Q8_0).
- **bf16**: Optimized BFloat16 support.
- **dotprod**: Mandatory for modern tensor math.

### 2. How to Build (Synapse Notes)
Use the unified build script with the `cpu_ultimate` target:
```bash
./build.sh cpu_ultimate
```
*Note: This automatically disables GGML_OPENCL and GGML_VULKAN to prevent hybrid offloading overhead.*

---

## ⚙️ Runtime Optimal Settings
Even with a perfect build, misconfiguration can kill performance.

### 1. Thread Count (`-t`)
**DO NOT** use all available cores. Using efficiency cores for heavy math slows down the Prime/Performance cores.
- **Snapdragon 8 Elite**: `-t 6` (2 Prime + 4 Performance)
- **Dimensity 9400**: `-t 8` (All Big Cores)
- **Snapdragon 8 Gen 3**: `-t 5`

### 2. GPU Offloading (`-ngl`)
**Set to 0.** Even offloading a few layers triggers the memory synchronization bottleneck that ruins token generation speed.

### 3. Quantization Strategy
- **Best Balance**: `Q4_K_M` (Optimized for I8MM).
- **Maximum Intelligence**: `IQ4_XS` (Uses I8MM effectively).

---

## 📱 SoC-Specific Matrix
| Family | Best Configuration | Performance Target (8B Model) |
| :--- | :--- | :--- |
| **Snapdragon Elite** | CPU + I8MM + `-t 6` | **12–18 tok/s** |
| **Dimensity 9400** | CPU + I8MM + `-t 8` | **15–20 tok/s** |
| **Exynos 2400** | CPU + I8MM + `-t 4` | **8–12 tok/s** |
| **Tensor G4** | CPU + I8MM + `-t 4` | **5–8 tok/s** |

---

## 👑 Best Models for Android (Feb 2026)

### 1. The Speed Demon: LFM2-8B (Liquid)
- **Throughput**: **15-18 tok/s** (SD 8 Elite CPU)
- **Best For**: Real-time chat, summarization, simple tasks.
- **Why**: Non-attention architecture bypasses memory bottlenecks.

### 2. The Smart Choice: Qwen2.5-7B-Instruct
- **Throughput**: **14-16 tok/s** (SD 8 Elite CPU)
- **Best For**: Reasoning, logic, complex instructions.
- **Why**: Beats Llama-3.1 in reasoning benchmarks while maintaining high speed on I8MM.
- **Note**: **Must** limit context (`-c 8192`) on devices with <12GB RAM to prevent OOM.

### 3. The Specialist: Qwen2.5-Coder-7B
- **Throughput**: **15+ tok/s**
- **Best For**: Coding tasks, structured data extraction (JSON).
- **Why**: SOTA coding performance for its size.

### 4. General Purpose: Llama-3.1-8B-Instruct
- **Throughput**: 10-12 tok/s
- **Best For**: General knowledge, roleplay.

---

## ⚡ Best Compact Models (< 3B)
For older devices or background tasks, these models fly on CPU.

### 1. The New King: DeepSeek-R1-Distill-Qwen-1.5B
- **Throughput**: **40-50 tok/s** (SD 8 Elite CPU)
- **Best For**: Complex reasoning, logic puzzles, math.
- **Why**: Distilled from a 671B model. Beats Llama-3.2-3B in logic while running 2x faster.
- **Note**: Outputs `<think>` tags. Hide these in UI for cleaner chat.

### 2. The Sweet Spot: Qwen2.5-3B-Instruct
- **Throughput**: **25-30 tok/s**
- **Best For**: Daily assistant tasks, RAG on low-RAM devices (4-6GB).
- **IQ**: Punches way above its weight, rivaling older 7B models.

### 3. The Standard: Llama-3.2-3B-Instruct
- **Throughput**: **22-26 tok/s**
- **Best For**: Function calling, following strict formats.

---

## 📸 Multimodal (Vision) Support
**Qwen2-VL-7B** is fully supported and recommended for image analysis.
- **Performance**: ~2s image processing latency.
- **Setup**: Requires the `mmproj` (vision projector) file alongside the model.

---

## 🛠️ Troubleshooting
- **Libomp missing**: If the app fails with a `libomp.so` error, ensure you have bundled the NDK's `libomp.so` into your APK (handled by the latest `build.sh`).
- **Slow generation**: Verify that "Power Saving Mode" is OFF. Android will throttle the I8MM instructions to save energy.
- **Stuttering**: If rooted, set CPU governor to `performance`.

---
*Created: Feb 2, 2026*
*Source: Community Benchmarks & Adreno Driver Analysis*
