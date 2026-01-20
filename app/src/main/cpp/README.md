# Native Build Setup

To enable the LLM engine, you must clone the `llama.cpp` repository into this directory.

1. Open a terminal in this directory (`app/src/main/cpp`).
2. Run: `git clone https://github.com/ggerganov/llama.cpp`
3. Open `CMakeLists.txt` and uncomment the lines related to `llama.cpp`.
4. Implement the TODOs in `native-lib.cpp` calling the actual llama functions.
