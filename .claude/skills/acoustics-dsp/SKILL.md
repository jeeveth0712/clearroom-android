---
name: acoustics-dsp
description: Signal-processing recipes for ClearRoom's dsp/ package in pure Kotlin. Covers the RBJ biquad band-pass, the 7-band octave filter bank, band Leq and approximate dBA, clap detection, noise floor, Schroeder backward integration, T20/T30 line fits, RT60 quality checks and take rejection, take statistics, and the echo-waterfall matrix. Use it whenever writing or debugging Biquad.kt, OctaveBank.kt, Reverb.kt, band levels, RT60 numbers, decay curves (V3, V10) or noise and voice band levels, and whenever an RT60 looks wrong, a clap gets rejected or a filter "rings", even if the user doesn't say "DSP".
---

# Acoustics DSP for ClearRoom

Everything here lives in `dsp/` and is plain Kotlin with no Android imports. Work in `Double` and `DoubleArray`. Float rounding over 240,000-sample Schroeder sums is measurable, and boxed `List<Double>` in the hot loops costs both speed and memory. Every constant from the doc's "Exact parameters" table goes in one `DspConstants` object with the units in the name (`DECAY_SMOOTHING_S = 0.010`), so nothing is a magic number.

## 1. Biquad (RBJ band-pass, 0 dB peak gain)

```kotlin
/** One second-order section. Stateful: create a new one (or call reset) per signal. */
class Biquad private constructor(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {            // transposed direct form II
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() { z1 = 0.0; z2 = 0.0 }

    companion object {
        fun bandPass(centerHz: Double, q: Double, sampleRate: Int): Biquad {
            require(centerHz < sampleRate / 2.0) { "band $centerHz Hz is above Nyquist" }
            val w0 = 2 * PI * centerHz / sampleRate
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            return Biquad(alpha / a0, 0.0, -alpha / a0, -2 * cos(w0) / a0, (1 - alpha) / a0)
        }
    }
}
```

- Two sections in series per band, each with Q = 1.414 (doc parameter). Because they're in series, the -3 dB bandwidth comes out narrower than one octave. That's acceptable for this app, but don't "fix" it by changing Q without telling the user.
- The most common bug is reusing a filter across takes, bands or seats, so the old state leaks into the next signal. Build fresh filters per analysis call (it's cheap). The streaming day log is the one exception: it keeps state across 1-s blocks on purpose (see `noise-day-log`).

## 2. Octave bank and band levels

Bands: 125, 250, 500, 1000, 2000, 4000, 8000 Hz (index 0..6 everywhere, never by Hz lookups scattered in the code).

```kotlin
fun bandLevelsDb(clip: DoubleArray, sampleRate: Int): DoubleArray =
    DoubleArray(BANDS_HZ.size) { k -> leqDb(OctaveBank.filter(clip, k, sampleRate)) }

fun leqDb(x: DoubleArray): Double = 10 * log10(meanSquare(x) + ENERGY_FLOOR)   // ENERGY_FLOOR = 1e-20
```

- Skip the first 50 ms of each filtered signal when computing a snapshot Leq, so the filter's start-up transient isn't counted.
- All levels are dB relative to full scale (dBFS). The doc's rule is that only differences (SNR, decay) are shown as results.
- Approximate A-weighted level from the 7 bands: add the A-weighting corrections at the octave centres (IEC 61672: -16.1, -8.6, -3.2, 0.0, +1.2, +1.0, -1.1 dB) and sum the energies: `10*log10(Σ 10^((L_k + A_k)/10))`. Label it "dBA (125 Hz–8 kHz bands)" because content outside the bands is missing.

## 3. Clap → RT60 per band (Reverb.kt)

Run this pipeline per take. Each step is its own small function so it can be tested alone.

1. **Validate the clip.** Reject if clipped (see `android-audio-capture`). Find the broadband peak index `p`. Reject with "No clap found" if the peak is less than 0.3 s after the start (no room for the noise estimate) or within 1.5 s of the end.
2. **Filter per band.** Band-pass the whole clip (see time-reversed filtering below for low bands).
3. **Energy envelope.** Square the band signal and smooth it with a 10 ms moving average (doc parameter). Use the envelope for detection and plots. The Schroeder integral uses the unsmoothed squared signal.
4. **Noise floor** (doc rule): the lower of the mean energy over the 0.3 s before the clap, `[p - 0.3 s, p - 10 ms)`, and the mean energy of the last 0.5 s.
5. **Dynamic range** = envelope peak dB - noise floor dB. Reject the band if it's below the threshold. The doc gives two values: FR-2 says 25 dB, the parameters table says 30 dB. Use the table's 30 dB (a T20 fit down to -25 dB needs margin above the floor), keep it in one constant, and tell the user about the difference.
6. **Truncation point** `t_end`: the first index after the peak where the envelope falls to noise floor + 5 dB.
7. **Schroeder curve:** `E[n] = Σ_{i=n}^{t_end} x²[i]` as a reverse cumulative sum, then `L[n] = 10*log10(E[n] / E[p])`, so L starts at 0 dB at the peak.
8. **Line fit:** least squares of `L` against time over the samples where L is in [-5, -25] dB (T20). Fit T30 over [-5, -35] dB only if the curve reaches -35 dB before `t_end`. `T60 = -60 / slope` with slope in dB/s.
9. **Quality checks:** reject if `T60` is outside 0.1–6 s (doc), if the slope isn't negative, or if fewer than about 20 ms of samples fall in the fit range. Compute the non-linearity ξ = 1000·(1 - r²) per mille (ISO 3382). Above 10 ‰ the decay isn't a straight line, so flag "fit poor, retake". When both T20 and T30 exist, the curvature `C = 100·(T30/T20 - 1)` % above about 10 % points the same way.

```kotlin
sealed interface BandResult {
    data class Ok(val t20: Double, val t30: Double?, val xiPermille: Double) : BandResult
    data class Rejected(val reason: RejectReason) : BandResult
}
enum class RejectReason { CLIPPED, NO_CLAP, LOW_DYNAMIC_RANGE, FIT_POOR, OUT_OF_RANGE }
```

A sealed result makes "rejected" impossible to ignore. Never use `NaN` or `-1.0` as a "no value" marker in this package.

### Time-reversed filtering (low bands, short RT)

A band-pass filter rings for a short time of its own. At 125–250 Hz in a dead room (T60 around 0.3 s), that ringing is long enough to make the measured decay look slower. Filter the time-reversed clip and reverse the output again: the filter's ringing then lands before the clap instead of on the decay (ISO 3382-1 allows this). It's cheap, so use it for all bands, or at least for 125 and 250 Hz. Check that the synthetic self-test at T60 = 0.3 s passes in those bands.

### Mapping user messages

| Condition | Message (FR-2 wording) |
| --- | --- |
| Low dynamic range, noise floor low | "Clap louder, or try a balloon pop" |
| Low dynamic range, noise floor high | "Too noisy: switch off fans or wait for quiet" |
| Clipped | "Too loud: clap softer or step back" |
| Fit poor | "Echo measurement unclear, please retake" |

## 4. Takes and seats

- Three takes per seat (FR-2). Per band, use only the accepted takes. Report the mean, the standard deviation and the CV = sd / mean. Flag a CV above 10 % at 500 Hz–2 kHz (NFR-3).
- 125 Hz and 8 kHz are often rejected for claps (weak low end). The STI engine needs all 7 bands. Fill a missing band from the nearest valid band and mark it `estimated = true`, so the UI and report can say so. The risk table names 500 Hz–4 kHz as the primary bands.
- Keep the Schroeder curve and the fit line of the best take per band. V3 plots them.

## 5. Waterfall matrix for V10

For each band, take the envelope in dB relative to the band's own peak, sampled every 10 ms from the peak to peak + 2 s: 200 columns × 7 rows. Clamp at -60 dB so the floor is flat. Downsample on the DSP side, not in the renderer, because the 3D scene should receive about 1,400 points, not 96,000 per band.

## Numeric safety

- `log10(0.0)` is `-Infinity`, and `0/0` is `NaN`, which then spreads through STI silently. Add `ENERGY_FLOOR` inside every log. At function boundaries, check results with `require(x.isFinite())` and turn failures into a `Rejected` result.
- Guard every division by a count or a variance (an empty fit range or a constant signal).
- Convert the incoming `FloatArray` to `DoubleArray` once, at the entry point.

## Performance (NFR-9: result ≤ 5 s per clap)

A 5-s clip at 48 kHz × 7 bands × 2 sections is about 3.4 M multiply-adds, which takes milliseconds. Losing that budget is almost always due to allocation: `map`, `zip`, `windowed` or boxed lists over sample arrays. Use indexed loops over `DoubleArray` in the hot path and keep the functional style for the 7-element band arrays. Run analysis on `Dispatchers.Default`, and call `ensureActive()` between bands so a retake can cancel it.

## Testing

The `clearroom-testing` skill has the synthetic decay generator and the NFR-3 tolerances (±10 % or ±0.1 s). At minimum, cover: a known T60 is recovered in every band; clipped and low-range takes are rejected with the right reason; a pure 1 kHz tone puts its level in band index 3, at least 10 dB above its neighbours; Leq of a full-scale sine is about -3 dBFS.
