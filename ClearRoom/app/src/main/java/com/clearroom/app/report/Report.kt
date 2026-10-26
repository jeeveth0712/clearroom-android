package com.clearroom.app.report

import com.clearroom.app.acoustics.ClarityGrid
import com.clearroom.app.acoustics.ClarityModel
import com.clearroom.app.acoustics.ClarityResult
import com.clearroom.app.acoustics.Fixes
import com.clearroom.app.acoustics.RankedFix
import com.clearroom.app.acoustics.RoomClarity
import com.clearroom.app.data.FanState
import com.clearroom.app.data.MicSetup
import com.clearroom.app.data.SampleData
import com.clearroom.app.data.Session
import com.clearroom.app.viz.Rt60ChartModel
import java.io.File

/** One set of results the report draws: the seat clarity map (V1) and the echo times (V4). */
data class ReportSide(val clarity: RoomClarity, val grid: ClarityGrid?, val echo: Rt60ChartModel) {
    companion object {
        /** Null until [session] has enough readings for a result. Does the maths: call it off the main thread. */
        fun of(session: Session): ReportSide? {
            val clarity = (ClarityModel.evaluate(session) as? ClarityResult.Ready)?.room ?: return null
            val echo = Rt60ChartModel.of(session) ?: return null
            return ReportSide(clarity, ClarityModel.floorGrid(session), echo)
        }
    }
}

/** How the current readings were taken: the report's "measurement conditions" (FR-12). */
data class ReportConditions(
    val mic: MicSetup?,
    val measuredSeats: Int,
    val seatCount: Int,
    val claps: Int,
    val noiseReadings: Int,
    /** Fan states during the noise readings, each once, in [FanState] order. */
    val fans: List<FanState>,
    val studentsUsually: Int,
    val voiceMeasured: Boolean,
    /** Echo time at speech pitches from every accepted clap, s. */
    val speechEchoS: Double,
) {
    companion object {
        fun of(session: Session, now: ReportSide) = ReportConditions(
            mic = session.mic,
            measuredSeats = session.seats.count { it.hasReadings },
            seatCount = session.room.seatCount,
            claps = session.seats.sumOf { it.rtTakes.size },
            noiseReadings = session.seats.count { it.noise != null },
            fans = session.seats.filter { it.noise != null }.mapNotNull { it.fans }.distinct().sorted(),
            studentsUsually = session.room.students,
            voiceMeasured = now.clarity.voiceMeasured,
            speechEchoS = now.echo.speechRt60S,
        )
    }
}

/**
 * Everything the one-page report shows (FR-12), worked out from one session at one moment: the
 * results now (and before a change, FR-13), the best fixes, and how it was measured. Words and
 * layout are the writer's job.
 */
data class Report(
    val session: Session,
    val createdAtMillis: Long,
    val now: ReportSide,
    /** The results before a change while comparing (FR-13); null otherwise. */
    val before: ReportSide?,
    /** The best single changes for the back row, only those that noticeably help, best first. */
    val topFixes: List<RankedFix>,
    val conditions: ReportConditions,
) {
    val isSample: Boolean get() = session.room.id == SampleData.ROOM_ID

    /** One echo-time scale for both sides, so equal bars before and after look equal. */
    val echoAxisTopS: Double get() = maxOf(now.echo.axisTopS, before?.echo?.axisTopS ?: 0.0)

    companion object {
        const val TOP_FIXES = 3

        /** Smaller gains print as "+0.00" and change nothing a listener would notice: not worth recommending. */
        const val MIN_GAIN = 0.01

        /** Null until the room has a result. Does the maths: call it off the main thread. */
        fun of(session: Session, createdAtMillis: Long): Report? {
            val now = ReportSide.of(session) ?: return null
            return Report(
                session = session,
                createdAtMillis = createdAtMillis,
                now = now,
                before = session.before?.let(ReportSide::of),
                // A report recommends: changes that would make things worse stay on the Fixes tab.
                topFixes = Fixes.rank(session).filter { it.gain >= MIN_GAIN }.take(TOP_FIXES),
                conditions = ReportConditions.of(session, now),
            )
        }
    }
}

/** Writes a report where the share sheet can reach it. The app writes a PDF; tests use a fake. */
interface ReportWriter {
    /**
     * Writes [report] and returns its file. Blocking: call it on the IO dispatcher.
     *
     * @throws java.io.IOException when the file can't be written.
     */
    fun write(report: Report): File
}
