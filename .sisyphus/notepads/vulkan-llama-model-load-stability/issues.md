# Issues - Vulkan Llama Stability

## Critical
- **Hang on Load**: No timeout mechanism; if native load hangs (e.g., driver lockup), app hangs forever.
- **Missing SoC Detection**: Pixel 7 FE (Tensor G2) likely not detected, attempting Vulkan/OpenCL and failing.
- **S22 Crash**: Exynos 2200 flagged as problematic but still crashing; blocklist effectiveness needs validation.

## Open Questions
- Exact SoC ID for "Pixel 7 FE" (likely `gs201`).
- Whether OpenCL is genuinely available on Exynos/Tensor (likely not).
