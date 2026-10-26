package com.clearroom.app.audio

import android.Manifest
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.clearroom.app.dsp.SILENCE_DB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Runs against the phone's real microphone. Checks structure and lifecycle, not exact levels. */
@RunWith(AndroidJUnit4::class)
class AndroidRecorderTest {

    @get:Rule
    val microphone: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val recorder = AndroidRecorder(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun record_returnsExactlyTheRequestedLength_atTheReportedRate() = runBlocking {
        val capture = recorder.record(1.0)
        val rate = capture.info.sampleRate
        assertTrue("unexpected sample rate $rate", rate == 48_000 || rate == 44_100)
        assertEquals(rate, capture.clip.sampleRate)
        assertEquals(rate, capture.clip.samples.size)
    }

    @Test
    fun record_deliversFiniteSamples() = runBlocking {
        val samples = recorder.record(0.5).clip.samples
        assertTrue("non-finite sample found", samples.all { it.isFinite() })
    }

    @Test
    fun record_reportsHowTheMicrophoneWasSetUp() = runBlocking {
        val info = recorder.record(0.2).info
        // Logged (settings only, never samples) so the device's capability is visible in logcat.
        Log.i("ClearRoomCapture", "source=${info.source} rate=${info.sampleRate} format=${info.format} disabled=${info.effectsDisabled}")
        assertTrue(info.source in AudioSourceKind.entries)
        assertTrue(info.format in SampleFormat.entries)
    }

    @Test
    fun record_twiceInARow_works_soTheMicrophoneWasReleased() = runBlocking {
        recorder.record(0.3)
        val second = recorder.record(0.3)
        assertEquals((0.3 * second.info.sampleRate).roundToInt(), second.clip.samples.size)
    }

    @Test
    fun cancelledRecording_stopsQuickly_andReleasesTheMicrophone() = runBlocking {
        val long = launch(Dispatchers.Default) { recorder.record(5.0) }
        delay(300)
        val cancelStarted = System.nanoTime()
        long.cancelAndJoin()
        val cancelMs = (System.nanoTime() - cancelStarted) / 1_000_000
        assertTrue("cancel took $cancelMs ms", cancelMs < 1_000)

        val next = withTimeout(5_000) { recorder.record(0.5) }
        assertEquals((0.5 * next.info.sampleRate).roundToInt(), next.clip.samples.size)
    }

    @Test
    fun liveLevel_followsTheSound_andResetsAfterwards() = runBlocking {
        val levels = mutableListOf<Double>()
        val watcher = launch(Dispatchers.Default) { recorder.liveLevelDb.collect { levels += it } }
        val capture = recorder.record(1.0)
        delay(100)
        watcher.cancelAndJoin()

        // Android hands other apps pure silence while a call or assistant holds the microphone.
        // Then a flat -120 dB meter is correct, so skip rather than report a false failure.
        assumeTrue(
            "microphone delivered silence (is a call or another app using it?)",
            ClipQuality.of(capture.clip.samples) != ClipQuality.SILENT,
        )
        assertTrue("sound was captured but the level never moved: $levels", levels.any { it > SILENCE_DB })
        assertEquals(SILENCE_DB, recorder.liveLevelDb.value, 0.0)
    }

    @Test
    fun blocks_streamWholeSecondsBackToBack_fromOneOpenMicrophone() = runBlocking {
        val blocks = withTimeout(5_000) { recorder.blocks(1.0).take(2).toList() }
        assertEquals(2, blocks.size)
        blocks.forEach { block ->
            assertEquals(block.sampleRate, block.samples.size)
            assertTrue("non-finite sample found", block.samples.all { it.isFinite() })
        }
    }

    @Test
    fun blocks_releaseTheMicrophone_whenTheCollectorStops() = runBlocking {
        withTimeout(5_000) { recorder.blocks(0.5).take(1).toList() }
        val after = recorder.record(0.3) // would fail if the stream still held the microphone
        assertEquals((0.3 * after.info.sampleRate).roundToInt(), after.clip.samples.size)
    }
}
