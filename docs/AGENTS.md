# DOCS KNOWLEDGE BASE

**Context:** Documentation & Task Tracking

## OVERVIEW
Central repository for technical specifications, environment setup, and development task tracking.

## STRUCTURE
- **wsl/**: Infrastructure scripts and setup guides for the Linux-side build host.
  - `scripts/`: Local copies of build tools for reference.
  - `guides/`: Step-by-step environment configuration and Vulkan setup.
- **android_tasks/**: Incremental feature roadmap and historical implementation logs.
  - Sequentially numbered files (e.g., `01-initialize-android-project.md`) tracking progress.
- **release/**: Compliance checklists, store assets, and readiness reports for production.
- **Root Assets**: Project-level plans like `ANDROID_PORT_PLAN.md` and `DESIGNER_BRIEF.md`.

## CONVENTIONS
- **Markdown Style**: Follow GitHub Flavored Markdown (GFM). Use headers for scanability.
- **Mermaid Diagrams**: Prefer Mermaid for architectural and data flow visualizations.
- **Task Management**: Update `[ ]` to `[x]` in task files immediately upon feature merge.
- **Relative Paths**: Use absolute paths for Windows host or project-root-relative paths.
- **Front Matter**: Include `Generated` date and `Context` at the top of major technical guides.

## ANTI-PATTERNS
- **Stale Tasks**: Leaving `android_tasks` in an inconsistent state relative to the codebase.
- **Root Redundancy**: Repeating build instructions or JNI safety rules already defined in the root `AGENTS.md`.
- **Secret Exposure**: Committing PII, API keys, or private signing configurations to any markdown file.
- **Broken Links**: Moving files without updating internal documentation references.
- **Unformatted Code**: Including code snippets without appropriate language tags for syntax highlighting.
