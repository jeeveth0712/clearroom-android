package com.clearroom.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.clearroom.app.dsp.LevelSmoother
import com.clearroom.app.dsp.SILENCE_DB
import com.clearroom.app.dsp.rmsDbFs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * [AudioInput] over Android's AudioRecord, set up for measurement rather than voice: the raw
 * (UNPROCESSED) source when the phone supports it, and AGC, noise suppression and echo
 * cancelling switched off, because each one silently corrupts decay and noise readings.
 */
class AndroidRecorder(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AudioInput {

    private val appContext = context.applicationContext
    private val _liveLevelDb = MutableStateFlow(SILENCE_DB)
    override val liveLevelDb: StateFlow<Double> = _liveLevelDb.asStateFlow()
    private val oneRecordingAtATime = Mutex()

    override suspend fun record(seconds: Double): Capture {
        require(seconds.isFinite() && seconds > 0.0) { "seconds must be > 0, was $seconds" }
        // AudioRecord.read blocks its thread, so it runs on IO, never on Main or the DSP pool.
        return oneRecordingAtATime.withLock { withContext(io) { capture(seconds) } }
    }

    override fun blocks(blockSeconds: Double): Flow<AudioClip> {
        require(blockSeconds.isFinite() && blockSeconds > 0.0) { "blockSeconds must be > 0, was $blockSeconds" }
        return flow {
            oneRecordingAtATime.withLock {
                withOpenMicrophone { opened, _ ->
                    startRecording(opened.record)
                    val smoother = LevelSmoother()
                    val blockSamples = (blockSeconds * opened.sampleRate).roundToInt()
                    while (true) {
                        val block = FloatArray(blockSamples) // fresh each time: the collector keeps it
                        fill(opened, block, smoother)
                        emit(AudioClip(block, opened.sampleRate))
                    }
                }
            }
        }.flowOn(io)
    }

    private suspend fun capture(seconds: Double): Capture = withOpenMicrophone { opened, info ->
        startRecording(opened.record)
        val samples = FloatArray((seconds * opened.sampleRate).roundToInt())
        fill(opened, samples, LevelSmoother())
        Capture(AudioClip(samples, opened.sampleRate), info)
    }

    /** Opens the best microphone setup, switches the voice effects off, and always releases both. */
    private suspend fun <T> withOpenMicrophone(use: suspend (OpenedRecord, CaptureInfo) -> T): T {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw CaptureException(CaptureProblem.PERMISSION_REVOKED, "RECORD_AUDIO is not granted")
        }
        val source = AudioSourceKind.fromUnprocessedProperty(
            appContext.getSystemService(AudioManager::class.java)
                ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED),
        )
        try {
            val opened = openFirstWorking(source)
                ?: throw CaptureException(CaptureProblem.MIC_UNAVAILABLE, "no sample format could be opened")
            val effects = disableEffects(opened.record.audioSessionId)
            try {
                return use(opened, CaptureInfo(source, opened.sampleRate, opened.format, effects.map { it.first }))
            } finally {
                stopQuietly(opened.record)
                effects.forEach { it.second.release() }
                opened.record.release() // a leaked AudioRecord blocks the mic for every app
                _liveLevelDb.value = SILENCE_DB
            }
        } catch (e: SecurityException) {
            throw CaptureException(CaptureProblem.PERMISSION_REVOKED, e.message ?: "permission revoked")
        }
    }

    private fun startRecording(record: AudioRecord) {
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw CaptureException(CaptureProblem.MIC_UNAVAILABLE, "microphone is busy")
        }
    }

    /** Fills [out] completely from the running recorder, updating the live level as it goes. */
    private suspend fun fill(opened: OpenedRecord, out: FloatArray, smoother: LevelSmoother) {
        val record = opened.record
        val total = out.size
        val chunk = opened.sampleRate / LEVEL_UPDATES_PER_S
        val pcm16Buffer = if (opened.format == SampleFormat.PCM_16) ShortArray(chunk) else null
        var filled = 0
        var emptyReads = 0
        while (filled < total) {
            currentCoroutineContext().ensureActive() // Cancel stops the mic within one chunk
            val wanted = minOf(chunk, total - filled)
            val read = if (pcm16Buffer == null) {
                record.read(out, filled, wanted, AudioRecord.READ_BLOCKING)
            } else {
                readPcm16(record, pcm16Buffer, out, filled, wanted)
            }
            when {
                read < 0 -> throw CaptureException(CaptureProblem.READ_FAILED, "AudioRecord.read returned $read")
                read == 0 && ++emptyReads > MAX_EMPTY_READS ->
                    throw CaptureException(CaptureProblem.READ_FAILED, "microphone delivered no data")
                read > 0 -> {
                    _liveLevelDb.value = smoother.next(rmsDbFs(out, filled, read), read.toDouble() / opened.sampleRate)
                    filled += read
                }
            }
        }
    }

    private fun readPcm16(record: AudioRecord, buffer: ShortArray, out: FloatArray, at: Int, wanted: Int): Int {
        val read = record.read(buffer, 0, wanted, AudioRecord.READ_BLOCKING)
        for (i in 0 until read) out[at + i] = buffer[i] / PCM16_FULL_SCALE
        return read
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openFirstWorking(source: AudioSourceKind): OpenedRecord? =
        CANDIDATE_FORMATS.firstNotNullOfOrNull { (rate, format) -> open(source, rate, format) }

    /** Null when this device can't record in [format] at [rate]; some claim float support but fail. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun open(source: AudioSourceKind, rate: Int, format: SampleFormat): OpenedRecord? {
        val encoding = when (format) {
            SampleFormat.FLOAT_32 -> AudioFormat.ENCODING_PCM_FLOAT
            SampleFormat.PCM_16 -> AudioFormat.ENCODING_PCM_16BIT
        }
        val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, encoding)
        if (minBytes <= 0) return null
        val bytesPerSample = if (format == SampleFormat.FLOAT_32) 4 else 2
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(source.platformSource())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBytes, rate * bytesPerSample / 5)) // >= 200 ms headroom
                .build()
        } catch (e: UnsupportedOperationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        return OpenedRecord(record, record.sampleRate, format)
    }

    private fun disableEffects(session: Int): List<Pair<DisabledEffect, AudioEffect>> = buildList {
        if (AutomaticGainControl.isAvailable()) {
            switchOff(AutomaticGainControl.create(session))?.let { add(DisabledEffect.AUTOMATIC_GAIN_CONTROL to it) }
        }
        if (NoiseSuppressor.isAvailable()) {
            switchOff(NoiseSuppressor.create(session))?.let { add(DisabledEffect.NOISE_SUPPRESSOR to it) }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            switchOff(AcousticEchoCanceler.create(session))?.let { add(DisabledEffect.ECHO_CANCELLER to it) }
        }
    }

    /** Some devices throw when an effect is touched; then it isn't attached, so there's nothing to undo. */
    private fun switchOff(effect: AudioEffect?): AudioEffect? = try {
        effect?.apply { setEnabled(false) } // setEnabled returns a status code, so it isn't a Kotlin property
    } catch (e: RuntimeException) {
        effect?.release()
        null
    }

    private fun stopQuietly(record: AudioRecord) {
        try {
            record.stop()
        } catch (e: IllegalStateException) {
            // Never started (for example the mic was busy): nothing to stop.
        }
    }

    private fun AudioSourceKind.platformSource(): Int = when (this) {
        AudioSourceKind.UNPROCESSED -> MediaRecorder.AudioSource.UNPROCESSED
        AudioSourceKind.VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private class OpenedRecord(val record: AudioRecord, val sampleRate: Int, val format: SampleFormat)

    private companion object {
        /** Tried in order: 48 kHz float is what the doc asks for (NFR-7); the rest are fallbacks. */
        val CANDIDATE_FORMATS = listOf(
            48_000 to SampleFormat.FLOAT_32,
            44_100 to SampleFormat.FLOAT_32,
            48_000 to SampleFormat.PCM_16,
            44_100 to SampleFormat.PCM_16,
        )
        const val LEVEL_UPDATES_PER_S = 20 // 50 ms chunks keep the level bar smooth
        const val MAX_EMPTY_READS = 100
        const val PCM16_FULL_SCALE = 32_768f
    }
}
