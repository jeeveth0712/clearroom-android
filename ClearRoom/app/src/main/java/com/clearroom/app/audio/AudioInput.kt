package com.clearroom.app.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mono samples in [-1, 1] with the rate they were really captured at (filters depend on it).
 * Clips live in memory only and are never written to storage (NFR-6).
 */
class AudioClip(val samples: FloatArray, val sampleRate: Int) {
    init {
        require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
    }

    val durationS: Double get() = samples.size.toDouble() / sampleRate
}

/** Which Android microphone path was used. Both have no automatic gain control. */
enum class AudioSourceKind {
    /** Raw microphone signal: the best case for measuring. */
    UNPROCESSED,

    /** Fallback when UNPROCESSED isn't supported; Android requires it to have no AGC. */
    VOICE_RECOGNITION;

    companion object {
        /** Maps AudioManager's PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED value ("true" or null). */
        fun fromUnprocessedProperty(value: String?): AudioSourceKind =
            if (value.equals("true", ignoreCase = true)) UNPROCESSED else VOICE_RECOGNITION
    }
}

enum class SampleFormat { FLOAT_32, PCM_16 }

/** Platform audio effects ClearRoom found attached to the microphone and switched off. */
enum class DisabledEffect { AUTOMATIC_GAIN_CONTROL, NOISE_SUPPRESSOR, ECHO_CANCELLER }

/** How a clip was captured; goes into the report's measurement conditions (FR-12). */
data class CaptureInfo(
    val source: AudioSourceKind,
    val sampleRate: Int,
    val format: SampleFormat,
    val effectsDisabled: List<DisabledEffect>,
)

class Capture(val clip: AudioClip, val info: CaptureInfo)

/** [IN_USE_BY_DAY_LOG]: the day log holds the microphone; one thing records at a time. */
enum class CaptureProblem { MIC_UNAVAILABLE, PERMISSION_REVOKED, READ_FAILED, IN_USE_BY_DAY_LOG }

/** The only exception [AudioInput.record] throws, apart from cancellation. */
class CaptureException(val problem: CaptureProblem, message: String) : Exception(message)

/** Smoothed microphone level for the live meter, in dBFS (about 20 updates a second while recording). */
interface LiveLevelSource {
    val liveLevelDb: StateFlow<Double>
}

interface AudioInput : LiveLevelSource {
    /**
     * Records exactly `round(seconds × sampleRate)` samples at the rate reported in the result.
     * One recording at a time; cancelling stops and releases the microphone promptly.
     *
     * @throws CaptureException when the microphone can't be opened, read, or used without permission.
     */
    suspend fun record(seconds: Double): Capture

    /**
     * An endless stream of back-to-back [blockSeconds] clips from one open microphone, with no gap
     * between blocks (the day log, FR-9). Collecting opens the microphone; cancelling the collector
     * releases it. Each block is a fresh array the collector may keep.
     *
     * @throws CaptureException as for [record].
     */
    fun blocks(blockSeconds: Double): Flow<AudioClip>
}
