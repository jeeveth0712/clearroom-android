package com.clearroom.app.audio

import com.clearroom.app.dsp.peakDbFs
import com.clearroom.app.dsp.rmsDbFs

/** What the Measure tab shows after a recording; holds numbers only, never the samples. */
data class RecordingSummary(
    val durationS: Double,
    val sampleRate: Int,
    val source: AudioSourceKind,
    val peakDbFs: Double,
    val averageDbFs: Double,
    val quality: ClipQuality,
) {
    companion object {
        fun of(capture: Capture): RecordingSummary = RecordingSummary(
            durationS = capture.clip.durationS,
            sampleRate = capture.info.sampleRate,
            source = capture.info.source,
            peakDbFs = peakDbFs(capture.clip.samples),
            averageDbFs = rmsDbFs(capture.clip.samples),
            quality = ClipQuality.of(capture.clip.samples),
        )
    }
}
