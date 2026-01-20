# Android Port Task List

This document breaks down the porting process into actionable tasks.

## Status Legend
- [ ] Pending
- [x] Completed
- [-] Skipped/Not Applicable

---

## Phase 1: Project Setup & Foundation

### 1.1 Environment & Build
- [ ] **Initialize Android Project**
    - Create new Project in Android Studio (Empty Activity).
    - Min SDK: 26 (Android 8.0), Target SDK: 34.
    - Language: Kotlin.
    - Build System: Gradle (Kotlin DSL).
- [ ] **Configure Dependencies (libs.versions.toml)**
    - [ ] Jetpack Compose (BOM).
    - [ ] Navigation Compose.
    - [ ] Room (Runtime, Kapt/KSP, KTX).
    - [ ] Hilt (Android, Compiler).
    - [ ] Coroutines (Core, Android).
    - [ ] Lifecycle (ViewModel, Runtime).
    - [ ] OkHttp & Retrofit (for downloading).
    - [ ] Markdown renderer for Compose.
- [ ] **Setup Architecture Structure**
    - Create package structure: `core`, `domain`, `feature`, `ui`.
    - Setup Hilt `@HiltAndroidApp` class.

### 1.2 Data Layer (Room)
- [ ] **Define Entities**
    - Create `NoteEntity` (matches `NoteStorage.ts`).
    - Create `TypeConverters` for `List<String>` (tags) and `List<Float>` (embedding).
- [ ] **Create DAO**
    - `NoteDao`: `getAll()`, `getById()`, `insert()`, `delete()`, `update()`.
- [ ] **Database Setup**
    - Configure `AppDatabase` abstract class.
    - Create Hilt module `DatabaseModule` to provide DB and DAO instances.

---

## Phase 2: Core Domain Logic

### 2.1 Note Management
- [ ] **Note Repository**
    - Define `NoteRepository` interface.
    - Implement `NoteRepositoryImpl` using `NoteDao`.
- [ ] **Use Cases**
    - `GetNotesUseCase` (returns Flow list).
    - `SaveNoteUseCase`.
    - `DeleteNoteUseCase`.

### 2.2 LLM Integration Infrastructure (Critical)
- [ ] **JNI / Native Setup**
    - [ ] Download/Clone `llama.cpp` source.
    - [ ] Configure `CMakeLists.txt` to build `llama` as a shared library (`.so`).
    - [ ] Create Kotlin JNI wrapper class (`LlamaContext`).
- [ ] **Model Manager**
    - Create `ModelManager` class to check for existing `.gguf` files.
    - Implement `ModelRepository` to track downloaded models (using DataStore or simple File check).
- [ ] **Inference Service**
    - Create `LlmEngine` class (Singleton).
    - Implement methods: `loadModel(path)`, `completion(prompt)`, `embed(text)`.
    - *Note*: Ensure this runs on a background thread (`Dispatchers.IO`).

### 2.3 Vector Search Logic
- [ ] **Vector Math Utilities**
    - Implement `cosineSimilarity(float[], float[])` in pure Kotlin.
- [ ] **Search Use Case**
    - Create `VectorSearchUseCase`.
    - Logic:
        1. Fetch all notes with embeddings from Repo.
        2. Embed query using `LlmEngine.embed(query)`.
        3. Iterate and calculate similarity.
        4. Sort and return top K results.

---

## Phase 3: Features & UI Implementation

### 3.1 Design System
- [ ] **Theme Setup**
    - Define Colors (Light/Dark) in `ui/theme/Color.kt`.
    - Define Typography in `ui/theme/Type.kt`.
    - Create generic Components: `AppTopBar`, `AppButton`, `LoadingIndicator`.

### 3.2 Feature: Onboarding & Settings
- [ ] **Onboarding Screen**
    - Explain "Offline AI" concept.
    - Request permissions (if needed, though mostly file access).
- [ ] **Settings / Model Manager Screen**
    - List available GGUF models (hardcoded URL list similar to RN config).
    - Implement Download Action (trigger `WorkManager`).
    - Progress Bar for downloads.
    - "Load Model" button to initialize the engine.

### 3.3 Feature: Notes
- [ ] **Note List Screen**
    - RecyclerView equivalent (LazyColumn).
    - Search Bar (text search + vector search toggle).
    - FAB to create new note.
- [ ] **Note Detail/Edit Screen**
    - TextField for Title.
    - TextField for Content.
    - "Generate/Complete" button (triggers AI completion on current text).

### 3.4 Feature: Chat (RAG)
- [ ] **Chat UI**
    - Message List (User vs AI bubbles).
    - Input field.
- [ ] **RAG Implementation**
    - When user sends message:
        1. `VectorSearchUseCase.execute(query)`.
        2. Construct prompt with Context (retrieved notes).
        3. Stream response from `LlmEngine` to UI.

---

## Phase 4: Polish & Delivery

### 4.1 Testing
- [ ] **Unit Tests**
    - Test `cosineSimilarity` math.
    - Test `NoteRepository` logic.
- [ ] **Instrumentation Tests**
    - Test Room DB migrations/operations.

### 4.2 Performance
- [ ] Profile memory usage when LLM is loaded.
- [ ] Optimize `VectorSearch` (ensure it doesn't block UI).

### 4.3 Release
- [ ] Configure `build.gradle` signing configs.
- [ ] Create App Icon (mipmap).
- [ ] Generate Signed APK/AAB.
