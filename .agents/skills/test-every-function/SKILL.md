---
name: test-every-function
description: Add and verify proportionate JVM or on-phone tests for new or changed ClearRoom Kotlin functions and behaviours. Use for implementation, bug fixes, and test-coverage checks in this repository.
---

# Test changed ClearRoom behaviour

The owner requires meaningful tests for new or changed functions in the same milestone. Choose the layer that can observe the behavior; avoid tests that repeat implementation details without checking an outcome.

- Pure `dsp/`, `acoustics/`, `measure/`, `daylog/`, `report/Report.kt`, and `viz/math3d/` logic gets JVM tests with deterministic inputs and assertions on numerical results, boundary cases, and failure handling.
- ViewModel tests use the repository's fakes and coroutine test scheduler. Stop an endless fake day-log flow before `runTest` ends. Test that derived outputs belong to the current session after room switches.
- Android storage, PDF, microphone, permissions, Compose navigation, and screen behavior get on-phone tests where JVM stubs cannot prove the result. Device tests run in the separate `uiTest` app; scroll to off-screen elements before asserting.
- Name JVM tests as readable backtick sentences with `kotlin.test`, and device tests as camelCase JUnit4 methods. Use `assertTrue` rather than Kotlin's `assert`.
- Run relevant focused tests first, then `test lintDebug connectedAndroidTest` for a completed milestone when the phone is available. Confirm lint ends with “No issues found.” Report failures or unavailable device checks explicitly.

The heuristic gap finder is [scripts/find_untested.py](scripts/find_untested.py). From the repository root: `python -I .agents/skills/test-every-function/scripts/find_untested.py --main ClearRoom/app/src/main/java --tests ClearRoom/app/src/test/java ClearRoom/app/src/androidTest/java`. Review its output manually: name matches are not proof of good tests, and private composables may be exercised through screen tests.
