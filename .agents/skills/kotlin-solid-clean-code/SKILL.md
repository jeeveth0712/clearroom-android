---
name: kotlin-solid-clean-code
description: Review or refactor ClearRoom Kotlin and Compose structure for readable ownership, immutable state, coroutine safety, and justified design patterns. Use when code boundaries or complexity are the task.
---

# Kotlin design in ClearRoom

The project has one `AppViewModel` as the coordinator, pure domain models/engines, Android adapters, and stateless Compose screens. Preserve those boundaries when refactoring. Current `AGENTS.md` and source code are authoritative over older hypothetical class examples.

- Put arithmetic and decisions in pure Kotlin, microphone and file operations in Android adapters, and formatting or interaction in UI. Keep `UiEvent` handlers thin enough to test with fakes.
- Prefer immutable data and `StateFlow.update`. Avoid mutable arrays in equality-sensitive state, hidden global singletons, and new frameworks for a one-use abstraction.
- Treat cancellation as control flow: rethrow `CancellationException`, release resources in `finally`, and never run blocking audio or file I/O on the main thread.
- Hoist Compose state that matters across screens, key remembered derived values to their inputs, and keep screen components accessible with resource strings.
- Use a named pattern only when it clarifies an existing variation or lifecycle need. The [design-patterns reference](references/design-patterns.md) has Kotlin examples; read only the relevant section before introducing one.
- Keep units in names (`lengthM`, `timeS`, `levelDb`) and add tests for observable behavior when restructuring nontrivial logic.
