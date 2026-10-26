package com.clearroom.app.report

import android.content.res.Resources
import com.clearroom.app.R
import com.clearroom.app.acoustics.Cause
import com.clearroom.app.acoustics.RankedFix
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.audio.AudioSourceKind
import com.clearroom.app.ui.formatDateTime
import com.clearroom.app.ui.formatMetres
import com.clearroom.app.ui.screens.labelRes
import com.clearroom.app.ui.screens.nameRes
import com.clearroom.app.ui.screens.phraseRes
import com.clearroom.app.ui.screens.rangeText
import com.clearroom.app.ui.screens.rowVerdict
import com.clearroom.app.ui.screens.signedSti
import com.clearroom.app.ui.screens.textRes
import com.clearroom.app.ui.screens.whyRes
import com.clearroom.app.viz.decimals
import com.clearroom.app.viz.rt60Caption
import java.time.ZoneId

/**
 * The report's words, from strings.xml in the phone's language: plain words (NFR-2), every score
 * with its range (NFR-10). Lists hold one line each; the page decides where they go.
 */
class ReportText(
    val title: String,
    val roomLine: String,
    val sizeLine: String,
    val sampleNote: String?,
    val headline: String,
    val verdict: String,
    val why: String,
    /** Front rows, the change since "before", and the uncalibrated warning, as they apply. */
    val notes: List<String>,
    /** One title per seat map: "now", or "before" and "after". */
    val mapTitles: List<String>,
    val mapCaption: String,
    /** The four clarity bands with their score ranges, poor first. */
    val legend: List<String>,
    /** One title per echo chart, like [mapTitles]. */
    val echoTitles: List<String>,
    val echoAxisX: String,
    val echoAxisY: String,
    val echoTarget: String,
    val echoTooLong: String,
    val echoCaption: String,
    val fixesTitle: String,
    val fixes: List<String>,
    val fixesNote: String,
    val conditionsTitle: String,
    val conditions: List<String>,
    val footer: List<String>,
) {
    companion object {
        fun of(report: Report, res: Resources, zone: ZoneId = ZoneId.systemDefault()): ReportText {
            val room = report.session.room
            val now = report.now.clarity
            val back = rowVerdict(now, now.rows - 1)
            val comparing = report.before != null
            return ReportText(
                title = res.getString(R.string.report_title),
                roomLine = res.getString(R.string.report_room_line, room.name, formatDateTime(report.createdAtMillis, zone)),
                sizeLine = res.getString(
                    R.string.report_size,
                    room.lengthM.formatMetres(),
                    room.widthM.formatMetres(),
                    room.heightM.formatMetres(),
                    res.getQuantityString(R.plurals.room_plan_rows, room.rows, room.rows),
                    res.getQuantityString(R.plurals.room_plan_seats_per_row, room.cols, room.cols),
                ),
                sampleNote = if (report.isSample) res.getString(R.string.report_sample) else null,
                headline = res.getString(if (now.rows > 1) R.string.results_verdict_back else R.string.results_verdict_all, res.getString(back.band.phraseRes())),
                verdict = res.getString(R.string.report_verdict, rangeText(res, back.band, back.lowSti, back.highSti), res.getString(back.cause.nameRes())),
                why = res.getString(back.cause.whyRes()),
                notes = notes(report, res, zone),
                mapTitles = if (comparing) listOf(res.getString(R.string.report_map_before), res.getString(R.string.report_map_after)) else listOf(res.getString(R.string.report_map_title)),
                mapCaption = res.getString(R.string.report_map_caption),
                legend = StiBand.entries.map { res.getString(it.legendRes()) },
                echoTitles = if (comparing) listOf(res.getString(R.string.compare_echo_before), res.getString(R.string.compare_echo_after)) else listOf(res.getString(R.string.results_rt_title)),
                echoAxisX = res.getString(R.string.chart_pitch_hz),
                echoAxisY = res.getString(R.string.chart_echo_time_s),
                echoTarget = res.getString(R.string.results_rt_target, decimals(report.now.echo.targetS, 1)),
                echoTooLong = res.getString(R.string.results_rt_too_long),
                echoCaption = rt60Caption(res, report.now.echo),
                fixesTitle = res.getString(R.string.fixes_ranking_title),
                fixes = buildList {
                    report.topFixes.forEachIndexed { index, fix -> add(fixLine(index + 1, fix, res)) }
                    if (isEmpty()) add(res.getString(R.string.report_fixes_none))
                    // Why a louder voice or a microphone isn't on the list, as the Fixes tab explains it.
                    if (back.cause == Cause.ECHO) add(res.getString(R.string.fixes_ranking_echo_note))
                },
                fixesNote = res.getString(R.string.report_fixes_note),
                conditionsTitle = res.getString(R.string.report_conditions_title),
                conditions = conditions(report.conditions, res),
                footer = listOf(res.getString(R.string.report_footer_scale), res.getString(R.string.report_footer)),
            )
        }

        private fun notes(report: Report, res: Resources, zone: ZoneId): List<String> = buildList {
            val now = report.now.clarity
            if (now.rows > 1) {
                val front = rowVerdict(now, 0)
                add(res.getString(R.string.results_verdict_front, res.getString(front.band.phraseRes()), rangeText(res, front.band, front.lowSti, front.highSti)))
            }
            val before = report.before
            val baseline = report.session.baseline
            if (before != null && baseline != null) {
                val was = rowVerdict(before.clarity, before.clarity.rows - 1)
                add(res.getString(R.string.report_before, formatDateTime(baseline.takenAtMillis, zone), rangeText(res, was.band, was.lowSti, was.highSti)))
                add(res.getString(R.string.fixes_summary_echo, decimals(before.echo.speechRt60S, 1), decimals(report.now.echo.speechRt60S, 1)))
            }
            if (!now.voiceMeasured) add(res.getString(R.string.report_uncalibrated))
        }

        /** "1. Hang 20 m² of heavy curtains: +0.05 to +0.08 (medium cost)". */
        private fun fixLine(rank: Int, fix: RankedFix, res: Resources): String {
            val (low, high) = signedSti(fix.gainLow) to signedSti(fix.gainHigh)
            val gain = if (low == high) low else res.getString(R.string.report_fix_range, low, high)
            return res.getString(R.string.report_fix, rank, res.getString(fix.preset.nameRes()), gain, res.getString(fix.preset.cost.textRes()))
        }

        private fun conditions(conditions: ReportConditions, res: Resources): List<String> = buildList {
            val mic = conditions.mic
            add(
                when (mic?.source) {
                    null -> res.getString(R.string.report_mic_unknown)
                    AudioSourceKind.UNPROCESSED -> res.getString(R.string.report_mic_unprocessed, kiloHertz(mic.sampleRateHz))
                    AudioSourceKind.VOICE_RECOGNITION -> res.getString(R.string.report_mic_voice, kiloHertz(mic.sampleRateHz))
                },
            )
            add(res.getString(R.string.report_seats, conditions.measuredSeats, conditions.seatCount))
            add(res.getString(R.string.report_readings, conditions.claps, conditions.noiseReadings))
            if (conditions.fans.isNotEmpty()) {
                val locale = res.configuration.locales[0]
                add(res.getString(R.string.report_fans, conditions.fans.joinToString(", ") { res.getString(it.labelRes()).lowercase(locale) }))
            }
            add(res.getString(R.string.report_students, conditions.studentsUsually))
            add(res.getString(if (conditions.voiceMeasured) R.string.report_voice_measured else R.string.report_voice_typical))
            add(res.getString(R.string.report_echo, decimals(conditions.speechEchoS, 1)))
        }

        private fun StiBand.legendRes(): Int = when (this) {
            StiBand.POOR -> R.string.report_legend_poor
            StiBand.FAIR -> R.string.report_legend_fair
            StiBand.GOOD -> R.string.report_legend_good
            StiBand.EXCELLENT -> R.string.report_legend_excellent
        }
    }
}

/** 48000 → "48", 44100 → "44.1": a sample rate the way people say it. */
fun kiloHertz(sampleRateHz: Int): String =
    if (sampleRateHz % 1000 == 0) (sampleRateHz / 1000).toString() else decimals(sampleRateHz / 1000.0, 1)
