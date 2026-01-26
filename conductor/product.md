# Initial Concept
A high-performance Android note-taking application featuring on-device Large Language Model (LLM) integration via `llama.cpp`, supporting both Vulkan and OpenCL GPU acceleration for privacy-first, offline-capable AI assistance.

# Product Definition - Synapse Notes AI

## Target Audience
- **Privacy-conscious individuals** who require their personal data and AI interactions to remain strictly on-device.
- **Power users and researchers** who need AI-assisted note-taking with local knowledge retrieval (RAG) capabilities.
- **Developers and tech enthusiasts** interested in the cutting edge of high-performance Android AI implementations and hardware acceleration.

## Core Goals
- **Privacy & Security**: Provide a completely offline-capable AI experience where no data leaves the device.
- **High Performance**: Demonstrate and deliver high-speed LLM inference by leveraging Vulkan and OpenCL GPU backends.
- **Intelligent Retrieval**: Empower users with local Retrieval-Augmented Generation (RAG) to query their personal knowledge base.

## Key Features
- **Multi-Backend Acceleration**: High-performance AI chat utilizing Vulkan and OpenCL to ensure maximum compatibility and speed across diverse Android hardware.
- **RAG Pipeline**: Chat with your notes using local vector search, allowing for context-aware AI responses based on personal content.
- **Automated Model Management**: Curated GGUF model selection combined with intelligent, automatic backend selection (Vulkan vs OpenCL vs CPU) based on device hardware capabilities.
- **Cloud Sync**: Secure synchronization with Google Drive for backup and cross-device availability without compromising on-device inference privacy.
- **Material 3 Design**: A modern, fluid user interface supporting seamless light/dark mode transitions.
