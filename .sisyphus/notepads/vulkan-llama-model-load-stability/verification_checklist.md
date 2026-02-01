# Verification Checklist

## 1. Emulator (Baseline)
**Goal**: Ensure logic doesn't break standard x86 environments.

- [ ] **Device ID**:
  ```bash
  adb shell getprop ro.hardware
  # Expect: ranchu (or similar)
  ```
- [ ] **Logs**:
  ```bash
  adb logcat -d -s LlmEngine:I HardwareCapability:I
  # Expect: "Selected backend: CPU" (unless GPU configured)
  # Expect: "Batch: 256" (Default)
  # Expect: "Mmap: true" (Default)
  ```

## 2. Galaxy S22 (Exynos 2200)
**Goal**: Verify OpenCL fallback and safety flags.

- [ ] **Device ID**:
  ```bash
  adb shell getprop ro.board.platform
  # Expect: s5e9925
  ```
- [ ] **Logs**:
  ```bash
  adb logcat -d -s LlmEngine:I HardwareCapability:I
  # Expect: "Selected backend: OPENCL" (or CPU if OpenCL missing)
  # Expect: "Mmap: false" (Override)
  # Expect: "Batch: 32" (Override)
  ```

## 3. Pixel 7a (Tensor G2)
**Goal**: Verify CPU enforcement (avoiding Vulkan/OpenCL hang).

- [ ] **Device ID**:
  ```bash
  adb shell getprop ro.board.platform
  # Expect: gs201
  ```
- [ ] **Logs**:
  ```bash
  adb logcat -d -s LlmEngine:I HardwareCapability:I
  # Expect: "Selected backend: CPU"
  # Expect: "Mmap: false" (Override)
  ```

## 4. Galaxy S25 (Snapdragon 8 Elite)
**Goal**: Verify OpenCL preference over Vulkan.

- [ ] **Device ID**:
  ```bash
  adb shell getprop ro.board.platform
  # Expect: sm8750
  ```
- [ ] **Logs**:
  ```bash
  adb logcat -d -s LlmEngine:I HardwareCapability:I
  # Expect: "Selected backend: OPENCL"
  # Expect: "Mmap: false" (Override)
  ```

## 5. Timeout Test (Any Device)
**Goal**: Verify app doesn't hang forever.

- [ ] **Simulate Hang**: (Requires code mod or bad driver)
  - Verify app UI remains responsive (spinner).
  - Verify "Model load timed out" log appears after 60s.
  - Verify "Attempting to load model with backend: CPU" appears after timeout.
