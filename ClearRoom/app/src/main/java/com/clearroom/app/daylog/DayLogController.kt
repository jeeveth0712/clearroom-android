package com.clearroom.app.daylog

import com.clearroom.app.audio.AudioInput
import com.clearroom.app.audio.CaptureException
import com.clearroom.app.audio.CaptureProblem
import com.clearroom.app.data.BandLevels
import com.clearroom.app.dsp.StreamingOctaveBank
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** What the Log tab shows: the room's current or latest log, whether it's running, and what went wrong. */
data class DayLogState(
    val roomId: String? = null,
    val running: Boolean = false,
    val startMillis: Long? = null,
    val seconds: List<LogSecond> = emptyList(),
    /** False when the sound-label model couldn't load: the log then keeps levels only (doc fallback). */
    val labelsAvailable: Boolean = true,
    val problem: CaptureProblem? = null,
    /** The log couldn't be written to storage. */
    val saveFailed: Boolean = false,
)

/**
 * The day log (FR-9): one-second blocks from the microphone → band levels with filters that keep
 * running → a label → one [LogSecond] appended to storage and to [state]. Pure Kotlin; the
 * ViewModel owns it and supplies the coroutine scope, so stopping the screen's ViewModel stops it.
 */
class DayLogController(
    private val scope: CoroutineScope,
    private val audio: AudioInput,
    private val store: LogStore,
    private val newLabeller: () -> SoundLabeller?,
    private val dsp: CoroutineDispatcher,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long,
) {
    private val _state = MutableStateFlow(DayLogState())
    val state: StateFlow<DayLogState> = _state.asStateFlow()
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** Shows [roomId]'s latest saved log (or nothing), unless a log is running. */
    fun show(roomId: String?) {
        if (isRunning) return
        if (roomId == null) {
            _state.value = DayLogState()
            return
        }
        _state.value = DayLogState(roomId = roomId)
        scope.launch {
            val latest = withContext(io) { store.latest(roomId) }
            _state.update { if (it.roomId == roomId && !it.running) it.copy(startMillis = latest?.startMillis, seconds = latest?.seconds.orEmpty()) else it }
        }
    }

    fun start(roomId: String) {
        if (isRunning) return
        val startMillis = clock()
        _state.value = DayLogState(roomId = roomId, running = true, startMillis = startMillis)
        job = scope.launch { run(roomId, startMillis) }
    }

    fun stop() {
        job?.cancel()
    }

    /** Deletes every saved log of [roomId] and shows it empty. Refused while a log runs: stop it first. */
    fun clear(roomId: String) {
        if (isRunning) return
        scope.launch {
            val deleted = try {
                withContext(io) { store.deleteAll(roomId) }
                true
            } catch (e: IOException) {
                false
            }
            _state.value = if (deleted) DayLogState(roomId = roomId) else _state.value.copy(saveFailed = true)
        }
    }

    private suspend fun run(roomId: String, startMillis: Long) {
        val writer = try {
            withContext(io) { store.open(roomId, startMillis) }
        } catch (e: IOException) {
            _state.update { it.copy(running = false, saveFailed = true) }
            return
        }
        val labeller = withContext(dsp) { newLabeller() }
        _state.update { it.copy(labelsAvailable = labeller != null) }
        var bank: StreamingOctaveBank? = null
        try {
            audio.blocks(BLOCK_S).collect { clip ->
                val (bands, label) = withContext(dsp) {
                    val filters = bank?.takeIf { it.sampleRate == clip.sampleRate } ?: StreamingOctaveBank(clip.sampleRate).also { bank = it }
                    filters.bandLevelsDb(clip.samples) to labeller?.label(clip)
                }
                val second = LogSecond(clock(), BandLevels(bands.toList()), label)
                _state.update { it.copy(seconds = it.seconds + second) }
                try {
                    withContext(io) { writer.append(second) }
                } catch (e: IOException) {
                    _state.update { it.copy(saveFailed = true) }
                }
            }
        } catch (e: CaptureException) {
            _state.update { it.copy(problem = e.problem) }
        } finally {
            // Release in every case, Stop (cancellation) included: native model memory and the file.
            // Everything happens inside NonCancellable: after Stop, returning from it into the
            // cancelled log throws again, so a line after this block would never run.
            withContext(NonCancellable + io) {
                labeller?.close()
                try {
                    writer.close()
                } catch (e: IOException) {
                    _state.update { it.copy(saveFailed = true) }
                }
                _state.update { it.copy(running = false) }
            }
        }
    }

    private companion object {
        const val BLOCK_S = 1.0
    }
}
