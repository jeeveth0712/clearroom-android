package com.clearroom.app.measure

import com.clearroom.app.audio.AudioClip
import com.clearroom.app.audio.AudioInput
import com.clearroom.app.audio.CaptureException
import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.audio.ClipQuality
import com.clearroom.app.audio.RecordingSummary
import com.clearroom.app.data.BandLevels
import com.clearroom.app.dsp.OctaveBank
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What a recording is for, and how long it lasts. */
enum class MeasureKind(val durationS: Double) {
    /** FR-4 says 30 s; the parameters table says 10 s. 30 s chosen (owner's decision) for a steadier reading. */
    NOISE(30.0),

    /** FR-5 and "Exact parameters": the speaker reads for 20 s at normal voice, phone at 1 m. */
    VOICE(20.0),

    /** "Exact parameters": a 5-s recording with the clap about 1 s in (FR-2). */
    CLAP(5.0),
}

sealed interface MeasureOutcome<out T> {
    data class Measured<T>(val result: T, val summary: RecordingSummary) : MeasureOutcome<T>

    /** The clip itself was unusable (clipped or silent); nothing should be analysed or saved. */
    data class Rejected(val quality: ClipQuality) : MeasureOutcome<Nothing>

    data class Failed(val problem: CaptureProblem) : MeasureOutcome<Nothing>
}

/**
 * Runs one measurement: countdown, recording, the clip-quality check, then [analyse] on the DSP
 * dispatcher. The samples exist only inside [run] and are dropped when it returns (NFR-6).
 */
class MeasurementRunner(
    private val audio: AudioInput,
    private val dsp: CoroutineDispatcher,
) {
    suspend fun <T> run(
        kind: MeasureKind,
        countdownS: Int = COUNTDOWN_S,
        onCountdown: (secondsLeft: Int) -> Unit,
        onRecording: () -> Unit,
        analyse: (AudioClip) -> T,
    ): MeasureOutcome<T> {
        require(countdownS >= 0) { "countdownS can't be negative: $countdownS" }
        for (secondsLeft in countdownS downTo 1) {
            onCountdown(secondsLeft)
            delay(1_000)
        }
        onRecording()
        val capture = try {
            audio.record(kind.durationS)
        } catch (e: CaptureException) {
            return MeasureOutcome.Failed(e.problem)
        }
        return withContext(dsp) {
            val summary = RecordingSummary.of(capture)
            if (summary.quality != ClipQuality.OK) {
                MeasureOutcome.Rejected(summary.quality)
            } else {
                MeasureOutcome.Measured(analyse(capture.clip), summary)
            }
        }
    }

    companion object {
        /** Doc "Exact parameters": a 3-s countdown before each recording. */
        const val COUNTDOWN_S = 3
    }
}

/** The analysis for noise and voice readings: seven octave-band levels (FR-4, FR-5). */
fun bandLevelsOf(clip: AudioClip): BandLevels =
    BandLevels(OctaveBank.bandLevelsDb(clip.samples, clip.sampleRate).toList())
