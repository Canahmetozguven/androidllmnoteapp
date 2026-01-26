# CORE LAYER KNOWLEDGE BASE

## OVERVIEW
Infrastructure layer managing the local Room database, data repositories, and the native AI inference engine.

## STRUCTURE
```
core/
├── ai/          # Llama.cpp Kotlin wrapper (LlmEngine, LlmContext)
├── data/        # Repository implementations (Chat, Note, Google Drive)
├── database/    # Room DB: Entities (NoteEntity, ChatMessageEntity) & DAOs
├── di/          # Hilt modules (DatabaseModule, AiModule, RepositoryModule)
├── network/     # Background tasks (WorkManager for cloud sync)
└── preferences/ # SharedPreferences/DataStore for app state
```

## WHERE TO LOOK
| Component | Primary File | Role |
|-----------|--------------|------|
| **AI Engine** | `ai/LlmEngine.kt` | Manages model lifecycle and inference requests |
| **Native Bridge** | `ai/LlmContext.kt` | Direct JNI interface for `llama.cpp` |
| **Database** | `database/AppDatabase.kt` | Central Room DB definition & migrations |
| **DI Root** | `di/DatabaseModule.kt` | Hilt provides for Room and DAOs |

## CONVENTIONS
- **Hilt**: All core components must be injected. Use `@Singleton` for `LlmEngine` and `AppDatabase`.
- **Room**: 
    - Use `suspend` for one-shot operations (insert, update, delete).
    - Use `Flow<T>` for observable data (lists, single items).
    - Ensure `fallbackToDestructiveMigration()` is used during rapid development.
- **Coroutines**: 
    - **AI**: All `LlmEngine` calls MUST use `Dispatchers.IO` (handled internally via `withContext`).
    - **Database**: Repository operations must switch to `Dispatchers.IO` before DAO calls.
- **Result Pattern**: Repositories should return `Result<T>` to handle errors gracefully.

## TESTING STRATEGY
- **Repositories**: Test `Repository` implementations using JUnit 5. Mock `Dao` and `LlmEngine`.
- **Database**: Test `Dao`s in `androidTest` using `Room.inMemoryDatabaseBuilder`.
- **AI Engine**: Test `LlmEngine` logic using JUnit 5 + Mockito. Mock `LlmContext` (do NOT load native lib).

## ANTI-PATTERNS (STRICT)
- **NO Main Thread Access**: Room and LlmEngine will throw exceptions or freeze UI if accessed on Main.
- **NO Manual Instantiation**: Never use `LlmEngine()` or `AppDatabase()` constructors directly; use Hilt.
- **NO State in Repositories**: Repositories should be stateless; use `LlmEngine` for AI state or DB for persistence.
- **NO Business Logic**: Core is for data/infra only. Keep validation and logic in `domain` or `feature`.
