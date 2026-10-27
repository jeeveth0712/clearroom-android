---
name: sti-room-model
description: Maintain ClearRoom's STI calculation, Sabine room fit, distance propagation, limiting cause, fix predictions, required voice, uncertainty, or clarity grid. Use when a speech-clarity result or proposed fix changes.
---

# STI and room model

The engine lives in `dsp/Sti.kt` and `acoustics/`. Read `AGENTS.md` section 6 and the requirements document's Implementation notes before changing numeric behaviour. Both contain decisions made after the original Claude guide was written.

- Preserve IEC 60268-16's 14 modulation frequencies, seven band weights, and the direct-sound term. Check changes against the independent `../clearroom-testing/scripts/sti_reference.py` reference and golden cases.
- Fit Sabine absorption from the room-mean RT60 pooled across claps and seats. Speech propagation uses Q = 2; critical distance uses `0.057 * sqrt(Q * V / T60)`. Interpolate noise from measured seats in energy space.
- Verdict boundaries are poor below 0.45, fair from 0.45, good from 0.60, and excellent from 0.75. A row is judged by its worst seat. Show a range from echo-time uncertainty and label an unmeasured voice fallback as uncalibrated.
- The current limiting-cause rule picks the largest single-change gain among removing noise, removing echo, and moving the talker to 1 m; ties favour echo then noise. This remains an owner decision, so do not silently switch to a distance-first rule.
- Fix presets are defined in `acoustics/Fixes.kt`; costs are free/low/medium until the owner supplies rupee prices. Do not invent material absorption tables. Required voice returns `Unreachable` where echo caps STI below 0.60.

Test boundary, monotonicity, finite-result, sample-room, and before/after cases when changing the model. Never adjust a tolerance solely to make a test pass.
