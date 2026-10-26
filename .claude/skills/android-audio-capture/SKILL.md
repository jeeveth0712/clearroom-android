---
name: android-audio-capture
description: How to capture clean, unprocessed microphone audio on Android for acoustic measurement in ClearRoom. Covers AudioRecord at 48 kHz mono float, choosing the UNPROCESSED or VOICE_RECOGNITION source, turning off AGC, noise suppression and echo cancelling, fixed-length recordings for clap, noise and voice, 1-second streaming blocks, the live level meter, clipping and busy-microphone detection, and keeping audio out of storage. Use it whenever touching Recorder.kt, AudioRecord, microphone permissions, the level meter, the clap countdown, or any code that reads samples, even when the request just says "record", "mic" or "capture".
---

# Android audio capture for measurement

A measurement app needs the opposite of what a voice app needs. Automatic gain control (AGC) flattens the clap decay and noise suppression removes the fan noise you're trying to measure. Either one makes RT60 and SNR wrong without any visible error. Most of this skill is about getting raw samples and proving you got them.

## Interface first

Hide `AudioRecord` behind a small interface. The engine, the self-test and the sample room can then run on a fake input that produces synthetic signals, without a microphone.

```kotlin
/** Mono PCM in [-1, 1] at the given rate; samples never leave memory. */
class AudioClip(val samples: FloatArray, val sampleRate: Int)

interface AudioInput {
    /** Records exactly [seconds] of audio. Suspends; cancel to stop. */
    suspend fun record(seconds: Double): AudioClip
    /** Endless stream of [blockSeconds]-long clips for the day log. */
    fun blocks(blockSeconds: Double = 1.0): Flow<AudioClip>
    /** Smoothed live level in dBFS for the meter (about 20 updates/s). */
    val liveLevelDb: StateFlow<Double>
    /** What was actually used, for the report's measurement conditions. */
    val captureInfo: CaptureInfo
}

data class CaptureInfo(val source: String, val sampleRate: Int, val effectsDisabled: List<String>)
```

Always carry `sampleRate` with the samples. Filter coefficients depend on it, and a device that falls back to 44.1 kHz will silently shift every octave band if the DSP assumes 48 kHz.

## Choosing the source

```kotlin
fun chooseSource(context: Context): Int {
    val audioManager = context.getSystemService(AudioManager::class.java)
    val unprocessed = audioManager
        .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED
           else MediaRecorder.AudioSource.VOICE_RECOGNITION
}
```

`getProperty` returns `null` or `"true"` as a string, so compare with `== "true"`, not `toBoolean()` on a nullable. `VOICE_RECOGNITION` is the fallback because Android's compatibility rules require it to have no AGC. The risk table says to "reject devices that don't support it". In practice, warn and note the source in the report instead of blocking, because most phones work fine with `VOICE_RECOGNITION`.

## Building the recorder

```kotlin
@RequiresPermission(Manifest.permission.RECORD_AUDIO)
private fun open(source: Int, rate: Int): AudioRecord? {
    val format = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
        .setSampleRate(rate)
        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
        .build()
    val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
    if (minBytes <= 0) return null                       // ERROR or ERROR_BAD_VALUE
    val bufferBytes = maxOf(minBytes, rate * Float.SIZE_BYTES / 5)   // >= 200 ms headroom
    val record = AudioRecord.Builder()
        .setAudioSource(source)
        .setAudioFormat(format)
        .setBufferSizeInBytes(bufferBytes)
        .build()
    return if (record.state == AudioRecord.STATE_INITIALIZED) record else { record.release(); null }
}
```

Fallback order: (48 kHz, float) → (44.1 kHz, float) → (48 kHz, PCM_16BIT) → (44.1 kHz, PCM_16BIT). With 16-bit, read a `ShortArray` and divide by `32768f`. Some devices claim float support but fail to initialize, which is why the state check is there.

Turn off any effect the platform attached, and keep references so you can release them:

```kotlin
private fun disableEffects(session: Int): List<AudioEffect> = buildList {
    if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(session)?.let { it.setEnabled(false); add(it) }
    if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.let { it.setEnabled(false); add(it) }
    if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.let { it.setEnabled(false); add(it) }
}
```

`AudioEffect.setEnabled()` returns a status code, so Kotlin can't treat it as an `enabled = false` property: call the method. Some devices throw when an effect is touched; catch `RuntimeException`, release that effect and carry on. ClearRoom's `audio/Recorder.kt` has the working version.

## The read loop

`AudioRecord.read(..., READ_BLOCKING)` blocks the thread. Run it on `Dispatchers.IO`, not `Default` (which is for DSP) and never on Main.

```kotlin
override suspend fun record(seconds: Double): AudioClip = withContext(Dispatchers.IO) {
    val record = openBest() ?: throw CaptureException.Unavailable
    val effects = disableEffects(record.audioSessionId)
    val total = (seconds * record.sampleRate).roundToInt()
    val out = FloatArray(total)
    try {
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw CaptureException.Busy
        var filled = 0
        while (filled < total) {
            ensureActive()                                  // cancellation stops the mic promptly
            val n = record.read(out, filled, minOf(CHUNK, total - filled), AudioRecord.READ_BLOCKING)
            if (n < 0) throw CaptureException.ReadFailed(n)  // ERROR_INVALID_OPERATION, ERROR_DEAD_OBJECT, ...
            updateLiveLevel(out, filled, n)
            filled += n
        }
        AudioClip(out, record.sampleRate)
    } finally {
        try { record.stop() } catch (e: IllegalStateException) { }   // it never started
        effects.forEach { it.release() }
        record.release()
    }
}
```

- Release in `finally`, always. A leaked `AudioRecord` keeps the microphone busy for every app until the process dies.
- Use a `CHUNK` of about 50 ms (2,400 samples at 48 kHz) so the level meter updates smoothly.
- `blocks()` is a `callbackFlow` or `flow { }` over the same loop that emits a new `AudioClip` per block. Allocate a new array per block, or copy, because the consumer may still hold the previous one.

## Live level meter

Compute RMS over each chunk: `10 * log10(meanSquare + 1e-12)` in dBFS. Smooth it (an exponential moving average with about 100 ms time constant) and publish it to `liveLevelDb`. Read it in the UI with `collectAsStateWithLifecycle`. Don't push every chunk through `AppViewModel` state, because 20 recompositions per second of the whole UI state is wasted work.

## Checks on every clip

Run these before any analysis and turn failures into the plain-language messages from FR-2:

| Check | Rule | Message to user |
| --- | --- | --- |
| Clipping | 3+ consecutive samples with \|x\| ≥ 0.99 | "Too loud: clap a bit softer or step back" |
| Busy or muted mic | RMS below -90 dBFS for the whole clip, or all zeros | "Microphone is in use by another app or muted" |
| Wrong rate | `record.sampleRate` differs from the request | Not an error: pass the real rate on and record it in `CaptureInfo` |

Android 10+ lets a higher-priority app (a call, the assistant) take the microphone. The other app then receives silence, not an error, which is why the all-zeros check matters.

## Recording lengths and guidance (from "Exact parameters")

- Clap: 5 s. Show a 3-s countdown first, then "Clap now" at about 1 s into the recording, so at least 0.3 s of pre-clap noise is captured for the noise floor.
- Noise snapshot: the parameters table says 10 s, but FR-4 says 30 s. Put the value in one constant (`NOISE_SNAPSHOT_SECONDS`) and ask the user which one they want. 30 s gives a steadier Leq in rooms with intermittent noise.
- Voice reference: 20 s at 1 m while the speaker reads the given paragraph.

## Privacy (NFR-6)

- Never write PCM to disk: no `FileOutputStream` of samples, no `MediaRecorder`, no WAV export, no debug dumps. Keep the voice clip in memory, compute its 7 band levels, then drop the reference. This is stricter than the doc (which allows storing the clip until analysis ends) and simpler to prove.
- Never log sample values or buffers with `Log.*`.
- The day log stores only band levels and labels (see the `noise-day-log` skill).

## Testing

- Write a `FakeAudioInput(signal: (sampleRate: Int) -> FloatArray)` that returns synthetic clips (decays, tones, silence, clipped signals). The `clearroom-testing` skill has the generators.
- Check on a real device that `captureInfo.source` reports `UNPROCESSED` or `VOICE_RECOGNITION`, and that a steady fan gives a steady level (no slow drift up or down). Drift is the sign of AGC still running.
