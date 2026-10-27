---
name: clearroom-android-foundation
description: Maintain ClearRoom's Android architecture, Compose navigation, theme, ViewModel state, dependency setup, or microphone permission flow. Use for app shell, Gradle, manifest, UI state, and package-boundary changes.
---

# ClearRoom Android foundation

Read the current `AGENTS.md` and the requirements document's Implementation notes before changing architecture. Earlier setup examples in `.claude/skills/` describe a build that has since shipped; the current source and `AGENTS.md` take precedence.

- The Android project is `ClearRoom/`; package `com.clearroom.app`, minimum SDK 29, target SDK 37. Versions live in `gradle/libs.versions.toml`. Do not add dependencies for views the existing Compose Canvas code already draws.
- `MainActivity` creates the single `AppViewModel` through `AppContainer`. UI state is an immutable `StateFlow<UiState>`; screen composables receive values and dispatch `UiEvent`s. Keep derived clarity, fixes, and log results tied to the session that produced them.
- Pure Kotlin engine packages are `dsp/`, `acoustics/`, `measure/`, `daylog/`, `data/Models.kt`, `report/Report.kt`, and `viz/math3d/`. Do not import `android.*`, `androidx.*`, or `org.json` into them. Android wrappers, persistence, and Compose code stay outside the engine.
- The five tabs are Room, Measure, Results, Fixes, and Log. Keep selected tab saveable; the day log and measurements share one microphone. Opening another room stops the log.
- The UI follows the phone's light/dark mode with fixed lime/ink chrome and fixed clarity-band colours. Text comes from `strings.xml`; counts use plurals. Explain acoustic terms, show an STI range with every verdict, and retain accessible spoken alternatives for visualizations.
- Ask for microphone permission only when recording is requested. The app stores levels and labels, never PCM audio, and has no INTERNET permission.
- Keep `testBuildType = "uiTest"` and its separate application ID so device tests cannot erase the owner's saved rooms. Respect the 95 MB APK cap and the existing ARM ABI filters.

For a relevant change, run the checks required by `AGENTS.md` and inspect the result on a phone when available. Never measure into the owner's saved room from scripts.
