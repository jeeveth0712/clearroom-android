---
name: acoustics-dsp
description: Change or debug ClearRoom's octave filters, band levels, clap detection, Schroeder decay, RT60 estimation, or echo curves. Use for code in dsp/ and measurements with incorrect frequency or decay results.
---

# Acoustics DSP

Use the implemented code in `ClearRoom/app/src/main/java/com/clearroom/app/dsp/` and the numeric decisions in `AGENTS.md` section 6. Keep this engine pure Kotlin and use the clip's real sample rate. Put tuned values in `DspConstants.kt` with units in their names.

- Seven octave bands run from 125 Hz to 8 kHz; speech bands are indices 2–4 (500 Hz–2 kHz). Biquad state must be fresh for each independent measurement. The streaming day log is the exception: its filter state persists between one-second blocks.
- Compute energy and Leq in linear energy before converting to dB. Use `Double` for cumulative energy and guard every log and denominator against zero or non-finite values.
- Clap onset is the first point within 20 dB of the peak, with at least 12 dB above background. Estimate the noise floor from pre-onset and tail windows; filter the time-reversed clip for the decay curves.
- Each RT60 band needs 30 dB of dynamic range. Truncate at noise +5 dB and fit T20 from −5 to −25 dB. Reject nonlinearity over 40 ‰ or RT60 outside 0.1–6 s. A clap counts only when 500 Hz, 1 kHz, and 2 kHz are valid; borrowed neighbouring bands are marked estimated.
- Keep the latest accepted decay curves for V3 and V10. Return a typed rejection for invalid data rather than NaN, `-1`, or a plausible looking fallback.

Verify known tones, synthetic decays, short/long and noisy claps, and cancellation. `$clearroom-testing` carries the independent reference and tolerances.
