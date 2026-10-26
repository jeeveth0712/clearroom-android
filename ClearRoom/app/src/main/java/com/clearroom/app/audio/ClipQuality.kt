package com.clearroom.app.audio

import com.clearroom.app.dsp.rmsDbFs
import kotlin.math.abs

/** First check on every recording, before any analysis (FR-2 rejects clipped takes). */
enum class ClipQuality {
    OK,

    /** The microphone overloaded: samples stuck at full scale. */
    CLIPPED,

    /** Nothing came through: the mic is muted or another app holds it (Android 10+ hands us silence). */
    SILENT;

    companion object {
        /** |sample| at or above this counts as full scale. */
        const val CLIP_LEVEL = 0.99f

        /** This many full-scale samples in a row means clipping, not a single loud peak. */
        const val CLIP_RUN = 3

        /** A whole clip quieter than this is treated as no signal; real rooms sit far above it. */
        const val SILENT_BELOW_DB = -90.0

        /** Clipping is reported first: a clipped clip is never "silent". */
        fun of(samples: FloatArray): ClipQuality = when {
            hasClippedRun(samples) -> CLIPPED
            rmsDbFs(samples) < SILENT_BELOW_DB -> SILENT
            else -> OK
        }

        private fun hasClippedRun(samples: FloatArray): Boolean {
            var run = 0
            for (x in samples) {
                run = if (abs(x) >= CLIP_LEVEL) run + 1 else 0
                if (run >= CLIP_RUN) return true
            }
            return false
        }
    }
}
