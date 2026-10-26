package com.clearroom.app.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearroom.app.daylog.SoundLabel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/** The bundled YAMNet model loads and runs on the real phone through MediaPipe (FR-9 sound labels). */
@RunWith(AndroidJUnit4::class)
class YamnetLabellerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theModelLoadsFromTheApp() {
        val labeller = YamnetLabeller.create(context)
        assertNotNull("YAMNet failed to load on this phone", labeller)
        labeller?.close()
    }

    @Test
    fun aSecondOfSilenceIsQuietOrUnlabelled() {
        val labeller = requireNotNull(YamnetLabeller.create(context))
        try {
            val label = labeller.label(AudioClip(FloatArray(RATE), RATE))
            assertTrue("silence was labelled $label", label == null || label == SoundLabel.QUIET)
        } finally {
            labeller.close()
        }
    }

    @Test
    fun aSecondOfSoundGetsALabelWithoutCrashing() {
        val labeller = requireNotNull(YamnetLabeller.create(context))
        try {
            val tone = FloatArray(RATE) { (0.3 * sin(2 * PI * 440.0 * it / RATE)).toFloat() }
            labeller.label(AudioClip(tone, RATE)) // any label, or none: the point is it runs at 48 kHz input
        } finally {
            labeller.close()
        }
    }

    private companion object {
        const val RATE = 48_000
    }
}
