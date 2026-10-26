---
name: sti-room-model
description: The ClearRoom clarity engine in pure Kotlin. Covers the indirect STI method (modulation transfer function, apparent SNR, transmission index, IEC 60268-16 band weights and redundancy), the echo-only and noise-only variants, the limiting cause per seat, the Sabine room fit, speech level vs distance with directivity and room constant, critical distance, the seat STI grid, the what-if simulator and fix ranking, required voice level, uncertainty bands and the hourly clarity timeline. Use it whenever writing or debugging Sti.kt, RoomModel.kt, seat verdicts, causes, what-if sliders, fix ranking (V6), required voice (V7), STI numbers, or anything that turns RT60 and noise into a clarity score, even if the user only says "the score looks wrong" or "add a fix".
---

# STI and room model

`dsp/Sti.kt` and `acoustics/RoomModel.kt` are pure Kotlin. Every formula below is one small named function with a KDoc line naming its source (doc section or standard). That makes each one testable, and lets the self-test screen run the exact same code (NFR-4).

## Constants (from the doc; verify the weights against IEC 60268-16:2020)

```kotlin
object StiConstants {
    val BANDS_HZ = intArrayOf(125, 250, 500, 1000, 2000, 4000, 8000)
    val MOD_FREQS_HZ = doubleArrayOf(0.63, 0.8, 1.0, 1.25, 1.6, 2.0, 2.5, 3.15, 4.0, 5.0, 6.3, 8.0, 10.0, 12.5)
    val ALPHA = doubleArrayOf(0.085, 0.127, 0.230, 0.233, 0.309, 0.224, 0.173)   // male
    val BETA = doubleArrayOf(0.085, 0.078, 0.065, 0.011, 0.047, 0.095)
    const val SNR_APP_LIMIT_DB = 15.0
    const val TARGET_STI = 0.60
}
```

Sanity check to keep as a unit test: `ALPHA.sum() - BETA.sum() == 1.0` (±1e-9), so a perfect channel scores exactly 1.0. If someone edits a weight and this fails, the weights are wrong.

## The STI chain

```kotlin
/** Doc "Analysis methods" step 4: Houtgast-Steeneken MTF with reverberation and noise. */
fun modulationIndex(fModHz: Double, t60S: Double, snrDb: Double): Double {
    val reverb = 1.0 / sqrt(1.0 + (2 * PI * fModHz * t60S / 13.8).pow(2))
    val noise = if (snrDb == Double.POSITIVE_INFINITY) 1.0 else 1.0 / (1.0 + 10.0.pow(-snrDb / 10))
    return reverb * noise
}

fun transmissionIndex(m: Double): Double {
    val snrApp = when {
        m >= 1.0 -> SNR_APP_LIMIT_DB
        m <= 0.0 -> -SNR_APP_LIMIT_DB
        else -> (10 * log10(m / (1 - m))).coerceIn(-SNR_APP_LIMIT_DB, SNR_APP_LIMIT_DB)
    }
    return (snrApp + SNR_APP_LIMIT_DB) / (2 * SNR_APP_LIMIT_DB)
}

fun bandMti(t60S: Double, snrDb: Double): Double =
    MOD_FREQS_HZ.sumOf { transmissionIndex(modulationIndex(it, t60S, snrDb)) } / MOD_FREQS_HZ.size

fun sti(t60PerBand: DoubleArray, snrPerBand: DoubleArray): Double {
    require(t60PerBand.size == 7 && snrPerBand.size == 7) { "need 7 bands" }
    require(t60PerBand.all { it.isFinite() && it >= 0.0 }) { "T60 must be finite and >= 0: ${t60PerBand.contentToString()}" }
    require(snrPerBand.none { it.isNaN() }) { "SNR is NaN: ${snrPerBand.contentToString()}" }   // +Inf means "no noise"
    val mti = DoubleArray(7) { bandMti(t60PerBand[it], snrPerBand[it]) }
    val weighted = (0 until 7).sumOf { ALPHA[it] * mti[it] }
    val redundancy = (0 until 6).sumOf { BETA[it] * sqrt(mti[it] * mti[it + 1]) }
    return (weighted - redundancy).coerceIn(0.0, 1.0)
}
```

- "Noise removed" means `snrDb = +∞`. "Echo removed" means `t60S = 0.0`. Express the echo-only and noise-only variants as calls to the same `sti()` with those inputs. Never copy the formula, because two copies drift apart.
- Bands: poor < 0.45 ≤ fair < 0.60 ≤ good < 0.75 ≤ excellent. Put this in one `enum class StiBand` with a `from(sti)` function, shared by the charts, the 3D scenes and the PDF.
- This is a simplified indirect method. IEC 60268-16:2020 also adds auditory masking and a reception threshold, which aren't modelled here. The UI shows bands, not "certified" values (NFR-10, risk table).

## Room model (acoustics/RoomModel.kt)

Notation per band k: `V = L·W·H`, `S = 2(LW + LH + WH)`.

1. **Sabine fit from measurement:** `A_k = 0.161·V / T60_k` (m² sabins), `ᾱ_k = A_k / S`. Clamp ᾱ to at most 0.95, because the formulas below divide by `1 - ᾱ`. Start the what-if simulator from these fitted values, not from the surface tags, so it begins from reality.
2. **Before any clap is measured** (preview only): `A_k = Σ S_i·α_i,k` from the FR-1 surface tags. The doc gives α only for heavy curtain and seated students. Put the other materials' α tables in one `Absorbers.kt` with a source comment for each row, and ask the user for the source table rather than inventing coefficients.
3. **Room constant:** `R_k = S·ᾱ_k / (1 - ᾱ_k)`.
4. **Speech level at distance r** (directivity Q = 2):
   `L_s,k(r) = L_1m,k + 10·log10((Q/(4πr²) + 4/R_k) / (Q/(4π) + 4/R_k))`
   This equals `L_1m` exactly at r = 1 m, which is a free unit test. Clamp r to at least 0.5 m.
5. **SNR per band at a seat:** `SNR_k = L_s,k(r) - N_k(seat)`.
6. **Critical distance:** `r_c = 0.057·sqrt(Q·V / T60)`. Use the mean T60 of the 500 Hz and 1 kHz bands and document the choice in KDoc. The doc doesn't say which band.

### Seat inputs for unmeasured seats

- **RT60:** use the room mean per band across measured seats. Reverberation is close to uniform in a room, and averaging is steadier than interpolating 3 noisy points.
- **Noise:** inverse-distance-weighted interpolation of each band, in energy (not dB), from the measured seats. With one measured seat, use its values everywhere.
- Then compute STI per seat from these inputs. This is better than interpolating STI values themselves (FR-7 says "interpolate STI"). It gives the same smooth map, and every seat's number comes from a real model evaluation, so the cause and the required voice level exist for every seat too.

### Seat geometry defaults (doc)

Speaker 1 m from the front wall, centred. Seats spread in depth from 2.5 m to (length - 0.8 m), with 0.8 m side margins. Use the floor distance. The "back row" is the row farthest from the speaker, and **back-row STI = the minimum over that row**, because the goal is that every seat can follow. Define this once in `backRowSti()` and tell the user if you choose the mean instead.

## Limiting cause (doc step 5)

```kotlin
enum class Cause { ECHO, NOISE, DISTANCE }

fun limitingCause(actual: Double, withNoiseRemoved: Double, withEchoRemoved: Double,
                  distanceM: Double, criticalDistanceM: Double): Cause {
    if (distanceM > criticalDistanceM) return Cause.DISTANCE
    val gainIfQuiet = withNoiseRemoved - actual       // large → noise is the problem
    val gainIfDry = withEchoRemoved - actual          // large → echo is the problem
    return if (gainIfQuiet > gainIfDry) Cause.NOISE else Cause.ECHO
}
```

Name the variables by what was removed. The classic bug is labelling "STI with noise removed" as `echoOnly` and then reporting the wrong cause. The doc doesn't say whether distance overrides the echo/noise comparison. This code reads "distance-limited when the seat is beyond the critical distance" as an override and checks it first. Confirm that reading with the user, and keep the rule in this one function.

## What-if simulator (FR-8) and fix ranking (V6)

Model the room as an immutable `AcousticState(absorptionPerBand, noisePerSeat, voiceAt1m, speakerPos, micGainDb)`. Each fix is a function `AcousticState -> AcousticState` (Command pattern, see `kotlin-solid-clean-code`). Then recompute `T60' = 0.161·V / A'` and everything after it.

| Fix (doc presets) | Change |
| --- | --- |
| Heavy curtain, 20 m² | `A'_k = A_k + 20·(α_curtain,k - α_covered,k)`. α_curtain = 0.14, 0.35, 0.55, 0.72, 0.70, 0.65, 0.65. If the covered surface is unknown, use α_covered = 0 and note that the gain is slightly optimistic. |
| Soft boards, 15 m² | Same formula with the soft-board α (needs a sourced table) |
| Fans off | Noise -6 dB in all bands, or the measured per-band difference when both fan states were recorded at that seat |
| Portable mic | Speech +10 dB at every seat (simplification: it ignores loudspeaker position) |
| Students present | `A'_k += N·a_student,k` with a = 0.15, 0.25, 0.35, 0.45, 0.50, 0.50, 0.50 m² each. Skip it if the measurement was done with the room occupied, otherwise the students are counted twice. |
| Move speaker / change voice | New position → new distances; voice shifts all bands of `L_1m` by Δ dB |

Ranking: apply each preset alone to the current state, compute the back-row STI gain, sort by gain descending, and show the cost. Costs in ₹ aren't given in the doc: keep them in one table and ask the user for real numbers. Show the predicted result as a range (risk table: Sabine can be off), for example the gain computed at T60 ± the take spread.

## Required voice level (FR-11, V7)

STI never decreases as voice level rises, so bisect over a uniform shift Δ ∈ [-20, +40] dB of all bands of `L_1m` until the back-row STI reaches 0.60 (tolerance 0.1 dB).

- If even Δ = +40 dB doesn't reach 0.60, return `Unreachable`. The seat is then echo-limited: its ceiling is the noise-removed STI. Tell the user a microphone or curtains are needed, not a louder voice.
- Reference voice levels at 1 m step 6 dB apart (ISO 9921): normal, raised = normal + 6, loud = normal + 12. With a measured voice reference, "normal" is the measured level, so no calibration is needed. With the fallback, use normal = 60 dBA, raised = 66, loud = 72, and mark the result uncalibrated (NFR-5).

## Fallback voice and the calibration catch

The 60 dBA fallback is an absolute level, but the phone measures noise in dBFS. Comparing them needs an assumed dBFS → dB SPL offset for the device. Keep it in one constant, and label every result that uses it "uncalibrated" (NFR-5). The fallback also needs a speech spectrum shape for the 7 bands. Take it from the IEC 60268-16 reference male speech spectrum, store it with a citation comment, and verify the values instead of guessing them.

## Uncertainty (NFR-10)

For each seat, compute STI at `T60_k ± sd_k`: the longer T60 gives the lower bound. Show the result as a band ("fair, 0.52–0.58"). With a single accepted take, show a fixed ±10 % RT spread and say why.

## Hourly clarity timeline (FR-10, V5)

For each logged hour, energy-average each band: `10·log10(mean(10^(L/10)))`. Use that as the back-row noise, recompute back-row STI, and flag hours below 0.60. The day log is recorded at one spot, so apply the hourly change relative to the snapshot taken at that spot to the other seats.

## Reference numbers

`clearroom-testing/scripts/sti_reference.py` is an independent Python version of this chain. Run it to get expected values for any T60/SNR case instead of working them out by hand. Two cases are worth remembering: T60 = 0, SNR = 0 dB → STI 0.500; T60 = 1.0 s, no noise → STI 0.588.
