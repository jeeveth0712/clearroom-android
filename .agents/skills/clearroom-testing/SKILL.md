---
name: clearroom-testing
description: Verify ClearRoom acoustic calculations, synthetic-clap and STI goldens, the in-app self-test, and real-room field checks. Use for numeric accuracy questions, self-test changes, and field-test preparation.
---

# ClearRoom testing and reference values

The app's 30-case self-test and JVM `SelfTestTest` share the same main-source cases in `selftest/`. The independent reference implementation is [scripts/sti_reference.py](scripts/sti_reference.py); run it for new STI expectations rather than copying the Kotlin output into tests.

- Key noise-only checks: T60 = 0 with SNR = 0 dB gives STI 0.500; T60 = 0 with SNR = +6 dB gives 0.700. Check the direct-sound cases with `--direct`, matching the app's direct/reverberant term.
- For RT60, use `selftest/SyntheticSignals.kt` and seeded test signals. Check accepted values against ±10% or ±0.1 s, plus clipped, silent, low-range, poor-fit, and out-of-range refusals. Preserve the NFR-9 timing check.
- Test monotonicity, finite STI in [0, 1], sample front/back order, the 0.60 target, and `Unreachable` when echo caps required voice. If a golden value fails, inspect the first mismatching stage rather than widening the tolerance.
- The field protocol is `docs/ClearRoom-Field-Tests.md`: 3–4 real rooms, repeatability, voice by distance, curtains, fan effect, listening scores, and usability. The owner performs this fieldwork; do not simulate or script measurements into their saved rooms.
- Keep exact input conditions with results: room, phone, audio source, fan state, occupancy, and measured distances. Do not mark M13 complete before the owner's field table has pass/fail outcomes.

From the repository root, run `python -I .agents/skills/clearroom-testing/scripts/sti_reference.py --direct` to inspect reference cases. Use `$test-every-function` for change-level test coverage.
