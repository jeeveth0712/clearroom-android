---
name: clearroom-testing
description: Test strategy for ClearRoom. Covers JVM unit tests for the pure dsp/acoustics code, the in-app self-test screen (NFR-3, NFR-4) sharing one set of cases with the unit tests, a synthetic clap-decay generator, golden STI values from an independent Python reference script, room-model and voice-level checks, JSON round-trips, coroutine tests, and the real-room field protocol (repeatability, distance model, curtain test, fan test, listening check, usability). Use it whenever writing tests, building the self-test screen, checking RT60/STI accuracy, debugging "numbers look off", or planning the testing days, even if the user just says "is this correct?" or "verify the engine".
---

# ClearRoom testing

This skill is the strategy and the source of correct answers. For the per-change routine (tests for every new function, test types per layer, naming, coverage, the untested-function finder), use the `test-every-function` skill.

The app can't use a reference meter (NFR-0), so correctness rests on three things: synthetic signals with known answers, an independent reference implementation, and repeatability in real rooms. Unit tests and the in-app self-test run the **same cases**: put the case definitions in the main source set (`selftest/SelfTestCases.kt`). The `app/src/test` JUnit suite iterates them, and the self-test screen shows them as pass/fail rows. One list means the phone checks exactly what CI checks.

## Running tests

- `gradlew.bat testDebugUnitTest` (Windows) or `./gradlew testDebugUnitTest`. These are JVM tests for `dsp/`, `acoustics/`, `data/Models.kt`, the 3D maths and the axis helper.
- Command-line Gradle on this machine needs `JAVA_HOME` pointed at Android Studio's JBR (the `java` on PATH is 1.8).
- Coroutines: `kotlinx-coroutines-test` with `runTest`. Inject dispatchers (`AppViewModel(dsp = StandardTestDispatcher(testScheduler))`). Never call `Thread.sleep` in tests.
- JSON tests need `testImplementation("org.json:json:<latest>")` (see `storage-and-pdf-report`).

## Golden STI values (NFR-4: within ±0.02)

Produced by `scripts/sti_reference.py`, an independent Python version of the doc's formulas. Same T60 and SNR in all 7 bands:

| Case | Expected STI |
| --- | --- |
| T60 = 0, no noise | 1.0000 |
| T60 = 0, SNR = 0 dB | 0.5000 |
| T60 = 0, SNR = +15 dB | 1.0000 |
| T60 = 0, SNR = -15 dB | 0.0000 |
| T60 = 0, SNR = +6 dB | 0.7000 |
| T60 = 0.3 s, no noise | 0.8273 |
| T60 = 0.5 s, no noise | 0.7361 |
| T60 = 0.6 s, no noise | 0.6999 |
| T60 = 1.0 s, no noise | 0.5882 |
| T60 = 1.5 s, no noise | 0.4977 |
| T60 = 2.0 s, no noise | 0.4371 |
| T60 = 0.6 s, SNR = 10 dB | 0.6134 |
| T60 = 1.0 s, SNR = 5 dB | 0.4634 |
| T60 = 1.5 s, SNR = 0 dB | 0.3109 |
| T60 = 0.5, 0.6, 0.7, 0.8, 0.8, 0.7, 0.6 s; SNR = 20, 18, 15, 12, 10, 8, 6 dB | 0.5913 |

For new cases, run:
`python -I .claude/skills/clearroom-testing/scripts/sti_reference.py --t60 0.8 --snr 10` (one value or 7 comma-separated; `--snr inf` means no noise). Paste the result into `SelfTestCases.kt` with a comment naming the command.

The noise-only rows can be checked by hand: with T60 = 0, apparent SNR equals the real SNR, so STI = (SNR + 15) / 30. The doc asks for "hand-worked" expected values in the report, and this is the simplest one to show.

Properties that must hold for any input (test them across a grid of values):

- `ALPHA.sum() - BETA.sum() == 1.0`.
- STI never increases when T60 rises, and never decreases when SNR rises.
- STI with noise removed ≥ STI, and STI with echo removed ≥ STI.
- The result is in [0, 1] and finite.

## Synthetic clap decays (NFR-3: RT60 within ±10 % or ±0.1 s, whichever is larger)

```kotlin
/**
 * Exponentially decaying white noise: energy falls 60 dB in t60S, on top of a steady
 * noise floor [floorBelowPeakDb] under the decay's start. Seeded, so it's deterministic.
 */
fun syntheticClap(t60S: Double, sampleRate: Int = 48_000, lengthS: Double = 5.0,
                  clapAtS: Double = 1.0, floorBelowPeakDb: Double = 60.0, seed: Long = 42): FloatArray {
    val rng = java.util.Random(seed)
    val peak = 0.2                                     // 4.5 sigma stays below 0.99: no false "clipped"
    val floor = peak * 10.0.pow(-floorBelowPeakDb / 20)
    return FloatArray((lengthS * sampleRate).toInt()) { i ->
        val t = i.toDouble() / sampleRate - clapAtS
        val decay = if (t < 0) 0.0 else peak * exp(-6.9078 * t / t60S)   // ln(1000): amplitude -60 dB at t60S
        (sqrt(decay * decay + floor * floor) * rng.nextGaussian()).toFloat()
    }
}
```

- Grid: T60 ∈ {0.3, 0.5, 0.8, 1.2, 2.0} s × `floorBelowPeakDb` ∈ {45, 60} × 3 seeds. White noise decays the same in every band, so every band should report the input T60.
- The hard case is 0.3 s at 125–250 Hz, where filter ringing biases the result. If it fails, apply time-reversed filtering (see `acoustics-dsp`), not a wider tolerance.
- Negative cases: the same clip scaled up and clamped at ±1.0 gets `CLIPPED`; `floorBelowPeakDb = 20.0` gets `LOW_DYNAMIC_RANGE`; pure noise gets `NO_CLAP` or `LOW_DYNAMIC_RANGE`; a T60 of 8 s gets `OUT_OF_RANGE`.
- Performance (NFR-9): one synthetic clip analysed in under 5 s. Assert well under that on the JVM (for example < 500 ms) so slowdowns show early.

## Room model and voice checks

- Sabine round trip: T60 → A → T60 gives back the same value.
- Speech level at r = 1 m equals `L_1m` exactly. It falls monotonically with distance and levels off at the reverberant floor.
- Critical distance: `0.057·sqrt(2·V/T60)` for one hand-computed room.
- Sample room: back-row STI is below front-row STI (the Day 1 done criterion).
- Limiting cause: high noise with a dry room → `NOISE`; quiet with T60 = 2 s → `ECHO`; a seat beyond r_c → `DISTANCE`.
- Required voice: the returned shift actually reaches STI ≥ 0.60 (within 0.005). With T60 = 3 s it returns `Unreachable`.
- What-if: adding curtain absorption never raises T60, and turning fans off never lowers STI.

## Field protocol (after the build, all on the phone)

From the doc's testing table, kept here so it's at hand during testing days:

| Test | How | Pass if |
| --- | --- | --- |
| Echo repeatability | 5 claps per seat, 3 seats, 3 rooms | CV of RT60 ≤ 10 % at 500 Hz–2 kHz |
| Distance model | Record the same voice at 1, 3, 5, 8 m | Mean error between measured and predicted L_s(r) ≤ 3 dB |
| What-if realism | Measure, hang 2–3 bedsheets or curtains, re-measure | Predicted RT60 change within ±25 % of measured |
| Fan effect | Same seat, fans off vs full speed | Noise and STI move in the expected direction; cause flips to "noise" at full speed |
| Listening check | 2–3 volunteers write down 20 words read from the front, at a front and a back seat | Seats rated poorer get lower word scores (rank agreement) |
| Usability | 3 people new to the app run a check | ≤ 10 minutes (NFR-1); each can say the top fix in their own words |

Rooms: a classroom, a small seminar room, a tiled bathroom or corridor, a furnished bedroom. Log each field result in the app or a CSV with date, room, phone model and audio source. The report needs measurement conditions, and a failing test is only useful if the conditions can be reproduced.

## When a test fails

Find the first stage where the numbers stop matching (filter output → envelope → Schroeder curve → fit → T60 → MTI → STI) by printing that stage for the failing case. Don't widen a tolerance to make a test pass. If the doc's tolerance seems impossible to meet, tell the user with the measured numbers.
