# Decisions - Vulkan Llama Stability

## Strategy
- **Diagnose First**: Capture adb logs + SoC IDs before changes.
- **Timeout**: Implement 60s timeout for model loading to escape hangs.
- **Device Overrides**:
    - **S22/S25**: Disable mmap, enforce device-specific backend order.
    - **Tensor**: Add to blocklist if confirmed unsupported.
- **Verification**: Adb-based commands only.

## Architecture
- Keep `LlmEngine` as orchestrator.
- Keep `native-lib.cpp` as source of truth for Vulkan flags.
- Sync detection logic between Kotlin and C++.
