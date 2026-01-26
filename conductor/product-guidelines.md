# Product Guidelines - Synapse Notes AI

## Tone and Voice
- **Professional & Concise**: AI responses and system messages should be direct, efficient, and objective. Avoid unnecessary pleasantries or conversational filler.
- **Authoritative yet Helpful**: Position the AI as a reliable research assistant that prioritizes accuracy and speed.

## Visual Identity & UX
- **Minimalist & High-Density UI**: Maximize vertical and horizontal space for note content. Use compact components and reduced padding to allow power users to see more information at once.
- **Material 3 Foundation**: Leverage Material 3 components while maintaining the high-density philosophy. Support Dynamic Color (Material You) where it doesn't compromise density.
- **Dark Mode Optimization**: Ensure a premium dark mode experience as the primary interface for many users, focusing on high contrast and reduced eye strain.

## Performance Transparency
- **Hardware Dashboard**: Provide a clear, easily accessible view of real-time performance metrics, including:
    - Current Backend (Vulkan, OpenCL, or CPU).
    - Tokens per second (T/s) for generation.
    - VRAM and System RAM utilization.
    - Model name and quantization level.
- **Feedback Loops**: Use distinct, low-latency visual cues for AI "thinking" states without interrupting the user's flow.

## Editing Experience
- **WYSIWYG Rich Text**: Deliver a visual-first editor that allows for rich formatting (bold, lists, headers) without requiring Markdown knowledge.
- **Contextual AI Assistance**: Integrate AI directly into the editing flow:
    - **Summarization**: One-tap summary of long notes.
    - **Inline Refinement**: Tools to rewrite, expand, or simplify selected text.
    - **Sidecar Chat**: Persistent access to the LLM to query the current note or the entire knowledge base (RAG).
