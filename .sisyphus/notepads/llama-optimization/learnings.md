# Learnings - llama-optimization

- Vulkan and OpenCL backends on Android show device/driver variability; OpenCL can be slower than CPU on Adreno/Mali, and Vulkan can be very slow on some Android GPUs.
- Qualcomm’s upstreamed OpenCL backend improves Adreno prospects, but backend selection should remain dynamic with a fallback chain (Vulkan → OpenCL → CPU) and device blocklists.
- Android builds should target arm64-v8a with `-march=armv8.7a`, rely on runtime feature detection (SME2/dotprod/int8mm), and disable OpenMP for NDK builds.
- Memory is a primary constraint: keep `n_ctx` modest (e.g., 4096), use quantized models, and expect Vulkan to increase RAM on some devices.
