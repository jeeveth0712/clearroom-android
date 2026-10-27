---
name: android-audio-capture
description: Work on ClearRoom microphone capture, AudioRecord, recording quality checks, countdowns, live level meter, or audio privacy. Use when samples are recorded, streamed, analysed, or released.
---

# Android audio capture

The source of truth is `ClearRoom/app/src/main/java/com/clearroom/app/audio/` and `AGENTS.md` sections 4–7. The Samsung S24 Ultra has no `UNPROCESSED` source; the app uses `VOICE_RECOGNITION` at 48 kHz float and disables attached noise suppression and echo cancellation. Do not assume effects or a capture rate without querying the device.

- Keep one shared microphone lifecycle in `Recorder`: open, disable processing, read, then release the `AudioRecord` and effects in `finally`. Measurement and day log must not capture simultaneously.
- A clap records 5 s after a 3 s countdown (optional 10 s). Noise records 30 s. The voice reference records 20 s at 1 m. Preserve the actual input source and sample rate with each reading.
- Reject a clip with at least three samples at `abs(x) >= 0.99`, or a silent clip below −90 dBFS. A phone call may hand the app silence; report that as a recording problem rather than saving a false measurement.
- Publish the live level separately from the main UI state, so meter updates do not recompose the whole screen. For the day log, stream consecutive one-second blocks from one open microphone.
- Never persist raw samples, buffers, speech, or audio files; store only derived band levels and coarse sound labels. Do not log sample contents.

Test quality and cancellation paths with fake audio, then run relevant on-phone capture tests with the phone unlocked and no active call. See `$clearroom-testing` for synthetic signals and `$noise-day-log` for streaming state.
