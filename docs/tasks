# Android Port Implementation Plan

## 1. Project Overview
**Goal**: Port the existing React Native "On-Device LLM Notes" application to a native Android application using Kotlin.
**Core Philosophy**: "Native-first UX", "Offline-only", "Full Feature Parity".
**Target Audience**: Developers with little to no prior knowledge of the React Native codebase.

## 2. Architecture & Tech Stack

We will use modern Android development standards (MAD) to ensure maintainability, performance, and scalability.

### Architecture Pattern
**MVVM (Model-View-ViewModel) + Clean Architecture**
-   **UI Layer (Presentation)**: Jetpack Compose for UI, ViewModels for state management.
-   **Domain Layer**: Use Cases (Interactives) encapsulating business logic (e.g., `SearchNotesUseCase`, `GenerateTextUseCase`).
-   **Data Layer**: Repositories implementing interfaces, handling data sources (Room DB, File System, AI Service).

### Technology Stack Mapping

| Component | Current (React Native) | New (Native Android) | Notes |
| :--- | :--- | :--- | :--- |
| **Language** | TypeScript | Kotlin | Use Coroutines & Flow for async |
| **UI Framework** | React Native / Expo | Jetpack Compose (Material3) | Single Activity architecture |
| **Navigation** | Expo Router | Jetpack Navigation Compose | Type-safe navigation |
| **Database** | expo-sqlite | Room Database (SQLite wrapper) | Type-safe DAOs, Flow observables |
| **State Mgmt** | React Hooks / Context | ViewModel + StateFlow | Lifecycle-aware state |
| **LLM Engine** | llama.rn | llama.cpp (JNI Bindings) | Need a robust service for inference |
| **Networking** | fetch / expo-file-system | OkHttp / Retrofit | Use WorkManager for large downloads |
| **Dependency Inj.**| Manual / Context | Hilt (Dagger) | Standard for modern Android |
| **Settings** | (Assumed) AsyncStorage | Jetpack DataStore | Preferences DataStore |

## 3. Module Structure

To keep the codebase clean, we will structure the package hierarchy by feature:

```text
com.example.llmnotes
├── core
│   ├── data        # Repositories, DataSources
│   ├── database    # Room DB, Entities, DAOs
│   ├── network     # Download logic
│   ├── ai          # LLM Inference Engine (JNI wrapper)
│   ├── di          # Hilt Modules
│   └── util        # Extensions, Helpers
├── domain
│   ├── model       # Pure Kotlin data classes (Note, Message)
│   └── usecase     # Business logic (e.g., VectorSearchUseCase)
├── feature
│   ├── notes       # NoteListScreen, NoteEditScreen, ViewModel
│   ├── chat        # ChatScreen, ChatViewModel
│   ├── settings    # ModelManagementScreen, SettingsViewModel
│   └── onboarding  # Welcome screens
└── ui              # Theme, Type, Color (Material3)
```

## 4. Key Implementation Details

### A. Database (Room)
The schema must match the existing one to support the data structure, though migration of actual user data isn't required (new app).
-   **Entity**: `NoteEntity`
    -   `id`: String (UUID)
    -   `title`: String
    -   `content`: String
    -   `createdAt`: Long
    -   `updatedAt`: Long
    -   `tags`: List<String> (TypeConverter)
    -   `embedding`: List<Float> (TypeConverter for Float Array)

### B. AI Engine & Background Service
The LLM inference is heavy.
-   **Engine**: Use `llama.cpp` android bindings.
    -   *Option A*: Compile `llama.cpp` manually with NDK.
    -   *Option B (Recommended)*: Use a pre-built library like `com.github.ggerganov:llama.cpp` if available via maven/jitpack, or similar wrappers like `java-llama.cpp`.
-   **Service**: Inference should run in a `ForegroundService` or a `ViewModel` scoped to the graph if we want to survive configuration changes. Given the memory usage, a singleton `LlmManager` injected via Hilt is crucial.
-   **Vector Search**:
    -   The current app performs a linear scan in JS.
    -   In Kotlin, we will fetch all notes with embeddings and perform cosine similarity calculation in a background thread (Dispatchers.Default).

### C. Model Management (Downloads)
-   Use `WorkManager` for downloading multi-gigabyte GGUF models. This ensures downloads continue even if the app is backgrounded.
-   Store models in `Context.filesDir` or App-specific external storage.

## 5. Risk Assessment
1.  **Memory Management**: Android kills background processes aggressively. The LLM might be 2GB+ in RAM. We must handle OOM (Out Of Memory) crashes gracefully.
2.  **JNI Complexity**: Integrating C++ code (`llama.cpp`) can be tricky with build systems (CMake).
3.  **UI Performance**: Rendering Markdown and code blocks in Jetpack Compose requires specific libraries (e.g., `compose-markdown`).

## 6. Resources
-   [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
-   [Room Database](https://developer.android.com/training/data-storage/room)
-   [Hilt Dependency Injection](https://developer.android.com/training/dependency-injection/hilt-android)
-   [llama.cpp Android Build Instructions](https://github.com/ggerganov/llama.cpp/blob/master/docs/android.md)
