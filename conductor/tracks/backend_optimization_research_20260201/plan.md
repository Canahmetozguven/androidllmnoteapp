# Implementation Plan - Advanced Backend Optimization Research

## Phase 1: Codebase Audit & Baseline Analysis [checkpoint: c9411b4]
- [x] Task: Analyze `cpp_kotlin_jni_setup.md` and related source files to map the current backend selection logic.
- [x] Task: Review existing build scripts (`build_opencl.sh`, `build_vulkan.sh`) to document current compiler flags and definitions.
- [x] Task: Examine `VULKAN_OPENCL_AHB_INTEROP_REFERENCE.md` and `GGML_OPENCL_ANALYSIS.md` to establish the known baseline for AHB and OpenCL.
- [x] Task: Conductor - User Manual Verification 'Codebase Audit & Baseline Analysis' (Protocol in workflow.md)

## Phase 2: External Research (Adreno/Mali & Llama.cpp)
- [ ] Task: Research Adreno-specific OpenCL vs. Vulkan performance characteristics and known driver quirks (Qualcomm developer docs, forums).
- [ ] Task: Research Mali (Exynos/MediaTek) optimization strategies and backend preference (Arm developer docs).
- [ ] Task: Research official documentation for Snapdragon, Exynos, and other popular mobile GPU architectures regarding OpenCL/Vulkan limitations and best practices.
- [ ] Task: Deep dive into `llama.cpp` GitHub issues and PRs focusing on "AHB", "Android", "Adreno", and "Mali" to find recent upstream fixes or workarounds.
- [ ] Task: Investigate specific kernel tuning parameters for mobile GPUs within the `llama.cpp` codebase.
- [ ] Task: Conductor - User Manual Verification 'External Research' (Protocol in workflow.md)

## Phase 3: Synthesis & Artifact Creation
- [ ] Task: Create the "Performance & Stability Matrix" mapping backends to chipsets with known issues.
- [ ] Task: Compile the "Upstream Reference List" and "Annotated Code Snippets" into a technical digest.
- [ ] Task: Write the "Comprehensive Research Report" summarizing architectural findings and optimization strategies.
- [ ] Task: Draft the `plan.md` for the subsequent "Optimization Implementation" track.
- [ ] Task: Conductor - User Manual Verification 'Synthesis & Artifact Creation' (Protocol in workflow.md)
