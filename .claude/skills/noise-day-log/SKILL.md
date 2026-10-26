---
name: noise-day-log
description: How to build ClearRoom's day-long noise log (FR-9, FR-10). Covers streaming 1-s band levels with continuous filter state, on-device sound labels with MediaPipe Tasks Audio and YAMNet, mapping AudioSet classes to fan/traffic/horn/chatter/music, hourly Leq and L90, event merging, crash-safe incremental storage, keeping the screen on vs a microphone foreground service, battery, and the "levels, not audio" privacy rules. Use it for audio/NoiseLog.kt, the Log tab, YAMNet/MediaPipe, V5 and V11 data, or any long-running microphone work, even if the user only says "log the noise all day" or "classify sounds".
---

# Day noise log

The day log turns 1–8 hours of microphone input into one row per second: time, 7 band levels and an optional label. No audio is stored (NFR-6). It feeds V5 (clarity timeline), V11 (noise landscape) and the hourly back-row STI (FR-10).

## Pipeline

```
AudioInput.blocks(1.0) ──► StreamingOctaveBank ──► LogSecond(time, bandsDb[7], label?)
                     └───► SoundLabeler (YAMNet) ─┘           │
                                                             ├─► LogWriter (append, crash-safe)
                                                             └─► LiveStats (hourly Leq, L90, events)
```

- **Streaming filters keep their state across blocks.** This is the opposite of clap analysis. Create one `OctaveBank` per log session and feed it block after block. Resetting per block adds a start-up transient every second, which shows up as a 1-Hz ripple in the low bands.
- Compute band Leq per 1-s block (see `acoustics-dsp`). Use `System.currentTimeMillis()` for the wall-clock timestamp and `java.time.ZonedDateTime` with the device zone for hour buckets. Measure durations with `SystemClock.elapsedRealtime()`, because the wall clock can jump.
- Run filtering on `Dispatchers.Default`. Collect the block flow in a coroutine owned by the log controller and cancel it on Stop.

## Sound labels with MediaPipe + YAMNet

Dependency: `com.google.mediapipe:tasks-audio` (latest stable, through the version catalog). Model: `yamnet.tflite` from the MediaPipe Audio Classifier model page, placed in `app/src/main/assets/`. Record the model file's SHA-256 in `docs/` and check it in a unit test, so a swapped model is noticed (the audit skill checks this too).

```kotlin
val classifier = AudioClassifier.createFromOptions(
    context,
    AudioClassifier.AudioClassifierOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("yamnet.tflite").build())
        .setRunningMode(RunningMode.AUDIO_CLIPS)
        .setMaxResults(3)
        .setScoreThreshold(0.3f)
        .build(),
)
fun label(clip: AudioClip): String? {
    val data = AudioData.create(
        AudioData.AudioDataFormat.builder().setNumOfChannels(1).setSampleRate(clip.sampleRate.toFloat()).build(),
        clip.samples.size,
    )
    data.load(clip.samples)
    val top = classifier.classify(data).classificationResults().firstOrNull()
        ?.classifications()?.firstOrNull()?.categories()?.firstOrNull() ?: return null
    return LabelMap.toAppLabel(top.categoryName())
}
```

- YAMNet works on 16 kHz input with about 0.975-s windows. Pass the real capture rate in `AudioDataFormat` and let MediaPipe resample. Check this API against the current MediaPipe docs before coding (use the Context7 MCP if configured), because the Tasks API changes between releases.
- Close the classifier when the log stops (`classifier.close()`). It holds native memory.
- If the model fails to load, log without labels. The doc's fallback order drops YAMNet first.

Map YAMNet (AudioSet) class names to the app's few labels in one table. Check the exact strings against the model's label list rather than trusting this table:

| App label | YAMNet classes (examples) |
| --- | --- |
| fan | "Mechanical fan", "Air conditioning" |
| traffic | "Traffic noise, roadway noise", "Vehicle", "Car", "Motorcycle", "Bus" |
| horn | "Vehicle horn, car horn, honking", "Air horn, truck horn" |
| chatter | "Speech", "Conversation", "Chatter", "Hubbub, speech noise, speech babble", "Children shouting" |
| music | "Music" and its subclasses |
| quiet | "Silence" |
| other | anything else above the threshold |

Store only the app label, never the raw class list, scores or any speech-derived text.

## Statistics

- **Hourly Leq per band and in dBA:** energy mean, `10·log10(mean(10^(L/10)))`. Never average dB values directly: an arithmetic dB mean understates loud events.
- **L90:** the level exceeded 90 % of the time, which is the 10th percentile of the 1-s dBA values in the hour. It's the steady background (fans) without the events. Show it next to Leq in V5's tooltip.
- **Events:** merge consecutive seconds with the same non-quiet label into one `NoiseEvent(start, end, label, maxDb)`. Bridge gaps of 2 s or less, so a horn doesn't become five events.
- **FR-10:** for each hour, recompute back-row STI from the hourly band Leq (see `sti-room-model`) and flag hours below 0.60.

## Storage that survives a crash

8 hours is 28,800 rows. Rewriting a single room JSON every minute (the doc's one-file-per-room store) means rewriting about 2 MB each time, and a crash mid-write loses the whole log. Instead, append rows to a separate log file per session (`files/logs/<roomId>/<startMillis>.jsonl`, one JSON array of numbers per line). Flush every 60 s, and have the room JSON point at the file. Tell the user this deviates slightly from "one JSON file per room". On load, ignore a truncated last line.

## Keeping it running

- **Doc plan (Day 2):** the Log tab keeps the screen on while logging (`LocalView.current.keepScreenOn = true` inside a `DisposableEffect`, cleared on dispose). The phone stays on the charger. This is the simplest setup, and the user sees the "Recording levels, not audio" banner the whole time (risk table).
- **NFR-9 says "screen off".** That needs a foreground service with `android:foregroundServiceType="microphone"`, the `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE` permissions (the latter on Android 14+), and a visible notification (`POST_NOTIFICATIONS` on 13+). The service must be started while the app is in the foreground, because Android blocks starting microphone capture from the background. Raise this conflict with the user before building it, since it's a real chunk of extra work.
- Battery (NFR-9 ≤ 5 %/h): the DSP per second is tiny. Most of the cost is the screen and YAMNet. If battery is over budget, classify every other second, or only seconds more than 6 dB above the running L90.

## Privacy checklist

- Samples exist only in memory, for one block at a time.
- Nothing in `LogSecond` can rebuild speech: 7 levels per second plus a coarse label.
- The banner "Recording levels, not audio" is visible whenever the log runs.
- Stop releases the microphone (Recorder `finally`), closes the classifier and flushes the writer.
