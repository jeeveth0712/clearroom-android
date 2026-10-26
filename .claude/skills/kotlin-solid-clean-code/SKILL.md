---
name: kotlin-solid-clean-code
description: Design and code-quality rules for all ClearRoom Kotlin and Jetpack Compose code. Covers the SOLID principles applied to Kotlin, creational, structural and behavioural design patterns mapped to concrete ClearRoom classes (Strategy, Command, State, Chain of Responsibility, Observer, Adapter, Facade, Decorator, Composite, Memento, Factory, Builder), Kotlin idioms for immutability, null safety, sealed results, unit-safe naming and value classes, coroutine hygiene, Compose state hoisting, naming, function size, comments, and a review checklist. Use it whenever writing, refactoring or reviewing any Kotlin in this project, when choosing where logic belongs or how classes talk to each other, and whenever the user mentions SOLID, design patterns, clean code, readability, refactoring, "this file is getting big" or code review, even if no pattern is named.
---

# Kotlin: SOLID, patterns and clean code for ClearRoom

The goal is code that someone new to the project (or you in two days, at 2 am, during the sprint) can read top to bottom and trust. Every rule here serves that goal or testability. When a rule would make a small piece of code harder to read, readability wins: patterns are tools, not quotas.

The pattern catalogue with Kotlin code for each pattern is in `references/design-patterns.md`. Read the relevant entry before introducing a pattern, so it's applied the idiomatic Kotlin way rather than the Java way.

## Architecture shape

```
ui / viz (Compose) ──► AppViewModel ──► engine use cases ──► dsp / acoustics (pure Kotlin)
                                   └──► ports: AudioInput, RoomRepository, SoundLabeler
android adapters (Recorder, Store, YamnetLabeler) implement the ports
```

Pure code in the centre, Android at the edges. Dependencies point inward. That's Dependency Inversion at the module level, and it's what lets the whole engine run in JVM unit tests and on the self-test screen (see `clearroom-android-foundation` for the package rule and its grep check).

## SOLID in Kotlin, applied here

**S — Single responsibility: one reason to change.**
`Reverb.kt` turns a clip into RT60 results. It doesn't decide what the user reads: mapping `RejectReason.CLIPPED` to "Too loud: clap softer" happens at the UI edge, so copy changes never touch DSP. Smell: a file whose functions you'd describe with "and" ("records audio *and* computes band levels").

**O — Open/closed: add behaviour by adding code, not editing working code.**
Each what-if fix is one implementation of `Fix`. The ranking function iterates `PresetFixes.all` and never changes when a fix is added. Choose between the two Kotlin tools on purpose:
- `sealed` hierarchy + exhaustive `when` for **closed** sets where the compiler should force every place to handle a new case: `StiBand`, `Cause`, `MeasurePhase`, `RejectReason`.
- `interface` for **open** sets that grow: `Fix`, `AudioInput`, `TakeValidator`.

**L — Liskov substitution: every implementation honours the contract.**
`AndroidRecorder`, `FakeAudioInput` and `SyntheticAudioInput` must all return exactly the requested length, report the true sample rate, and release resources on cancellation. Write the contract in the interface's KDoc, and run one shared contract test against every implementation. A fake that quietly returns a shorter clip makes tests pass while the real app breaks.

**I — Interface segregation: clients depend only on what they use.**
The Measure screen's level bar needs `liveLevelDb` and nothing else, so it depends on `LiveLevelSource`. `AudioInput : LiveLevelSource` adds recording. `RoomRepository` is about rooms only; the PDF export is a separate `ReportExporter`. Smell: a fake in a test that has to stub five methods it never calls.

**D — Dependency inversion: depend on abstractions you own.**
The engine declares `interface SoundLabeler { fun label(clip: AudioClip): String? }`. The MediaPipe class implements it in the Android layer. Wire concrete classes in one place (`AppContainer`) and pass them through constructors. Avoid `object` singletons that hold a `Context` or mutable state. They hide dependencies and leak between tests.

## Pattern map (details and code in references/design-patterns.md)

| Pattern | Where in ClearRoom | Why it earns its place |
| --- | --- | --- |
| Strategy | `Rt60Estimator` (T20 / T30), `NoiseInterpolation` (IDW / nearest), audio-source policy | Swap algorithms without `if` chains; test each alone |
| Command | `Fix` objects applied to an immutable `AcousticState` | Ranking, undo and "save scenario" fall out for free |
| State | `MeasurePhase` sealed type: Idle → Countdown → Recording → Analysing → Result / Error | Impossible states can't be represented, and the UI is a `when` |
| Chain of Responsibility | Take validators: clipping → clap present → dynamic range → fit quality | Each check is small; the first failure gives the user one clear message |
| Observer | `StateFlow` from `AppViewModel`; `Flow<AudioClip>` blocks | Built into Kotlin; don't hand-roll listener lists |
| Adapter | `AndroidRecorder` (AudioRecord → `AudioInput`), `YamnetLabeler` (MediaPipe → `SoundLabeler`) | Third-party APIs stay at the edge |
| Facade | `ClarityEngine` over `Reverb`, `Sti` and `RoomModel` | The ViewModel calls 5 methods, not 25 functions |
| Decorator | `LoggingRoomRepository`, `ThrottledLevelSource` | Add a cross-cutting concern without editing the class |
| Composite | Scene graph: primitives combined into one `SceneGeometry` | V8–V12 share one renderer |
| Memento | Frozen `baseline` results for before/after (FR-13) | Immutable snapshot, no copying bugs |
| Factory / static factory | `Biquad.bandPass(...)`, `AudioInputFactory.best(context)` | Validated construction with a name that says what you get |
| Builder | Kotlin default + named arguments and `copy()`; a small DSL for scenes | Java-style builder classes are rarely needed in Kotlin |
| Pipes and filters | DSP chain: filter → envelope → Schroeder → fit | Each stage is testable and printable when debugging |

Visitor is almost never needed in Kotlin: an exhaustive `when` over a sealed type does the same job with less code.

## Kotlin idioms that keep code correct

- **Immutability by default.** `val`, data classes, read-only `List`/`Map` in public APIs, `copy()` for changes. Mutable state lives in one place: `MutableStateFlow` inside the ViewModel, plus local arrays inside DSP functions.
- **No `!!`.** Use `?: return`, `?: error("why this can't happen")`, or a sealed result. At trust boundaries (parsing JSON, intents), use `requireNotNull(x) { "message" }`.
- **Expected failures are values, bugs are exceptions.** A rejected clap is a `BandResult.Rejected`, not an exception. A negative room length is a bug: `require(lengthM > 0) { "lengthM=$lengthM" }`.
- **Units in names, always:** `t60S`, `snrDb`, `distanceM`, `centerHz`, `levelDbFs`. Physics code fails silently when seconds meet milliseconds. For public engine APIs where several same-typed parameters sit side by side, add `@JvmInline value class` wrappers (`Seconds`, `Decibels`, `Meters`) and call with named arguments. Keep raw `DoubleArray` inside hot loops.
- **Constants with provenance.** Every number from the doc goes in one constants object with its source in a comment (`/** Doc "Exact parameters": decay fit range. */`).
- **Exhaustive `when` without `else` on sealed types,** so adding a case is a compile error everywhere it matters.
- **Scope functions:** one level deep, picked by intent (`apply` to configure, `let` for a nullable, `also` for side effects). Nested `let`s are a readability bug.
- **Extension functions** for domain vocabulary (`DoubleArray.energyMeanDb()`), kept in the package that owns the concept. No broad extensions on `Any` or `String` in global scope.

## Coroutines

- Structured concurrency only: `viewModelScope` or a scope you own and cancel. No `GlobalScope`.
- Inject dispatchers. `Default` for DSP, `IO` for files and `AudioRecord` reads, never block `Main`.
- **`runCatching` inside suspend code also catches `CancellationException`,** which breaks cancellation (the Stop button stops working). Catch specific exceptions, or rethrow `CancellationException`.
- Call `ensureActive()` in long loops (bands, seats, bisection) so retakes cancel promptly.

## Compose

- Stateless composables: `fun SeatHeatmap(model: SeatMapModel, onSeatTapped: (SeatId) -> Unit, modifier: Modifier = Modifier)`. `modifier` is the first optional parameter and is applied to the root.
- Hoist state to the screen level. Composables don't compute STI, parse JSON or touch the recorder.
- Pass immutable models. Mark them `@Immutable` when they hold `List`s, so Compose can skip recomposition.
- `remember(key)` with the right keys. `rememberSaveable` for user choices that should survive rotation.
- Every screen and chart has a `@Preview` using `SampleData`, in light and dark.

## Naming, functions, comments

- Use the doc's domain words consistently: seat, take, band, decay, clarity, fix, noise snapshot, voice reference. Never call one thing two names (pick "take", not "take" in one file and "attempt" in another).
- Avoid vague names such as `Manager`, `Helper`, `Util`, `process()` and `handle()`. A name says what the thing is or does: `ClapAnalyser`, `analyseTake()`.
- Booleans read as facts: `isClipped`, `hasVoiceReference`. Avoid boolean parameters at call sites (`analyse(clip, true)`). Use an enum or a named argument.
- Functions: one level of abstraction, usually under 30 lines, at most 4 parameters (otherwise a parameter object). Long DSP formulas are fine if each line is one named step.
- Comments explain **why** and cite sources (`// ISO 3382-1: time-reversed filtering moves filter ringing before the onset`), not what the next line does. KDoc on public API states units and ranges. No commented-out code. A `TODO` names what's missing and why.
- Keep acoustics abbreviations (RT60, STI, SNR, MTI, Leq, L90) in a glossary in the README. Use them in engineering code, never on the main UI (NFR-2).

## Formatting and static checks

Use ktlint's official Kotlin style and detekt with the default rule set plus complexity limits (they're set up in the `bug-vulnerability-audit` skill). Fix warnings instead of suppressing them. A suppression needs a comment explaining why.

## Review checklist (run it on every change)

1. Does each new class or file have one reason to change?
2. Did adding the feature require editing a `when` that isn't over a sealed type? If yes, consider Strategy or Command.
3. Are dependencies passed in through constructors, with no hidden singletons or `Context` in the engine?
4. Are there no `!!` and no ignored nullable or `Result` values?
5. Does every physical quantity's name carry its unit?
6. Are expected failures modelled as sealed results that the UI turns into plain-language messages?
7. Does any coroutine code swallow `CancellationException`?
8. Are Compose functions stateless with hoisted state and previews?
9. Is every doc number in the constants object with its source?
10. Could a new teammate understand the file without opening three others? If not, rename or split it before adding comments.
