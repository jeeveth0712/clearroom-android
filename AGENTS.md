# ClearRoom — project context for any coding agent

Read this first. It is the complete handover: what the project is, how the owner wants the work
done, where everything is, what has been built, what is decided, what is still open, and the traps
already hit. It is kept in the repository so it works from any account or tool.

- **Last updated:** 2026-10-27, after milestone **M11** (commit `8c50b31`).
- **Next step:** milestone **M12 — finish and audit** (section 9).
- **Repository:** https://github.com/jeeveth0712/clearroom-android (branch `main`).
- **Owner:** the student whose assignment this is (GitHub `jeeveth0712`). They write short messages ("m9", "ok next", "please start") and expect work to continue in the agreed order.

---

## 1. What the project is

ClearRoom is a native Android app (Kotlin, Jetpack Compose) that measures how clearly speech can be
heard at each seat of a classroom using only the phone's microphone, explains why (echo, noise or
distance), and predicts what cheap fixes would do.

It is the owner's answer to **"Assignment 2: Build a useful sensor app on your mobile phone"**,
graded on: (1) the app works, (2) it measures something useful with phone sensors, (3) the
visualizations make the measurements easy to understand.

Deliverables: the app (APK + source, this repo), a video of **at most 1 minute** showing it on the
phone, and the requirements document, which is **submitted too**.

The source of truth for features is `ClearRoom Speech Clarity Checker — Requirements.md` (FR-1…FR-13,
NFR-0…NFR-10, views V1…V15). The owner chose to build the **full** document: all 13 milestones and
all 15 views, in the original order.

## 2. Working agreements (follow these)

1. **Milestone loop.** For every milestone:
   1. write the code;
   2. write tests for every new or changed function, including JVM unit tests and on-phone UI tests (see `.claude/skills/test-every-function/SKILL.md`);
   3. run `test lintDebug connectedAndroidTest` until everything passes and lint reports no issues;
   4. install on the phone and **look at it** (screenshots via adb, section 13);
   5. tick the milestone in `docs/ClearRoom-Setup-and-Build-Plan.md` with a one-line "Done" note;
   6. add rows to the requirements doc's **Implementation notes** section;
   7. update **this file** (status, open decisions, commit table);
   8. make **one commit per milestone**, then push.
2. **Requirements doc:** only ever append to the "Implementation notes" section at the end. **Never edit the original sections.** Record every decision or deviation there (a table row: Area | Plan said | Built | Why).
3. **Commit messages** end with the attribution line used throughout the history:
   `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (adjust to your own model if you are a different agent). Push to `origin main`.
4. **Third-party tools:** the owner allows third-party MCP servers and CLIs, but you must **name each one before or when you use it, say what it does, and whether it needs an account or network**. Never log in to accounts or install things silently.
   `.mcp.json` configures Semgrep (`uvx semgrep-mcp`) and Context7 (`npx @upstash/context7-mcp`).
5. **Plain language in the UI (NFR-2):**
   - Verdicts use words like "tiring to follow".
   - Acoustics terms appear only with an explanation.
   - Every STI shown carries its range (NFR-10).
   - All strings live in `strings.xml`; counts use `<plurals>`.
6. **Ask before** anything hard to undo or outward-facing. Report test results honestly.
7. **Before the video:** remind the owner not to screen-record with the phone's microphone on, or ClearRoom hears silence.

## 3. Where things are

```
assignment_ed5518/                      git root (this repo)
├── AGENTS.md                           this file
├── CLAUDE.md                           imports this file for Claude Code
├── ClearRoom Speech Clarity Checker — Requirements.md   spec + "Implementation notes" (keep updated)
├── docs/ClearRoom-Setup-and-Build-Plan.md               setup guide, milestones M1–M13 with ticks
├── .claude/skills/<name>/SKILL.md      12 project guides; any agent can read them as docs
├── .mcp.json                           Semgrep + Context7 MCP config
├── tools/                              helper scripts (section 13)
└── ClearRoom/                          the Android Studio project
    ├── gradle/libs.versions.toml       all versions (version catalog)
    └── app/src/
        ├── main/java/com/clearroom/app/   the app (section 5)
        ├── test/java/...                  JVM unit tests
        └── androidTest/java/...           on-phone tests (run in the separate "uiTest" app)
```

Skills (each `SKILL.md` is a focused how-to with the project's rules):
- `clearroom-android-foundation`: architecture, packages, ViewModel and state rules
- `android-audio-capture`: AudioRecord, sources, effects off, level meter, privacy
- `acoustics-dsp`: biquads, octave bank, clap → RT60, Schroeder, quality checks
- `sti-room-model`: STI, Sabine fit, distance model, causes, what-if, required voice
- `compose-charts-2d`: hand-drawn 2D charts, axes, colours, view specs V1–V7, V13–V15
- `compose-3d-scene`: own 3D renderer and the 3D views V8–V12
- `noise-day-log`: the M10 day log, YAMNet labels, battery and privacy
- `storage-and-pdf-report`: JSON store, the PDF report (M11), before/after
- `clearroom-testing`: test strategy, the self-test screen, the field protocol; `scripts/sti_reference.py` gives golden STI values
- `kotlin-solid-clean-code` (+ `references/design-patterns.md`): code style and design rules
- `bug-vulnerability-audit` (+ checklists, Semgrep rules): run before releases and at M12
- `test-every-function` (+ `scripts/find_untested.py`): the testing rule

## 4. Toolchain, build and phone

**Owner's machine:** Windows 11, i5-10210U, 12 GB RAM. No emulator: everything runs on the real phone.

**Toolchain:**
- Android Studio Rabbit 1 (2026.2.1), with its bundled JBR (JDK 25).
- AGP 9.4.1 with built-in Kotlin. KGP 2.4.21 is forced through `buildscript { classpath(libs.kotlin.gradle.plugin) }`.
- Gradle 9.6.0, Compose BOM 2026.09.00 (material3 1.4, which has no icon library: the tab icons are vector XML files).
- lifecycle 2.11.0, coroutines 1.11.0.
- MediaPipe Tasks Audio 1.1.1 (sound labels, M10), with Google's YAMNet model bundled at `app/src/main/assets/yamnet.tflite` (`noCompress += "tflite"`). Downloaded once at build time; runs offline.
- Tests use org.json 20260814 (the real library, because android.jar's copy is a stub) and `kotlin-test-junit`.

**App configuration:**
- Package `com.clearroom.app`; `minSdk 29`, `targetSdk 37`.
- `testBuildType = "uiTest"`, with `applicationIdSuffix ".uitest"`, so on-phone tests install a separate app and **never wipe the owner's saved rooms**.
- **APK size cap: 95 MB** (owner's rule). `ndk.abiFilters` keeps only `arm64-v8a` and `armeabi-v7a`: MediaPipe's native library is 10–21 MB per processor type, and x86/x86_64 alone added 38 MB. The `check<Variant>ApkSize` task in `app/build.gradle.kts` runs after every packaging and with `check`, and fails the build above 95,000,000 bytes. Sizes now: debug 65.5 MB, release 36.7 MB (was 105 and 75). Lint's `ChromeOsAbiSupport` is disabled for this, with the reason in the build file.

**Command line (Git Bash).** Set these first:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ANDROID_HOME="D:/Android/Sdk" GRADLE_USER_HOME="D:/gradle-home"
cd ClearRoom
./gradlew.bat :app:compileDebugKotlin -q        # quick compile
./gradlew.bat test lintDebug -q                  # JVM tests + lint
./gradlew.bat connectedAndroidTest -q            # on-phone tests (phone unlocked!)
./gradlew.bat installDebug -q                    # install the real app
./gradlew.bat testUiTestUnitTest --tests "com.clearroom.app.viz.ChartKitTest"   # one JVM class (the plain `test` task rejects --tests)
```

**Where results land:**
- JVM: `app/build/test-results/testUiTestUnitTest/*.xml`
- Device: `app/build/outputs/androidTest-results/connected/uiTest/TEST-SM-S928B - 16.xml`
- Lint: `app/build/reports/lint-results-debug.txt`, which must end with "No issues found."

**Phone:** Samsung Galaxy S24 Ultra (SM-S928B, Android 16), connected over wireless adb.
- **Connect by service name, not IP:port.** With an IP:port serial, AGP 9.4's runner falsely reports failing tests.

  ```bash
  /d/Android/Sdk/platform-tools/adb.exe connect adb-RZCY61MJHWA-ZlwlRI._adb-tls-connect._tcp
  ```
- Keep the phone **unlocked** during `connectedAndroidTest`; its screen timeout is set to 10 min. A locked phone made 24 UI tests fail once.
- Before microphone tests, check `adb shell dumpsys telephony.registry | grep mCallState`, which should show `mCallState=0`. During a call Android gives other apps silence.
- In Git Bash, set `MSYS_NO_PATHCONV=1` on **adb** commands that take device paths like `/sdcard/...`. **Don't** set it while running gradlew: it breaks `JAVA_HOME`.

**Audio on this phone:** there is no `UNPROCESSED` source, so the app uses `VOICE_RECOGNITION` at 48 kHz float. A noise suppressor and an echo canceller come attached; the app switches both off with `setEnabled(false)`. No AGC is attached.

## 5. Architecture

**Packages:**
- **Engine (pure Kotlin, no `android.*`, `androidx.*` or `org.json` imports):** `dsp/`, `acoustics/`, `measure/`, `data/Models.kt`, `daylog/`, `report/Report.kt`, `viz/math3d/`. Tested on the plain JVM.
- **Android and UI:** `audio/` (including `YamnetLabeller.kt`), `data/Store.kt` + `RoomJson.kt` (org.json, AtomicFile), `permission/`, `report/` (the rest: words, page, PDF writer, sharing), `ui/`, `viz/` (Compose).

**Data flow:**
- `MainActivity` creates the `AppViewModel` through `AppContainer`, the composition root (Recorder, JsonRoomStore, dispatchers).
- `AppViewModel` holds a single `MutableStateFlow<UiState>` (session, room form, measure, fixes). The screens are stateless and send `UiEvent`s to `vm.onEvent`.
- Derived flows are computed on the DSP dispatcher, and each result carries the inputs it came from, so screens never mix one room's results with another room:
  - `vm.clarity: StateFlow<Evaluation?>`, where `Evaluation(session, result: ClarityResult, grid: ClarityGrid?, before: Evaluation?)` comes from `Evaluation.of(session)`; `before` is the same room with the "before" readings while comparing (FR-13).
  - `vm.fixes: StateFlow<FixesView?>`, where `FixesView(session, settings, outcome)` comes from `FixesView.of(...)`. It uses `conflate()` because sliders change fast.
  - `vm.log: StateFlow<DayLogView?>`, where `DayLogView(log: DayLogState, logSpot, summary: LogSummary?)` combines the day-log controller's state with the session and the picked seat.
  - `vm.liveLevelDb` is kept separate, so the level meter doesn't recompose the app.
- `ClearRoomApp`: a `Scaffold` with 5 tabs (Room, Measure, Results, Fixes, Log). The selected tab is kept with `rememberSaveable`. While a day log runs, `KeepScreenOn` holds the screen on app-wide.
- **Report (FR-12):** `UiEvent.ShareReport` → `UiState.report` goes `Writing` → `Ready(file)` (or `Failed`); `ClearRoomApp`'s `ShareReport` effect opens the share sheet once and sends `UiEvent.ReportShown`. The ViewModel builds `Report.of(session, clock())` on the DSP dispatcher and calls the injected `ReportWriter` on IO.
- **One recording at a time:** the day log and a measurement share the microphone. A measurement is refused while a log runs (`CaptureProblem.IN_USE_BY_DAY_LOG`) and Start log is disabled while measuring. Opening or creating another room stops the log; deleting a room deletes its logs.

**Key files:**
- **DSP:**
  - `dsp/Biquad.kt`, `OctaveBank.kt`, `OctaveBands.kt`: 7 bands, 125 Hz–8 kHz; `SPEECH_BANDS` = 500 Hz–2 kHz (indices 2..4).
  - `Levels.kt`
  - `Reverb.kt`: clap → `BandDecay.Ok/Rejected`, Schroeder curves, waterfall.
  - `Sti.kt`
  - `DspConstants.kt`: every tuned number.
- **Measuring:** `measure/MeasurementRunner.kt` (countdown + record + analyse) and `measure/ClapTake.kt` (accept/reject a clap; `curvesOf` keeps the curves).
- **Acoustics:**
  - `acoustics/ClarityModel.kt`: `evaluate`, `floorGrid`, `requiredVoice`, `limitingCause`; `RoomClarity`, `SeatClarity`, `ClarityGrid`, `WhatIf`.
  - `acoustics/Fixes.kt`: `FixSettings`, `FixPreset`, `rank`, `outcome`.
  - `RoomModel.kt`: `SabineRoom`, `SpeechPropagation`, `NoiseField`.
  - `SeatLayout.kt`, `Rt60Summary.kt`, `StiBand.kt`, `VoiceFallback.kt`.
- **Data:** `data/Models.kt` contains:
  - `Room`, `Session`, `SeatMeasure` (noise, fans, `rtTakes`, `lastClap: ClapCurves?`), `BandLevels` (a 7-value list), `RtTake`;
  - `ClapCurves` / `BandCurve` / `DecayFit`.

  `RoomForm.kt` validates the room form, including the seat-fit check. `SampleData.kt` is a 9 × 7 × 3.2 m room with 5 × 6 seats and 3 measured seats.
- **Day log (M10):**
  - `audio/AudioInput.blocks(blockSeconds)`: an endless flow of back-to-back clips from one open microphone (`Recorder` shares the open/effects/release code with `record`).
  - `dsp/StreamingOctaveBank.kt`: band levels per 1-s block with filter state kept across blocks.
  - `daylog/LogModels.kt`: `LogSecond`, `SoundLabel` (fan, traffic, horn, talking, music, quiet, other), `SoundLabeller`, `SoundLabels.fromYamnet`.
  - `daylog/LogStats.kt`: periods, Leq, L90, events, `backRowSti` (FR-10), `logSpot`; `LogSummary`.
  - `daylog/LogStore.kt`: `CsvLogStore` (`filesDir/logs/<roomId>/<start>.csv`).
  - `daylog/DayLogController.kt`: start, stop, show the latest log, clear.
  - `audio/YamnetLabeller.kt`: MediaPipe wrapper; `create()` returns null if the model can't load, and the log keeps levels only.
- **Report (M11):**
  - `report/Report.kt` (pure): `Report.of(session, millis)` with `now`/`before: ReportSide` (clarity, grid, `Rt60ChartModel`), `topFixes` (gain ≥ `MIN_GAIN` 0.01, at most 3), `ReportConditions`, `echoAxisTopS` (one scale for both sides); the `ReportWriter` interface.
  - `report/ReportText.kt`: every line of the page from strings.xml; `kiloHertz()`.
  - `report/ReportPage.kt`: `drawReport()` lays out one A4 page (595 × 842 pt, density 1) and returns whether it fitted; the charts are `drawSeatMap` / `drawRt60Chart` inside `inset(...)` boxes, styled by `ChartStyle.print()`.
  - `report/PdfReportWriter.kt`: `PdfDocument` + `CanvasDrawScope`; keeps only the newest report in `cache/reports/`; `ReportSharing.chooser()` builds the share intent.
  - Shared words: `rangeText(resources, …)` in `ResultsParts.kt`, `rt60Caption(resources, model)` in `Rt60Chart.kt`; `FixPreset.nameRes`, `CostTier.textRes`, `signedSti`, `FanState.labelRes` are `internal`.
- **UI:**
  - `ui/screens/`: `RoomScreen`, `MeasureScreen`, `ResultsScreen`, `FixesScreen`, `LogScreen`, plus `ResultsParts.kt` (shared cards, band/cause wording, and `ConfirmFirst`, a button that asks before it acts).
  - `ui/theme/`: `StiColors.kt` (fixed band colours), `SceneColors.kt` (3D palette).
- **Visualizations:**
  - 2D: `viz/ChartKit.kt` (Axis with nice ticks, `drawFrame`, `ChartStyle`, `ChartColors`, `decimals()`), `SeatMap.kt`, `Rt60Chart.kt`, `DecayChart.kt`, `SeatCharts.kt` (V13–V15), `FixCharts.kt` (V6, V7), `DayLogCharts.kt` (V5, clock times, sound-label colours), `RoomPlan.kt`.
  - 3D: `viz/Scene3D.kt` (renderer, gestures, `FramedScene3D`), `ClarityRoom3D.kt` (V8, V12), `Surfaces3D.kt` (V9, V10, and `chartView()` shared with V11), `NoiseLandscape3D.kt` (V11), and `math3d/` (`Vec3`, `Mat4`, `OrbitCamera`, `Projection`, `Face3`, `Solid`, `SceneGeometry`, `ScenePass`, `Argb`).

**3D renderer:**
- **Painting order:** the ground layer (inward-facing walls and the floor) is drawn first, so walls nearest the camera are culled into a cut-away view. Solids such as bars and the cone are then sorted as wholes, loose mesh faces individually, followed by lines and labels.
- **Gestures:** one finger sideways turns the scene; one finger up or down scrolls the page; two fingers zoom and tilt; double-tap resets.

## 6. Engine: methods and the numbers that matter

**Recording:**
- Clap: 5 s, after a 3 s countdown (10 s optional, so one person can walk to the front). 3 takes per seat.
- Noise: 30 s, with the fan state recorded.
- Voice: 20 s at 1 m while reading the given paragraph.
- Clips are rejected if clipped (3 or more samples at |x| ≥ 0.99) or silent (below −90 dBFS).

**Echo (clap → RT60):**
- The onset is the first point within 20 dB of the peak; the clap must stand at least 12 dB above the background.
- Noise floor: the lower of the 0.3 s before the onset (ending 50 ms before it) and the last 0.5 s.
- Each band is filtered on the time-reversed clip, needs at least 30 dB of dynamic range, and is truncated at noise + 5 dB.
- The Schroeder curve is fitted with T20 (−5 to −25 dB). A band is refused if its non-linearity ξ exceeds 40 ‰, or if T60 falls outside 0.1–6 s.
- A take counts when 500 Hz, 1 kHz and 2 kHz are all valid. Missing bands borrow the nearest band and are marked "estimated".

**STI:**
- IEC 60268-16 indirect method, with 14 modulation frequencies and the band weights.
- Adds the Houtgast–Steeneken direct-sound term: m = |d + 1/(1 + jx)| / (d + 1) × the noise term. With d = 0 this is exactly the doc's formula.
- Golden tests come from `.claude/skills/clearroom-testing/scripts/sti_reference.py --direct`.

**Room model:**
- Sabine fit on the room-mean echo time, pooled from every clap at every seat (A = 0.161·V/T60), giving the room constant R.
- Speech level by distance uses Q = 2; critical distance = 0.057·√(QV/T60).
- Noise at seats that weren't measured: inverse-distance-squared energy interpolation.
- Layout (doc "Room defaults"): the speaker stands 1 m from the front wall, centred. Rows run from 2.5 m to (length − 0.8 m), seats across between 0.8 m side margins. The form refuses seats closer than 0.5 m.

**Fallback voice:** the ANSI S3.5 normal-effort spectrum at 1 m (about 60 dBA), converted to phone units with SPL = dBFS + 112.35 (Android CDD). Results then say "uncalibrated".

**Bands and verdicts:**
- Bands: < 0.45 poor, 0.45–0.60 fair, 0.60–0.75 good, ≥ 0.75 excellent. The target is 0.60.
- A row is judged by its **worst** seat.
- The range shown is the STI with the echo time at mean ± its spread.

**Main cause:**
- Built as **rule A:** the largest gain among three single changes (noise removed, echo removed, talker at 1 m), with ties going to echo, then noise.
- **Not yet confirmed by the owner** (section 10).

**Required voice:** bisection over −20 to +40 dB; returns `Unreachable` when echo caps it.

**Day log:**
- 1-s blocks: 7 band levels plus a YAMNet label per second (score ≥ 0.3, mapped to 7 plain words; "Honk" is a goose, so it maps to other).
- Periods: 1 min for logs up to 15 min, 5 min up to 2 h, 1 h beyond. Leq = energy mean; L90 = 10th percentile of the seconds.
- Events: same-label seconds merged across gaps up to 10 s; "other" and "quiet" never make an event.
- Back-row STI per period (FR-10): each period's per-band change versus the noise snapshot at the log seat is applied to every measured seat, then the engine runs again.
- Approximate dBA on the Log tab = dBFS + 112.35, always labelled "about".

**Before and after (FR-13):** `Session.startRemeasuring(millis)` moves the seats into `Baseline` (voice kept) and leaves "after" empty; `Session.before` is the room with the "before" readings; `endComparison()` drops "before", or restores it if nothing new was measured.

**Fixes (`Fixes.kt`):**
- Curtains use the doc's α table: 0.14, 0.35, 0.55, 0.72, 0.70, 0.65, 0.65.
- Each student adds 0.15, 0.25, 0.35, 0.45, 0.50, 0.50, 0.50 m² of absorption per band.
- Fans off: −6 dB noise, offered only if fans were on. Microphone: +10 dB.
- Voice effort steps: raised +6 dB, loud +12 dB (ISO 9921).
- Presets for the ranking: 20 m² of curtains, fans off, microphone, standing 2 m closer, raised voice.

**Sample room results:**
- Front row Fair 0.59, back row Fair 0.57–0.58, main cause echo; echo time about 1.1 s.
- Curtains are the best single fix (+0.07). 40 m² of curtains plus a microphone gives Good 0.70.
- No voice reaches 0.60: the echo caps it.

The **Implementation notes** in the requirements doc list every deviation with its reason. Read them before changing the engine.

## 7. Data and storage

- Rooms are stored one per file at `filesDir/rooms/<id>.json`, written with AtomicFile, at `schemaVersion` 1.
- Non-finite numbers are written as `null`. Older files without `lastClap` still load, and damaged curves are dropped without losing the room.
- The sample room is never written to disk. "Save" on the sample copies only its layout, with a new id (a bug once copied its made-up readings).
- Day logs are stored one per file at `filesDir/logs/<roomId>/<start millis>.csv`: one line per second (time, 7 band levels, label word), flushed every 60 s. A cut-off last line is skipped on reading; room ids are checked against `[A-Za-z0-9-]{1,64}` before they become a folder name.
- Room files also hold `mic` (input and sample rate of the latest reading) and `baseline` (the "before" readings while comparing). Both are optional at schema 1: older files load without them, and a damaged one is dropped without losing the room.
- Reports: `cacheDir/reports/ClearRoom-report-<yyyyMMdd-HHmm>.pdf`, newest only. The FileProvider (`${applicationId}.fileprovider`, `res/xml/file_paths.xml`) exposes only that folder, never `files/` (rooms, logs).
- No audio is ever stored (NFR-6), only numbers and labels. There is no INTERNET permission.

## 8. Screens and views

**Tabs:**
- **Room:** the form, with the seat-fit check and its "Only N rows fit" message; floor plan; saved rooms (open, delete with a confirmation, damaged files listed); "Load sample room".
- **Measure:**
  - a seat picker, with ticks on seats that have readings;
  - the clap test (3 takes; RT60 table per band);
  - noise (30 s, with the fan state);
  - voice (20 s);
  - "Clear this seat's readings";
  - while re-measuring, a note that new readings fill in "after".
- **Results:**
  - a verdict card with "Share report (PDF)";
  - while comparing, a "Before and after" card (back rows and echo time before → after, the two seat maps side by side, the two echo charts on one scale, "Stop comparing");
  - a 3D room / seat map / cause map toggle;
  - seat details with V15 (tap a seat; the tab opens on the worst back-row seat);
  - cards for V9, V3, V4, V10, V13 and V14;
  - at the end, "Made a change?" with "Re-measure after a change" (own rooms only, after a confirmation).
- **Fixes:**
  - a before/after summary;
  - V12;
  - controls (curtains, students, fans, microphone, voice, speaker position, reset);
  - V6;
  - V7.
- **Log:**
  - start/stop, with the "Recording levels, not audio" banner, the timer, the live level and the log seat;
  - a summary (start, length, about-dBA Leq and background, loudest event);
  - V5 (noise panel with events above, back-row STI panel below);
  - V11;
  - the latest 12 events;
  - "Clear this room's day logs" (with a confirmation; not while logging).
  - The sample room can't be logged into.

| View | Status |
| --- | --- |
| V1 seat heatmap, V2 cause map, V3 decay curve, V4 RT60 by band, V5 day clarity timeline, V6 fix ranking, V7 required voice | done |
| V8 3D room, V9 clarity terrain, V10 echo waterfall, V11 noise landscape, V12 what-if morph | done |
| V13 MTF, V14 speech vs distance, V15 octave spectrum | done |

All 15 views are built.

**The PDF report** (one A4 page): title, room, date and size (plus a "made-up readings" line for the sample); back-row verdict with range and cause, front rows, before → after while comparing; seat map (two maps while comparing) with the band ranges as legend; echo time by pitch (two charts on one scale while comparing); the best fixes with gain range and cost, plus the echo note; how it was measured; a footer explaining the 0–1 scale and that it is not a compliance test.

## 9. Milestones

| # | Milestone | Status / commit |
| --- | --- | --- |
| M1 | Skeleton, theme, tabs, mic permission | done `ac4006c` |
| M2 | Recorder, live level meter, ViewModel, AppContainer | done `f9daf9a` |
| M3 | Models, JSON store, Room tab, sample room | done `9e09c2a` |
| M4 | Octave filters, noise and voice levels, seat picker | done `cba8d1c` |
| M5 | Clap test, RT60 per band, 3 takes | done `b11a6b7` |
| M6 | Clarity engine (STI, causes, what-if, voice) | done `fac05c4` (+ `027d0d4`, fix `e38093f`) |
| M7 | Scene3D + V8 | done `ba14819` (+ `5bf5667` seat fit, `de960ab` V8 look) |
| M8 | Results tab: verdict, V1, V2, seat details, V3, V4, V9, V10, V13–V15 | done `4a63fda` |
| M9 | Fixes tab: what-if controls, V12, V6, V7 | done `76e3515` |
| M10 | Log tab: day noise log (1 s band levels), YAMNet labels, keep-screen-on, V5, V11, clear logs | done `6bf1863` |
| M11 | PDF report + share (FileProvider, ACTION_SEND), before/after (FR-13) | done `8c50b31` |
| **M12** | **Finish: empty states, error text, app icon, release APK; full bug and vulnerability audit** | **next** |
| M13 | Testing phase: in-app self-test screen + field protocol in 3–4 rooms (including a 10-minute classroom day log, and predicted vs measured change after hanging curtains) | to do |

**Notes for M12:**
- **Start with the redesign** (owner's decisions, 2026-10-27), inspired by the Dribbble shot "Crypto Trading Mobile App" (dribbble.com/shots/25559085). Dribbble blocks automated fetching, so ask the owner for a screenshot.
  - Light by default; dark when the phone is in dark mode (both polished).
  - Lime accent `#D4F25C` with black (`#111318`) pill buttons and nav bar; white cards on a soft grey background; big bold numbers for the verdicts.
  - Font: **Manrope**, bundled (Google Fonts, OFL licence; a third-party download, so tell the owner when fetching it).
  - The clarity band colours (`StiColors.kt`) never change, and nothing else may use red, orange or blue.
  - Then do the rest of M12 in the new style.
- Read `.claude/skills/bug-vulnerability-audit/SKILL.md` (and its checklists) first; the Semgrep MCP failed to connect in the M11 session, so check it or run `semgrep` another way, and name every scanner before using it.
- Look for: empty states and error text on every tab, a real app icon (the template one is still in place), the app name, a signed release APK (signing keys stay out of git; ask the owner where to keep them).
- The release build has `optimization { enable = true }`: check that MediaPipe, org.json and Compose still work in release (install the release APK and run the sample room, a clap, a day log and a report).
- Before the video, remind the owner not to screen-record with the phone's microphone on.

## 10. Decisions still open (owner's call; current default in bold)

1. **Main-cause rule:**
   - **A, built:** whichever single change helps most.
   - B: the doc's literal rule (beyond the critical distance means "distance"; in a classroom almost every seat is).
   - C: rank only the fixes a school can make.
2. **Distance model:** **classical Sabine field, built.** Barron's revised theory would make back rows a little worse; it was offered and the owner hasn't answered.
3. **Fix costs in ₹:** the doc wants ₹ prices on V6. **The app shows free / low / medium cost** until the owner gives prices (for 20 m² of heavy curtains and a portable mic set).
4. **Soft-board absorption:** there is no sourced α table, so **soft boards are left out** of the fixes. The same applies to tile, glass and wood (plan Part E #6). Surface tags aren't used by the engine, because the Sabine fit uses measured RT60.
5. **Predicted vs measured change (testing table, "what-if realism"):** not built. The app shows before and after, but doesn't yet compare the measured change with the Fixes tab's prediction. Planned for the M13 curtain test.
6. **Screen-off day logging (NFR-9):** **not built.** The screen stays on while a log runs (plan Part E, decision 3). Logging with the screen off needs a microphone foreground service and its notification; offer it only if the owner asks.
7. Settled already:
   - noise snapshot 30 s;
   - clap rejection limit 30 dB;
   - ξ limit 40 ‰;
   - claps pooled per room;
   - a row is judged by its worst seat;
   - the sample can't be measured into; saving it copies the layout only;
   - day-log events: 10 s gap bridging, "other" excluded (seen on the phone: a fan's label flickers);
   - re-measuring clears the seats and keeps the voice; the report lists only fixes worth +0.01 or more;
   - APKs stay under 95 MB, for ARM phones only (section 4);
   - the M12 look: light + dark, lime on black, Manrope (section 9).
8. **Owner's own room:** saved as "Sample classroom", 3.5 × 2.5 × 3 m, but still with 5 × 6 seats (only 1 × 2 fit; the form now refuses this). It needs 1 row × 2 seats and fresh measurements. Its claps predate the saved curves, so V3 and V10 ask for a new clap.

## 11. Testing

**Current counts:** 530 JVM tests, 131 device tests, lint 0 issues. All pass at `8c50b31`.

**Conventions:**
- **Naming:** JVM tests use `kotlin.test` with backtick sentence names; androidTest uses camelCase names with JUnit4 and `createComposeRule` (v2).
- **Layout:** arrange, act, assert. Use fakes, not mocks.
- **Test helpers** (`app/src/test/java/com/clearroom/app/testing/`):
  - builders `aRoom()`, `aValidForm()`, `flatBands()`, `aMeasuredSeat()`;
  - `Signals.syntheticClap(t60S, seed)`;
  - `FakeAudioInput` (also fakes `blocks()`), `InMemoryRoomRepository`, `InMemoryLogStore` + `ScriptedLabeller`, `FakeReportWriter`, `MainDispatcherRule`.
- **ViewModel tests:**
  - `startApp()` creates the ViewModel and runs its startup.
  - `startWithRoom(...)` saves room "r1" first.
  - Use `runCurrent()` / `advanceUntilIdle()`.
  - A test that starts a day log must stop it (`UiEvent.StopLog` + `runCurrent()`): the fake block flow is endless, and `runTest` waits for it forever.
- **Device tests:**
  - Scroll before asserting: `performScrollTo()` for `verticalScroll` columns, `performScrollToNode` only in lazy lists.
  - Resolve strings with `context.getString` or `getQuantityString`.
  - A dialog's buttons can share text with the button that opened it: match with `hasAnyAncestor(isDialog())` (or its negation).
  - PDF tests render the page with `PdfRenderer` and look for the band colours; `drawReport` on a bitmap returns whether the page fitted.
- **Untested-function check:** `python -I .claude/skills/test-every-function/scripts/find_untested.py --main ClearRoom/app/src/main/java --tests ClearRoom/app/src/test/java ClearRoom/app/src/androidTest/java`. Composables covered by screen tests are an accepted exception.

## 12. Traps already hit (don't repeat them)

**Build and lint:**
- `@Test` comes from `kotlin-test-junit` (plain `kotlin-test` doesn't resolve it). Kotlin's `assert()` is a no-op in tests, so use `assertTrue`.
- Use `@param:StringRes` on constructor params; `AudioEffect.setEnabled(false)` (property syntax doesn't compile).
- `ui-test-manifest` must be added as `"uiTestImplementation"`, or you get "Unable to resolve activity ComponentActivity".
- Lint `PluralsCandidate`: write "%1$d of %2$d seats" as a `<plurals>`, or put the number at the end ("Students in the room: %1$d").
- `@ExperimentalMaterial3Api` is needed for `SegmentedButton`; `animateFloatAsState` in a loop is fine while the count is fixed (wrap it in `key(...)`).
- Two same-named `private`/`internal` composables in one package (`InfoCard`) cause overload ambiguity, so share them from `ui/screens/ResultsParts.kt`.
- A backtick test name can't contain `:` (it fails to compile on the JVM target); use `-`.

**Coroutines:**
- `withContext(NonCancellable + io) { … }` in a `finally` still rethrows `CancellationException` when it returns into the cancelled coroutine, so code **after** it never runs. Put every state update **inside** the block (this kept the day log saying "running" after Stop).
- An endless flow collected in `viewModelScope` keeps `runTest` busy forever: stop it in the test.

**Numbers and maths:**
- **Negative zero:** `round(x)*step` can give `-0.0`, which prints as "-0.0". `Axis.ticks` adds `+ 0.0`; `decimals()` prints 0 for values that round to zero.
- `Double` data-class equality treats `0.0 != -0.0`. Compare computed normals with a tolerance (the test helper `facing()`).
- JSON can't hold NaN or Infinity: write `null`.
- **Painter's sort:** a large floor polygon sorted with the bars covered the front rows. That's why the ground layer exists.

**Tooling and phone:**
- In Git Bash, heredocs containing `'` or `\'` break. Write a Python script to a file and run it with `python -I` (see `tools/`).
- `tools/sort_imports.py` and `tools/unused_imports.py` rewrite every file they're given (line endings too): pass **only the files you changed**, then check `git status` for strays.
- `tools/tap_text.py` finds adb through `ANDROID_HOME`: `export ANDROID_HOME="D:/Android/Sdk"` first, or it fails with "file not found". It matches the whole text exactly.
- The owner sometimes uses the phone while you script it (in M11 the app jumped to another room and tab between two steps). Check the screen before each tap, and ask if it changes under you.
- Claude Code's safety check refuses wildcard deletes inside `sh -c` (for example in `run-as`); delete files by their exact names.
- The Read tool can't render PDFs here (no poppler). To look at a report: `adb exec-out run-as com.clearroom.app cat cache/reports/<name>.pdf > report.pdf`, then render it with **pypdfium2** in a throwaway venv (third-party from PyPI: say so first).
- When writing a file that changed on disk since you last read it, read it again first.
- The app reopens on the **last tab**, so tap the Room tab before "Load sample room" when scripting taps.
- **The phone:** connect by service name, keep it unlocked, check the call state (section 4).

**Product:**
- Seats that don't fit merged into one block of bars, which is why the room form has a seat-fit check.
- The theme's primary blue looked like the "good" band colour. Fixed band colours live in `StiColors.kt`; the 3D scene uses `SceneColors`; other charts use `ChartColors` (never band hues).
- On the phone YAMNet's label for a steady fan flickers between "fan" and "other" every few seconds. With a 2 s gap, one minute of fan became a dozen events; hence the 10 s gap and no "other" events.

## 13. Checking on the phone (screenshots and taps)

Look at every new screen on the phone before calling a milestone done.

```bash
A=/d/Android/Sdk/platform-tools/adb.exe
MSYS_NO_PATHCONV=1 $A shell am force-stop com.clearroom.app
MSYS_NO_PATHCONV=1 $A shell am start -n com.clearroom.app/.MainActivity
python -I tools/tap_text.py "Room"; python -I tools/tap_text.py "Load sample room"; python -I tools/tap_text.py "Results"
MSYS_NO_PATHCONV=1 $A exec-out screencap -p > shot.png          # then view the PNG
MSYS_NO_PATHCONV=1 $A shell input swipe 60 2300 60 1000 600      # scroll (start at the left edge, outside 3D views)
```

The screen is 1440 × 3120 px. `tools/tap_text.py` taps the element whose text matches, using a uiautomator dump. It refuses to act unless ClearRoom is the app in front: the phone is the owner's own, so never tap or swipe in their other apps.

Other tools:
- `tools/sort_imports.py` sorts imports in ktlint order: everything else alphabetically, then `java`, `javax`, `kotlin`.
- `tools/unused_imports.py` removes unused imports (keeps `getValue`/`setValue`).

## 14. Resuming: checklist

1. `git pull`; read this file, then the requirements doc's Implementation notes, then the skill for the next milestone.
2. Set the environment variables (section 4), connect the phone by service name, and run `./gradlew.bat test lintDebug connectedAndroidTest -q` to confirm a green start: 530 JVM + 131 device tests.
3. Tell the owner what you're about to build, and which third-party tools it needs.
4. Follow the milestone loop (section 2). At the end, update this file's "Last updated" line, sections 8–11 and the commit table.
