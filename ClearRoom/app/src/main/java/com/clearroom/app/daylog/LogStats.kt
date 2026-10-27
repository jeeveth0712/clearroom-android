package com.clearroom.app.daylog

import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.NoiseField
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.VoiceFallback
import com.clearroom.app.data.BandLevels
import com.clearroom.app.data.SeatId
import com.clearroom.app.data.Session
import com.clearroom.app.dsp.OctaveBands
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/** A stretch of the log (an hour, or shorter for a short log): its energy-mean levels and background. */
data class LogPeriod(val startMillis: Long, val endMillis: Long, val bands: BandLevels, val leqDba: Double, val l90Dba: Double, val seconds: Int)

/** Back-to-back seconds with the same label, merged (FR-9 "events"). */
data class NoiseEvent(val startMillis: Long, val endMillis: Long, val label: SoundLabel, val maxDba: Double)

/** One point of V5's noise line: the energy-mean level over [stepMillis] starting at [timeMillis]. */
data class NoisePoint(val timeMillis: Long, val dba: Double)

/** What one event did to the back row (FR-10): its STI while the event lasted, and the change from the background (below 0 when worse). */
data class EventClarity(val stiDuring: Double, val change: Double)

/** The back row over a whole log, in plain words (concept "Mostly clear"). */
enum class DayVerdict { CLEAR_THROUGHOUT, MOSTLY_CLEAR, OFTEN_UNCLEAR, MOSTLY_UNCLEAR }

/**
 * Day-log statistics (noise-day-log skill). Levels are always averaged as energy, never as dB:
 * a plain dB mean hides loud events. Everything here is pure, so it's tested on the JVM.
 */
object LogStats {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS

    /**
     * Seconds of the same label at most this far apart still count as one event. The skill's 2 s
     * proved too short on the phone: YAMNet's score for a steady fan flickers below its threshold
     * for a few seconds at a time, which chopped one fan into a dozen events.
     */
    const val EVENT_GAP_MS = 10_000L

    /**
     * Phone levels are dBFS; adding the Android CDD sensitivity turns them into rough dB SPL, so
     * the log can say "about 48 dBA". Uncalibrated, and every screen says so (NFR-5).
     */
    const val PHONE_TO_SPL_DB = VoiceFallback.ASSUMED_SPL_MINUS_DBFS

    /** 10·log10 of the mean energy: the right way to average levels. */
    fun energyMeanDb(levelsDb: List<Double>): Double {
        require(levelsDb.isNotEmpty()) { "no levels to average" }
        return 10 * log10(levelsDb.sumOf { 10.0.pow(it / 10) } / levelsDb.size)
    }

    /**
     * L90: the level exceeded 90 % of the time, the 10th percentile (nearest rank) of the seconds'
     * levels. It's the steady background (fans) without the events.
     */
    fun l90Db(levelsDb: List<Double>): Double {
        require(levelsDb.isNotEmpty()) { "no levels" }
        val sorted = levelsDb.sorted()
        val rank = ceil(0.10 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /** Period length that keeps the charts readable: a minute for a test log, five for a lesson, an hour for a day. */
    fun periodMillisFor(spanMillis: Long): Long = when {
        spanMillis <= 15 * MINUTE_MS -> MINUTE_MS
        spanMillis <= 2 * HOUR_MS -> 5 * MINUTE_MS
        else -> HOUR_MS
    }

    /** V5's noise line step: 10 s for short logs, a minute otherwise. */
    fun noiseStepMillisFor(spanMillis: Long): Long = if (spanMillis <= 30 * MINUTE_MS) 10_000L else MINUTE_MS

    fun spanMillis(seconds: List<LogSecond>): Long =
        if (seconds.isEmpty()) 0 else seconds.last().timeMillis - seconds.first().timeMillis + 1_000L

    /** Periods of [periodMillis] from the first second; empty periods (the phone was stopped) are left out. */
    fun periods(seconds: List<LogSecond>, periodMillis: Long = periodMillisFor(spanMillis(seconds))): List<LogPeriod> {
        require(periodMillis > 0) { "period must be > 0" }
        if (seconds.isEmpty()) return emptyList()
        val start = seconds.first().timeMillis
        return seconds.groupBy { (it.timeMillis - start) / periodMillis }.toSortedMap().map { (index, inPeriod) ->
            val periodStart = start + index * periodMillis
            LogPeriod(
                startMillis = periodStart,
                endMillis = periodStart + periodMillis,
                bands = BandLevels(List(OctaveBands.COUNT) { band -> energyMeanDb(inPeriod.map { it.bands[band] }) }),
                leqDba = energyMeanDb(inPeriod.map { it.dba }),
                l90Dba = l90Db(inPeriod.map { it.dba }),
                seconds = inPeriod.size,
            )
        }
    }

    /** V5's noise line: energy-mean dBA per [stepMillis]. */
    fun noiseLine(seconds: List<LogSecond>, stepMillis: Long = noiseStepMillisFor(spanMillis(seconds))): List<NoisePoint> {
        if (seconds.isEmpty()) return emptyList()
        val start = seconds.first().timeMillis
        return seconds.groupBy { (it.timeMillis - start) / stepMillis }.toSortedMap().map { (index, inStep) ->
            NoisePoint(start + index * stepMillis, energyMeanDb(inStep.map { it.dba }))
        }
    }

    /**
     * Named sounds: the same label with gaps of at most [EVENT_GAP_MS] merged into one event. Quiet
     * and "other" (YAMNet's catch-all) never make an event: on the phone a fan's seconds alternate
     * with "other", which would chop one fan into dozens of events.
     */
    fun events(seconds: List<LogSecond>): List<NoiseEvent> {
        val events = ArrayList<NoiseEvent>()
        for (second in seconds) {
            val label = second.label?.takeIf { it != SoundLabel.QUIET && it != SoundLabel.OTHER } ?: continue
            val last = events.lastOrNull()
            if (last != null && last.label == label && second.timeMillis - last.endMillis <= EVENT_GAP_MS) {
                events[events.lastIndex] = last.copy(endMillis = second.timeMillis + 1_000L, maxDba = maxOf(last.maxDba, second.dba))
            } else {
                events += NoiseEvent(second.timeMillis, second.timeMillis + 1_000L, label, second.dba)
            }
        }
        return events
    }

    /**
     * FR-10: the back row's STI in each period. The log is recorded at one spot ([logSpot]), so each
     * period's band levels are compared with the noise snapshot there, and that change is applied
     * to every measured seat before the clarity engine runs again. Null for a period when the room
     * doesn't have enough measurements yet.
     */
    fun backRowSti(session: Session, logSpot: SeatId, periods: List<LogPeriod>): List<Double?> = backRowStiWith(session, logSpot, periods.map { it.bands })

    /**
     * FR-10 for each of [heard], band levels heard at [logSpot]: the back row's STI with the room's
     * noise shifted by the difference from the noise snapshot there. Null when the room isn't measured enough.
     */
    fun backRowStiWith(session: Session, logSpot: SeatId, heard: List<BandLevels>): List<Double?> {
        val readings = session.seats.mapNotNull { measure -> measure.noise?.let { SeatLayout.position(session.room, measure.seat) to it } }
        if (readings.isEmpty()) return heard.map { null }
        val snapshot = NoiseField.at(SeatLayout.position(session.room, logSpot), readings)
        return heard.map { bands ->
            val change = List(OctaveBands.COUNT) { band -> bands[band] - snapshot[band] }
            val shifted = session.copy(
                seats = session.seats.map { measure -> measure.copy(noise = measure.noise?.let { noise -> BandLevels(noise.valuesDb.mapIndexed { band, db -> db + change[band] }) }) },
            )
            (ClarityModel.evaluate(shifted) as? ClarityResult.Ready)?.room?.backRowSti
        }
    }

    /** Each band's background over the log: the level it stays above 90 % of the time (its L90). */
    fun backgroundBands(seconds: List<LogSecond>): BandLevels {
        require(seconds.isNotEmpty()) { "no seconds" }
        return BandLevels(List(OctaveBands.COUNT) { band -> l90Db(seconds.map { it.bands[band] }) })
    }

    /** Each band's energy mean over the seconds of [event] that carry its label (the gaps it bridged don't count). */
    fun eventBands(seconds: List<LogSecond>, event: NoiseEvent): BandLevels {
        val heard = seconds.filter { it.label == event.label && it.timeMillis >= event.startMillis && it.timeMillis < event.endMillis }
        require(heard.isNotEmpty()) { "no seconds of ${event.label} in the event" }
        return BandLevels(List(OctaveBands.COUNT) { band -> energyMeanDb(heard.map { it.bands[band] }) })
    }

    /**
     * What each of [events] did to the back row: its STI while the event lasted against the STI
     * with only the log's background. Null for every event when the room isn't measured enough.
     */
    fun eventClarity(session: Session, logSpot: SeatId, seconds: List<LogSecond>, events: List<NoiseEvent>): List<EventClarity?> {
        if (events.isEmpty()) return emptyList()
        val sti = backRowStiWith(session, logSpot, listOf(backgroundBands(seconds)) + events.map { eventBands(seconds, it) })
        val background = sti.first() ?: return events.map { null }
        return sti.drop(1).map { during -> during?.let { EventClarity(it, it - background) } }
    }

    /** Where the log is taken: the seat picked on the Measure tab, or the middle of the back row. */
    fun logSpot(session: Session, picked: SeatId?): SeatId =
        picked?.takeIf { session.contains(it) } ?: SeatId(session.room.rows - 1, session.room.cols / 2)
}

/**
 * Everything the Log tab draws from one log: the periods with their levels and the back row's
 * STI (V5, V11, FR-10), V5's noise line, the events, and the whole log's Leq and L90.
 */
data class LogSummary(
    val spanMillis: Long,
    val leqDba: Double,
    val l90Dba: Double,
    val periods: List<LogPeriod>,
    val backRowSti: List<Double?>,
    val noiseLine: List<NoisePoint>,
    val events: List<NoiseEvent>,
    /** What the latest [LATEST_EVENTS] events did to the back row; empty when the room isn't measured. */
    val eventClarity: Map<NoiseEvent, EventClarity> = emptyMap(),
) {
    /** FR-10: periods whose back-row STI is below the 0.60 target (periods without a score don't count). */
    val periodsBelowTarget: Int get() = backRowSti.count { it != null && it < ClarityModel.TARGET_STI }

    val periodsWithClarity: Int get() = backRowSti.count { it != null }

    /** When the back row hears best: the start of the period with the highest STI, or null if none has one. */
    val bestPeriodStartMillis: Long?
        get() = periods.indices.filter { backRowSti[it] != null }.maxByOrNull { requireNotNull(backRowSti[it]) }?.let { periods[it].startMillis }

    /** The loudest labelled event, for the summary line. */
    val loudestEvent: NoiseEvent? get() = events.maxByOrNull { it.maxDba }

    /** The back row's lowest and highest STI over the periods, or null when the room isn't measured. */
    val backRowRange: ClosedFloatingPointRange<Double>?
        get() = backRowSti.filterNotNull().takeIf { it.isNotEmpty() }?.let { it.min()..it.max() }

    /** How much of the log the back row could follow easily (0.60 or more), in words; null when the room isn't measured. */
    val dayVerdict: DayVerdict?
        get() {
            if (periodsWithClarity == 0) return null
            val easyShare = (periodsWithClarity - periodsBelowTarget).toDouble() / periodsWithClarity
            return when {
                easyShare >= 1.0 -> DayVerdict.CLEAR_THROUGHOUT
                easyShare >= MOSTLY_SHARE -> DayVerdict.MOSTLY_CLEAR
                easyShare >= OFTEN_SHARE -> DayVerdict.OFTEN_UNCLEAR
                else -> DayVerdict.MOSTLY_UNCLEAR
            }
        }

    companion object {
        /** The events list shows this many of the latest events, so only they get their clarity worked out. */
        const val LATEST_EVENTS = 12

        /** "Mostly clear" when at least three quarters of the log was easy to follow; "often hard" from a quarter. */
        private const val MOSTLY_SHARE = 0.75
        private const val OFTEN_SHARE = 0.25

        /** Null for an empty log. [session] (when the room is measured) adds the clarity over time. */
        fun of(seconds: List<LogSecond>, session: Session?, logSpot: SeatId?): LogSummary? {
            if (seconds.isEmpty()) return null
            val periods = LogStats.periods(seconds)
            val events = LogStats.events(seconds)
            val measured = session != null && logSpot != null
            val latest = events.takeLast(LATEST_EVENTS)
            val eventClarity = if (measured) LogStats.eventClarity(session, logSpot, seconds, latest) else emptyList()
            return LogSummary(
                spanMillis = LogStats.spanMillis(seconds),
                leqDba = LogStats.energyMeanDb(seconds.map { it.dba }),
                l90Dba = LogStats.l90Db(seconds.map { it.dba }),
                periods = periods,
                backRowSti = if (measured) LogStats.backRowSti(session, logSpot, periods) else periods.map { null },
                noiseLine = LogStats.noiseLine(seconds),
                events = events,
                eventClarity = latest.zip(eventClarity).mapNotNull { (event, clarity) -> clarity?.let { event to it } }.toMap(),
            )
        }
    }
}
