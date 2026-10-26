---
name: test-every-function
description: Writes tests for every module, class and function created or changed in ClearRoom, in the same change, before the work counts as done. Covers which test type each layer gets (JVM unit, ViewModel with coroutines/Turbine, Compose UI and microphone tests on the S24 Ultra), the six case types to cover per function, naming, Arrange-Act-Assert layout, fakes over mocks, test-data builders, determinism rules, coverage targets with Kover, and a script that lists functions no test mentions. Use it every time Kotlin code is added or modified in this project, after finishing any milestone, when fixing a bug (failing test first), and whenever the user says "write tests", "test this", "unit test", "coverage", "is this tested" or "add test cases", even if they don't name this skill.
---

# Test every function

Rule: **a function, class or module isn't done until its tests exist and pass, in the same change.** For ClearRoom this is more than a habit. The app can't be checked against a reference meter (NFR-0), so the tests are the main evidence that its numbers are right.

This skill is the per-change workflow. For *what the right answers are* (golden STI values, the synthetic clap generator, NFR-3/NFR-4 tolerances, the field protocol), use the `clearroom-testing` skill. Don't invent expected numbers: compute them by hand or with `clearroom-testing/scripts/sti_reference.py`.

## Workflow for each change

1. **List what changed:** every new or modified public or internal function, class and module.
2. **Pick the test type** for each from the layer table below.
3. **Write the cases** from the six-question checklist. One test function checks one behaviour.
4. **Run them:** `gradlew.bat testDebugUnitTest` (JVM). Run `gradlew.bat connectedDebugAndroidTest` (on the S24 Ultra) only for device tests.
5. **Find gaps:** `python -I .claude/skills/test-every-function/scripts/find_untested.py --main ClearRoom/app/src/main --tests ClearRoom/app/src/test ClearRoom/app/src/androidTest`. It lists functions, classes and composables that no test mentions. Either test each one, or state why not (for example "trivial getter, covered through `ClarityEngine` tests").
6. **Report** with the template at the end. If the project doesn't build yet, write the tests anyway and say clearly that they haven't been run.

For a **bug fix**, write the failing test first, watch it fail, then fix the code. The test name describes the bug's scenario, so it never comes back.

## Which test type per layer

| Layer / files | Test type | Source set | Tools |
| --- | --- | --- | --- |
| `dsp/`, `acoustics/`, `viz/math3d/`, `viz/Axis.kt`, chart model mappers | Pure JVM unit tests | `app/src/test` | JUnit 4 (template default), `kotlin.test` asserts |
| `data/Models.kt`, JSON encode/decode | JVM unit tests | `app/src/test` | plus `testImplementation("org.json:json")`, because android.jar's org.json is a stub |
| `AppViewModel` | JVM with fakes | `app/src/test` | `kotlinx-coroutines-test` (`runTest`, `MainDispatcherRule`), Turbine for `StateFlow` |
| `audio/Recorder.kt` (real `AudioRecord`) | Instrumented, on the phone | `app/src/androidTest` | `GrantPermissionRule` for RECORD_AUDIO |
| `data/Store.kt` file I/O (`AtomicFile`), `report/PdfReport.kt` | Instrumented | `app/src/androidTest` | Real `filesDir`/`cacheDir`; clean up in `@After` |
| YAMNet labeler | Instrumented | `app/src/androidTest` | Model loads; a synthetic tone or silence gives a label or null, not a crash |
| Composable screens and charts | Mapper logic in JVM tests; a few UI smoke tests on the phone | `app/src/test` + `app/src/androidTest` | `createComposeRule()`, `onNodeWithText`, `performClick` |

Keep the JVM suite fast (under about 30 s on this laptop) so it runs after every change. Device tests are slower, so run them at the end of each milestone.

Test dependencies to add at milestone M1, through the version catalog with the latest stable versions: `kotlin("test")` (for `kotlin.test` asserts with tolerances), `kotlinx-coroutines-test`, `app.cash.turbine:turbine`, `org.json:json` (test only), and `androidx.test:rules` (for `GrantPermissionRule`). Also the Kover Gradle plugin for coverage. The template already brings JUnit 4, `androidx.test.ext:junit`, Espresso and `ui-test-junit4`. Don't add MockK unless a fake is truly impractical.

## The six questions: cases to write for every function

1. **Known answer:** a normal input with an expected value worked out independently (by hand, from the doc, or with the reference script). Example: `transmissionIndex(0.5) == 0.5`.
2. **Boundaries:** exactly at, just below and just above each threshold the function cares about. Examples: STI band edges 0.45 / 0.60 / 0.75; T60 limits 0.1 s and 6 s; dynamic range at 30 dB; distance at the critical distance.
3. **Degenerate input:** empty arrays, one seat, one accepted take, all takes rejected, an all-zero signal, a room with one row.
4. **Invalid input:** negative or zero dimensions, NaN, Infinity, the wrong array length. The function must either throw a `require` error with a clear message or return the sealed `Rejected`/`Unreachable` value. Assert which one.
5. **Invariants:** properties that must hold across a seeded grid of inputs. Examples: STI stays in [0, 1]; STI never rises with T60; Sabine round trip; JSON round trip; adding absorption never raises T60.
6. **Regressions:** one test per bug ever fixed, named after the scenario.

Extra cases by kind:

- **Suspend / Flow:** the result arrives; cancellation stops the work and releases resources; errors become state, not crashes; the order of emitted states is right (for example Countdown → Recording → Analysing → Done).
- **Stateful classes** (`Biquad`, the streaming filter bank): a fresh instance gives the same output as after `reset()`. For the streaming bank, two halves fed one after the other give the same result as the whole signal.
- **Composables:** renders with the sample room; renders its empty state; one key interaction works (a tab switches, a seat tap opens its sheet).

You don't need all six for a trivial function, but do consider each one. Skip a question only when it truly doesn't apply.

## How tests look

```kotlin
class StiTest {

    @Test
    fun `noise only - STI equals (SNR + 15) over 30`() {
        // Arrange
        val t60 = DoubleArray(7) { 0.0 }
        val snr = DoubleArray(7) { 6.0 }
        // Act
        val result = sti(t60PerBand = t60, snrPerBand = snr)
        // Assert
        assertEquals(0.70, result, absoluteTolerance = 1e-9)
    }

    @Test
    fun `STI never rises as reverberation grows`() {
        val snr = DoubleArray(7) { 10.0 }
        val scores = listOf(0.0, 0.3, 0.6, 1.0, 2.0, 4.0).map { t -> sti(DoubleArray(7) { t }, snr) }
        assertEquals(scores.sortedDescending(), scores, "STI by rising T60: $scores")
    }

    @Test
    fun `NaN reverberation is rejected, not scored`() {
        assertFailsWith<IllegalArgumentException> { sti(DoubleArray(7) { Double.NaN }, DoubleArray(7) { 10.0 }) }
    }
}
```

- **Names:** in `app/src/test`, write backtick sentences describing the behaviour. In `app/src/androidTest`, use `camelCase_withUnderscores`. Backtick names with spaces fail to compile into Android test APKs when `minSdk` is below 30, and ClearRoom's is 29.
- **Layout:** Arrange / Act / Assert, with blank lines or comments between. One reason to fail per test.
- **Tolerances:** use `1e-9` for exact maths; the doc's tolerances for physics (±0.02 STI, ±10 % or ±0.1 s RT60); dB comparisons usually ±0.1 dB. Always pass the tolerance explicitly, never compare doubles with `==`.
- **Failure messages:** put the input in the message (`"T60=$t60 gave $result"`), so a failure on the self-test screen or in CI explains itself.
- **One test file per production file:** `Sti.kt` → `StiTest.kt`, in the same package.

## Test data: builders and fakes

- **Builders** with sensible defaults, so each test states only what matters:
  `fun aRoom(lengthM: Double = 9.0, widthM: Double = 7.0, heightM: Double = 3.0, rows: Int = 5, cols: Int = 6) = Room(...)`
  Keep them in `app/src/test/.../testdata/Builders.kt`. Reuse `SampleData` for "a full realistic session".
- **Fakes over mocks:** implement the real interface with simple behaviour (`FakeAudioInput` returning synthetic clips, `InMemoryRoomRepository`, `FakeSoundLabeler`). A fake still works after refactoring, while a mock checks exact calls and breaks when you rename one. Fakes go in `app/src/test/.../fakes/`.
- **Contract tests:** when there's a fake and a real implementation of the same interface (`AudioInput`), run the same behaviour checks against both. That's how the Liskov rule from `kotlin-solid-clean-code` is actually checked.

## Determinism

Tests that fail sometimes get ignored, and then a real failure gets ignored too.

- Random numbers: always seeded (`java.util.Random(42)`).
- Time: inject a clock or time source into anything that timestamps (`NoiseLog`). Never `Thread.sleep`; use `runTest`'s virtual time (`advanceTimeBy`).
- Dispatchers: inject them; `MainDispatcherRule` for `viewModelScope`:

```kotlin
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

- Files: JUnit's `TemporaryFolder` rule on the JVM, and app cache directories cleaned in `@After` on the device. Never the real rooms folder.
- No network and no real microphone in JVM tests. Microphone tests run on the phone only, in a quiet room, and assert structure (length, sample rate, finite values, the microphone released), not exact levels.

## Coverage targets (Kover)

| Package | Line coverage target |
| --- | --- |
| `dsp`, `acoustics`, `viz.math3d` | ≥ 90 % |
| `AppViewModel`, `data` | ≥ 80 % |
| UI (`ui`, `viz` composables) | Smoke tests; no number |

Coverage is a gap-finder, not the goal: a test that runs code without asserting anything raises coverage and proves nothing. Configure the thresholds in Kover's `reports { verify { rule { minBound(...) } } }` block. Kover's DSL has changed between versions, so check the current syntax (the Context7 MCP can fetch it). Run it with `gradlew.bat koverHtmlReportDebug`.

## Report format

After writing tests, finish with:

```markdown
### Tests added
| Production code | Test file | Cases |
| --- | --- | --- |
| dsp/Sti.kt · sti() | StiTest.kt | known answers (5 golden), band edges, NaN rejected, monotonic in T60 and SNR |

**Run:** 42 passed, 0 failed (testDebugUnitTest, 11 s) · device tests: not run (no phone connected)
**Untested by design:** Theme.kt (colours only)
**find_untested.py:** 0 functions, 0 classes, 1 composable (AppScaffold: covered by the tab-switch UI test once on device)
```

Report failures honestly, with the assertion message. Never weaken an assertion or widen a tolerance to make a test pass. If the doc's tolerance can't be met, report the measured numbers to the user.
