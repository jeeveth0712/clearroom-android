package com.clearroom.app.measure

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

/** What a level recording is for, and how long it lasts. */
enum class MeasureKind(val durationS: Double) {
    /** FR-4 says 30 s; the parameters table says 10 s. 30 s chosen (plan, Part E) for a steadier reading. */
    NOISE(30.0),

    /** FR-5 and "Exact parameters": the speaker reads for 20 s at normal voice, phone at 1 m. */
    VOICE(20.0),
}

sealed interface MeasureOutcome {
    data class Measured(val levels: BandLevels, val summary: RecordingSummary) : MeasureOutcome

    /** The clip itself was unusable (clipped or silent); nothing should be saved. */
    data class Rejected(val quality: ClipQuality) : MeasureOutcome

    data class Failed(val problem: CaptureProblem) : MeasureOutcome
}

/**
 * One band-level measurement: countdown, recording, quality check, then the seven octave-band
 * levels. The samples exist only inside [run] and are dropped when it returns (NFR-6).
 */
class LevelMeasurement(
    private val audio: AudioInput,
    private val dsp: CoroutineDispatcher,
) {
    suspend fun run(
        kind: MeasureKind,
        onCountdown: (secondsLeft: Int) -> Unit,
        onRecording: () -> Unit,
    ): MeasureOutcome {
        for (secondsLeft in COUNTDOWN_S downTo 1) {
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
                val levels = OctaveBank.bandLevelsDb(capture.clip.samples, capture.clip.sampleRate)
                MeasureOutcome.Measured(BandLevels(levels.toList()), summary)
            }
        }
    }

    companion object {
        /** Doc "Exact parameters": a 3-s countdown before each recording. */
        const val COUNTDOWN_S = 3
    }
}
