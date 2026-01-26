# Technology Stack - Synapse Notes AI

## Core Mobile Platform
- **Language**: Kotlin 1.9.22 (JVM)
- **UI Framework**: Jetpack Compose with Material 3
- **Dependency Injection**: Hilt (Dagger)
- **Asynchronous Programming**: Kotlin Coroutines & Flow

## AI & Local Inference Engine
- **Inference Library**: `llama.cpp` integrated via JNI
- **Hardware Acceleration**: 
    - **Vulkan API**: Primary high-performance backend for modern Android GPUs.
    - **OpenCL**: Secondary acceleration backend for Adreno and Mali GPUs to maximize compatibility.
- **Model Format**: GGUF (various quantization levels)

## Data & Persistence
- **Local Database**: Room (SQLite) for notes, metadata, and vector embeddings.
- **Vector Search**: Local vector similarity search (integrated with Room/SQLite).
- **File Storage**: Internal app storage for downloaded GGUF models and binary assets.

## Networking & Integration
- **API Client**: Retrofit 2 with OkHttp 4
- **Cloud Sync**: Google Drive API for secure, user-owned backups.
- **Auth**: Google Sign-In for Drive access.

## Build & Infrastructure
- **Android Build System**: Gradle 8.2+ (Kotlin DSL)
- **Native Build System**: CMake 3.22.1 with NDK 26.1.10909125
- **Cross-Compilation**: WSL2 (Ubuntu) environment for high-performance SPIR-V and C++ compilation.
