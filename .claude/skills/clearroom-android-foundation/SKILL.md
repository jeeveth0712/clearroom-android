---
name: clearroom-android-foundation
description: Project skeleton and architecture rules for the ClearRoom Android app (Kotlin, Jetpack Compose, Material 3, single Activity, 5-tab bottom navigation, one AppViewModel with StateFlow, coroutines, RECORD_AUDIO permission). Use this whenever creating or changing Gradle files, the manifest, MainActivity, navigation, theme, AppViewModel, UI state, the sample room, or when deciding which package/file new ClearRoom code belongs in, even if the user only says "add a screen", "wire this up", "set up the project" or "where should this go".
---

# ClearRoom Android foundation

The requirements doc (`ClearRoom Speech Clarity Checker — Requirements.md` in the project root) is the source of truth. This skill turns its "Android app design" section into working rules. When the doc and this skill disagree, follow the doc and point out the difference to the user.

## Starting the project

1. Create it from Android Studio's **Empty Activity (Compose)** template. Keep the template's AGP, Kotlin, Compose BOM and version catalog (`gradle/libs.versions.toml`). Don't pick versions by hand, because the template's set is already known to work together.
2. Set `minSdk = 29` (Android 10, NFR-7). Leave target and compile SDK at the template defaults.
3. Run `git init` and commit the empty template. The build plan asks for one commit per task so you can roll back.
4. JDK: AGP 8+ needs JDK 17 or newer. On this machine the `java` on PATH is 1.8, so command-line builds fail unless `JAVA_HOME` points at Android Studio's bundled JBR (usually `C:\Program Files\Android\Android Studio\jbr`). Builds from inside Android Studio already use the JBR.
5. Add new libraries through the version catalog only, and only when needed. The doc's stack needs almost nothing extra: `lifecycle-runtime-compose` (for `collectAsStateWithLifecycle`), `lifecycle-viewmodel-compose`, and later `com.google.mediapipe:tasks-audio` for FR-9. Charts, 3D, JSON and PDF all use built-in APIs on purpose (no library version risk).

## Package layout and the dependency rule

```
app/src/main/java/<package>/
  MainActivity.kt          permission flow, Scaffold, bottom navigation
  AppViewModel.kt          the one ViewModel; StateFlow<UiState>; delegates all work
  AppContainer.kt          composition root: builds Recorder, Store, engines
  data/Models.kt           Room, Seat, SeatMeasure, Session, LogSecond (pure Kotlin)
  data/Store.kt            JSON save/load (org.json, Android side)
  data/SampleData.kt       built-in sample room with full measurements
  audio/Recorder.kt        AudioRecord wrapper  (see android-audio-capture skill)
  audio/NoiseLog.kt        day log              (see noise-day-log skill)
  dsp/Biquad.kt, dsp/OctaveBank.kt, dsp/Reverb.kt, dsp/Sti.kt   (pure Kotlin)
  acoustics/RoomModel.kt   Sabine fit, distance model, what-if (pure Kotlin)
  viz/Scene3D.kt, viz/*.kt one composable per view V1-V15
  report/PdfReport.kt
  ui/theme/                Material 3 theme + StiColors (fixed band colors)
  ui/screens/              RoomScreen, MeasureScreen, ResultsScreen, FixesScreen, LogScreen
```

Dependencies point one way: `ui` and `viz` → `AppViewModel` → `audio`, `data`, `acoustics` → `dsp`. Nothing in `dsp/`, `acoustics/` or `data/Models.kt` may import `android.*`, `androidx.*` or `org.json.*`. The doc requires this so the engine can be unit-tested on a plain JVM "without changing a line". Local unit tests in `app/src/test` run on the JVM against a stub `android.jar`, so an Android import in the engine turns into "Method ... not mocked" failures. Check the rule with:

```bash
grep -rnE "^import (android|androidx|org\.json)\." app/src/main/java/*/*/dsp app/src/main/java/*/*/acoustics app/src/main/java/*/*/data/Models.kt
```

The command should print nothing.

## AppViewModel: one ViewModel, kept thin

The doc chooses one `AppViewModel`. Keep that decision, but don't let the ViewModel do the work: it holds state and calls small engine classes, and it contains no math, no `AudioRecord` and no JSON.

```kotlin
class AppViewModel(
    private val recorder: AudioInput,
    private val store: RoomRepository,
    private val clarity: ClarityEngine,
    private val dsp: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState.initial())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onClapTestRequested(seat: SeatId) = viewModelScope.launch {
        _state.update { it.copy(measure = it.measure.copy(phase = MeasurePhase.Countdown(3))) }
        val clip = recorder.record(seconds = 5.0)
        val take = withContext(dsp) { clarity.analyseClap(clip) }
        _state.update { it.withTake(seat, take) }
    }
}
```

- Keep `MutableStateFlow` private, expose `StateFlow`, and change state only with `update { }`. `update` is atomic, so two coroutines finishing together don't overwrite each other.
- Run DSP on `Dispatchers.Default` and file I/O on `Dispatchers.IO`. Inject the dispatcher so tests can pass a test dispatcher.
- Build the ViewModel through `viewModelFactory { initializer { AppViewModel(...) } }` using objects from `AppContainer`. A DI framework isn't worth the setup for a 2-day sprint. Constructor injection keeps the same testability.
- Model `UiState` as one immutable data class with one sub-state per tab (`room`, `measure`, `results`, `fixes`, `log`). Derived results (the STI grid, causes, fix ranking) are recomputed from inputs and never edited by hand.
- Model one-off events (a snackbar saying "Clap was clipped, try again") as state the screen clears after showing it, or as a `Channel` read with `receiveAsFlow()`. Don't use a `SharedFlow` without replay, because events are lost while the screen is stopped.

## Compose UI rules

- Collect state with `val state by vm.state.collectAsStateWithLifecycle()` at the screen level only. Pass plain values and lambdas down (`RoomScreen(state.room, onEvent = vm::onRoomEvent)`) so each screen composable is stateless and gets a `@Preview` that uses `SampleData`.
- Bottom navigation: 5 flat tabs don't need the navigation library. Use `enum class Tab { Room, Measure, Results, Fixes, Log }`, `var tab by rememberSaveable { mutableStateOf(Tab.Room) }`, `NavigationBar` + `when (tab)`. `rememberSaveable` keeps the tab across rotation.
- Theme: Material 3 with light and dark schemes. Dynamic color is fine for app chrome, but the STI band colors (poor/fair/good/excellent) are fixed semantic colors defined once in `ui/theme/StiColors.kt`. The doc requires "a red seat is red everywhere": in 2D, 3D and the PDF.
- Plain language (NFR-2): the main screens show verdicts like "Back rows: hard to follow. Main cause: echo." Acoustics terms (RT60, STI, MTF) appear only on the engineering cards, each with a one-line explanation. Keep strings in `strings.xml`.
- Every verdict shows its uncertainty band (NFR-10). Never show a bare 2-decimal STI on the main screen.

## Permissions and manifest

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-feature android:name="android.hardware.microphone" android:required="true" />
```

- Ask for RECORD_AUDIO with `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` when the user first starts a measurement, not at app launch. Explain why first ("ClearRoom measures sound levels; it never records or stores speech").
- Check the permission again right before every recording, because the user can revoke it from Settings while the app is running. If it's permanently denied, show a button that opens the app's settings page.
- Don't add the `INTERNET` permission. NFR-8 says all analysis is offline, and report sharing uses an intent, which needs no network permission. Leaving INTERNET out is also the strongest privacy guarantee you can show a school.

## Sample room first

`SampleData.kt` provides a complete `Session`: room dimensions, a 5 × 6 seat grid, three measured seats with RT60 takes, noise band levels and a voice reference, plus a short day log. The build plan's first rule is that every chart works with sample data before you touch the microphone again. Each screen should render the sample in a `@Preview` and from the Room tab's "Load sample room" action.

## Task done-criteria (from the build plan)

Treat these as acceptance checks for each task:

| Task | Done when |
| --- | --- |
| Skeleton | App installs; the 5 tabs switch; light and dark both work |
| Recorder | Measure tab records 5 s with a moving level bar |
| Data + Room tab | A room survives an app restart |
| Filters and levels | Noise and voice show 7 per-band numbers |
| Echo analysis | A real clap gives RT60 between 0.3 and 2 s in a normal room |
| Clarity engine | Sample room back-row STI is below front-row STI |
| 3D core | Sample room bars rotate smoothly |

## Related skills

`android-audio-capture`, `acoustics-dsp`, `sti-room-model`, `compose-charts-2d`, `compose-3d-scene`, `noise-day-log`, `storage-and-pdf-report`, `clearroom-testing`, `kotlin-solid-clean-code` (design rules for all code) and `bug-vulnerability-audit` (before each commit or release).
