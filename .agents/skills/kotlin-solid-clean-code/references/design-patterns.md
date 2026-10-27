# Design patterns in idiomatic Kotlin, applied to ClearRoom

These examples illustrate design options. Prefer the current implementation and `AGENTS.md` where class names or project structure have changed.

Each entry gives the intent, the Kotlin-native way to write it, the ClearRoom use, and when **not** to use it. Kotlin features (first-class functions, sealed types, data-class `copy`, default arguments, `object`, delegation with `by`) replace much of the ceremony of the classic Java versions. Prefer the lighter form unless the heavier one buys something specific.

## Contents

- Creational: [Factory](#factory-and-static-factory) · [Builder](#builder) · [Prototype](#prototype-copy) · [Singleton](#singleton-object) · [Composition root](#composition-root-manual-di)
- Structural: [Adapter](#adapter) · [Facade](#facade) · [Decorator](#decorator) · [Composite](#composite)
- Behavioural: [Strategy](#strategy) · [Command](#command) · [State](#state) · [Chain of Responsibility](#chain-of-responsibility) · [Observer](#observer) · [Memento](#memento) · [Template Method → slots](#template-method--compose-slots) · [Iterator](#iterator) · [Mediator](#mediator) · [Visitor → sealed when](#visitor--sealed-when)
- Architectural: [Pipes and filters](#pipes-and-filters) · [Repository](#repository) · [Unidirectional data flow](#unidirectional-data-flow-mvvm)

---

## Creational

### Factory and static factory

Intent: construct a valid object under a name that says what you get, and hide the choice of concrete class.

```kotlin
class Biquad private constructor(/* coefficients */) {
    companion object {
        fun bandPass(centerHz: Double, q: Double, sampleRate: Int): Biquad { /* RBJ maths, validated */ }
    }
}

object AudioInputFactory {
    fun best(context: Context): AudioInput =
        if (supportsUnprocessed(context)) AndroidRecorder(context, MediaRecorder.AudioSource.UNPROCESSED)
        else AndroidRecorder(context, MediaRecorder.AudioSource.VOICE_RECOGNITION)
}
```

ClearRoom: `Biquad.bandPass`, `AudioInputFactory.best`, `StiBand.from(sti)`, `PresetFixes.curtains(areaM2 = 20.0)`.
Don't: wrap a plain constructor in a factory that adds nothing.

### Builder

Intent: build complex objects step by step with readable call sites. In Kotlin, default and named arguments plus `copy()` cover almost every case:

```kotlin
data class Room(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lengthM: Double, val widthM: Double, val heightM: Double,
    val rows: Int = 5, val cols: Int = 6,
    val surfacePreset: SurfacePreset = SurfacePreset.BARE_CLASSROOM,
    val students: Int = 0,
)
val lab = Room(name = "Physics lab", lengthM = 9.0, widthM = 7.0, heightM = 3.2)
```

For tree-shaped data, a small type-safe DSL reads well, for example 3D scenes:

```kotlin
fun scene(block: SceneBuilder.() -> Unit): SceneGeometry = SceneBuilder().apply(block).build()

val geometry = scene {
    floor(widthM = room.widthM, lengthM = room.lengthM)
    seats.forEach { bar(at = it.position, heightM = it.sti * room.heightM / 2, color = StiBand.from(it.sti).color) }
    speakerCone(at = speakerPos)
}
```

Don't: write a Java-style `RoomBuilder` class with `setX()` methods.

### Prototype (copy)

Intent: make new objects by copying an existing one with changes. Data-class `copy()` is the built-in prototype. The what-if simulator relies on it: `state.copy(micGainDb = 10.0)` leaves the original untouched for "before".
Watch out: `copy()` is shallow. A `DoubleArray` inside is shared between both copies, which is one more reason the models use `List<Double>`.

### Singleton (`object`)

Use `object` only for stateless things: constants (`StiConstants`), pure function groups, preset tables. Don't use it for anything holding a `Context`, a file handle, a cache or mutable state. Those hide dependencies, leak memory and make tests order-dependent. Create them once in `AppContainer` instead.

### Composition root (manual DI)

```kotlin
class AppContainer(context: Context) {
    val audioInput: AudioInput by lazy { AudioInputFactory.best(context) }
    val repository: RoomRepository by lazy { JsonRoomStore(context.filesDir) }
    val clarity: ClarityEngine by lazy { ClarityEngine(Reverb(), RoomModel()) }
}
```

One place knows the concrete classes. Everything else receives interfaces through constructors. This gives the testability of a DI framework without the setup cost of a 2-day sprint.

---

## Structural

### Adapter

Intent: make a third-party API fit an interface you own, so the rest of the code never sees it.

```kotlin
interface SoundLabeler : AutoCloseable { fun label(clip: AudioClip): String? }

class YamnetLabeler(private val classifier: AudioClassifier) : SoundLabeler {
    override fun label(clip: AudioClip): String? = /* AudioData.create ... classify ... LabelMap */
    override fun close() = classifier.close()
}
```

ClearRoom: `AndroidRecorder` adapts `AudioRecord` to `AudioInput`; `YamnetLabeler` adapts MediaPipe to `SoundLabeler`; `JsonRoomStore` adapts org.json and files to `RoomRepository`. When MediaPipe changes its API, only one file changes.

### Facade

Intent: one simple entry point over a subsystem.

```kotlin
class ClarityEngine(private val reverb: Reverb, private val room: RoomModel) {
    fun analyseClap(clip: AudioClip): TakeResult = reverb.analyse(clip)
    fun seatGrid(session: Session): SeatGrid = room.seatGrid(session)
    fun rankFixes(session: Session, fixes: List<Fix>): List<RankedFix> = room.rank(session, fixes)
    fun requiredVoice(session: Session): VoiceAdvice = room.requiredVoice(session)
}
```

The ViewModel and the self-test screen talk to `ClarityEngine` only.
Don't: let the facade grow logic of its own. It delegates and nothing more.

### Decorator

Intent: add behaviour by wrapping an object behind the same interface. Kotlin's `by` delegation removes the boilerplate:

```kotlin
class LoggingRoomRepository(
    private val inner: RoomRepository,
    private val log: (String) -> Unit,
) : RoomRepository by inner {
    override suspend fun save(session: Session) {
        log("save room=${session.room.id} seats=${session.seats.size}")   // numbers only, never audio
        inner.save(session)
    }
}
```

ClearRoom: logging or timing around the repository, throttling the live level source for the UI.

### Composite

Intent: treat single primitives and groups the same way. For Scene3D, every `SceneNode` (bar, box, mesh, group) can emit its faces into one `SceneGeometry`, so V8–V12 all use one renderer:

```kotlin
sealed interface SceneNode {
    fun emit(into: GeometrySink)
    data class Bar(val base: Vec3, val heightM: Float, val color: Color) : SceneNode { override fun emit(into: GeometrySink) { /* 5 visible quads */ } }
    data class Mesh(val grid: HeightGrid, val colorOf: (Float) -> Color) : SceneNode { override fun emit(into: GeometrySink) { /* cell quads */ } }
    data class Group(val children: List<SceneNode>) : SceneNode { override fun emit(into: GeometrySink) = children.forEach { it.emit(into) } }
}
```

---

## Behavioural

### Strategy

Intent: interchangeable algorithms behind one interface. In Kotlin, a function type is often enough. Use an interface when the strategy has a name, parameters or more than one method.

```kotlin
fun interface Rt60Estimator { fun estimate(curve: SchroederCurve): FitResult }

val T20 = Rt60Estimator { fitLine(it, fromDb = -5.0, toDb = -25.0) }
val T30 = Rt60Estimator { fitLine(it, fromDb = -5.0, toDb = -35.0) }

fun interface NoiseInterpolation { fun noiseAt(seat: Seat, measured: List<SeatMeasure>): List<Double> }
```

ClearRoom: T20/T30, inverse-distance vs nearest-seat noise, audio-source policy.
Don't: create a strategy interface with a single implementation "for the future".

### Command

Intent: wrap a change as an object, so you can list it, apply it, compare it and undo it. With immutable state, undo is just keeping the previous state.

```kotlin
interface Fix {
    val id: String
    val label: String               // plain language: "Hang 20 m² of heavy curtains"
    val costInr: IntRange?          // from the user's cost table; null = unknown
    fun applyTo(state: AcousticState): AcousticState
}

class AddAbsorber(private val areaM2: Double, private val alphaPerBand: List<Double>, override val label: String,
                  override val id: String, override val costInr: IntRange?) : Fix {
    override fun applyTo(state: AcousticState) = state.copy(
        absorptionM2 = state.absorptionM2.mapIndexed { k, a -> a + areaM2 * alphaPerBand[k] },
    )
}

fun rank(base: AcousticState, fixes: List<Fix>, score: (AcousticState) -> Double): List<RankedFix> {
    val before = score(base)
    return fixes.map { RankedFix(it, gain = score(it.applyTo(base)) - before) }.sortedByDescending { it.gain }
}
```

A scenario (FR-8 "save a scenario") is just `List<Fix>`, applied with `fold`.

### State

Intent: behaviour depends on a state, and only legal states can exist. Use a sealed hierarchy plus one transition function:

```kotlin
sealed interface MeasurePhase {
    data object Idle : MeasurePhase
    data class Countdown(val secondsLeft: Int) : MeasurePhase
    data class Recording(val kind: MeasureKind, val elapsedS: Double) : MeasurePhase
    data class Analysing(val kind: MeasureKind) : MeasurePhase
    data class Done(val result: TakeResult) : MeasurePhase
    data class Failed(val reason: RejectReason) : MeasurePhase
}
```

The Measure screen is a `when (phase)` with no `else`. This replaces flag soup like `isRecording && !isAnalysing && error == null`, where impossible combinations are representable.

### Chain of Responsibility

Intent: pass a request along a list of handlers. Each one can reject it, or let it continue.

```kotlin
fun interface TakeValidator { fun check(take: TakeContext): RejectReason? }

val takeChecks: List<TakeValidator> = listOf(
    TakeValidator { if (it.isClipped) RejectReason.CLIPPED else null },
    TakeValidator { if (it.peakIndex == null) RejectReason.NO_CLAP else null },
    TakeValidator { if (it.dynamicRangeDb < MIN_DYNAMIC_RANGE_DB) RejectReason.LOW_DYNAMIC_RANGE else null },
)

fun firstRejection(take: TakeContext): RejectReason? = takeChecks.firstNotNullOfOrNull { it.check(take) }
```

The order is the order of the messages the user should see first. Adding a check doesn't change the others.

### Observer

Kotlin already has it: `StateFlow` for state, `Flow` for streams, `collectAsStateWithLifecycle()` in Compose. Don't write `addListener`/`removeListener` lists. They leak when someone forgets to remove a listener, which lifecycle-aware collection handles for you.

### Memento

Intent: capture a snapshot to restore or compare later, without exposing internals. For FR-13, freeze `ResultsSnapshot` (an immutable data class) as `session.baseline` when the user starts "re-measure after a change". Immutability makes the snapshot safe without defensive copies.

### Template Method → Compose slots

The classic version (an abstract base class with overridable steps) fights Compose. Use composition with slot lambdas instead:

```kotlin
@Composable
fun ChartCard(title: String, caption: String, modifier: Modifier = Modifier, chart: @Composable () -> Unit) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(title); chart(); Text(caption) } }
}
```

Every V-chart gets the same frame. Outside Compose, prefer passing functions over inheritance as well.

### Iterator

Use `Sequence` for lazy, pull-based pipelines over in-memory data, and `Flow` for asynchronous streams (1-s audio blocks). Avoid `Sequence` over sample arrays in hot DSP loops, where indexed loops on `DoubleArray` are much faster.

### Mediator

`AppViewModel` is the mediator. Screens never talk to each other or share mutable objects. A Measure action updates the state, and Results reacts by observing it. Keep the mediator thin (it delegates to the facade), or it becomes a god class.

### Visitor → sealed `when`

The classic Visitor exists because Java lacked exhaustive pattern matching. In Kotlin:

```kotlin
fun Cause.advice(): String = when (this) {
    Cause.ECHO -> "Soft materials (curtains, boards) will help most."
    Cause.NOISE -> "Reduce background noise first: fans, windows, coolers."
    Cause.DISTANCE -> "Move the speaker closer or use a microphone."
}
```

---

## Architectural

### Pipes and filters

The DSP chain is a pipeline of pure functions: `bandFilter → energyEnvelope → noiseFloor → schroeder → fitLine → t60`. Each stage takes and returns plain data, so a failing self-test case can be debugged by printing stage outputs (see `clearroom-testing`).

### Repository

`RoomRepository` (an interface in the domain) hides where rooms live. `JsonRoomStore` is today's implementation. A future database or a fake for tests needs no other change.

### Unidirectional data flow (MVVM)

UI events go up (`onEvent(RoomEvent.LengthChanged(9.0))`), state comes down (`StateFlow<UiState>`). The ViewModel is the only writer. This is what makes state bugs reproducible: given the same sequence of events, you get the same state.
