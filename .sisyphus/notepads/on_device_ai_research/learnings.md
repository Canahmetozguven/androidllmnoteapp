
## LFM2-1.2B-RAG Integration
- **Model Source**: Downloaded `LFM2-1.2B-RAG-Q4_K_M.gguf` from HuggingFace (LiquidAI/LFM2-1.2B-RAG-GGUF).
- **Asset Management**: 
  - Model file placed in `app/src/main/assets/`.
  - `ModelManager` updated to auto-copy `.gguf` files from assets to `filesDir/models` on initialization.
- **Configuration**:
  - `SettingsViewModel.kt` updated to include LFM2 in `AVAILABLE_MODELS` list.
  - Set as the first item in the list.
