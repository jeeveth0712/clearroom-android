---
name: noise-day-log
description: Change ClearRoom's continuous noise log, one-second octave levels, YAMNet labels, log storage/statistics, or Log tab views. Use for daylog/, YamnetLabeller, V5/V11, and long microphone sessions.
---

# Day noise log

The implemented flow is `AudioInput.blocks()` → `StreamingOctaveBank` and `YamnetLabeller` → `LogSecond` → `DayLogController`/`CsvLogStore` → summaries and V5/V11. See `AGENTS.md` sections 5–7 for exact files and decisions.

- Keep the octave bank's filter state across consecutive one-second blocks. Use energy averages for Leq and the tenth percentile for L90. Periods are 1 minute for logs up to 15 minutes, 5 minutes up to 2 hours, and 1 hour beyond.
- YAMNet is bundled at `app/src/main/assets/yamnet.tflite` and runs offline. Map its high-level classes to fan, traffic, horn, talking, music, quiet, or other at score ≥ 0.3. If the model cannot load, continue with levels only. Release native classifier resources on stop.
- Merge same-label events across gaps up to 10 s. Neither `other` nor `quiet` produces an event. Derive each period's back-row STI using the measured log-seat noise change.
- Store one CSV row per second under `filesDir/logs/<roomId>/<start>.csv`, flushing every 60 s. Reject unsafe room IDs before using them as folder names; ignore an incomplete last line on read. Never store raw audio.
- A log and a measurement cannot hold the microphone together. The current app keeps the screen on while logging; screen-off logging is not implemented and would require a microphone foreground service and notification.

Test summary math, event merging, cancellation, storage recovery, and the no-audio privacy invariant. Check the real device for label availability and microphone release when relevant.
