package com.clearroom.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.Cause
import com.clearroom.app.acoustics.CostTier
import com.clearroom.app.acoustics.FixPreset
import com.clearroom.app.acoustics.FixSettings
import com.clearroom.app.acoustics.Fixes
import com.clearroom.app.acoustics.FixesOutcome
import com.clearroom.app.acoustics.RoomClarity
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.acoustics.VoiceNeed
import com.clearroom.app.data.Session
import com.clearroom.app.dsp.OctaveBands
import com.clearroom.app.ui.AppTab
import com.clearroom.app.ui.FixesView
import com.clearroom.app.ui.UiEvent
import com.clearroom.app.ui.formatMetres
import com.clearroom.app.viz.ClarityMorph3D
import com.clearroom.app.viz.FixImprovementChart
import com.clearroom.app.viz.FixRowWords
import com.clearroom.app.viz.RowCompareChart
import com.clearroom.app.viz.VoiceRowsChart
import com.clearroom.app.viz.VoiceWords
import com.clearroom.app.viz.rowComparison
import com.clearroom.app.viz.voiceRows
import com.clearroom.app.viz.VoiceAdvice
import com.clearroom.app.viz.decimals
import com.clearroom.app.viz.voiceAdvice
import kotlin.math.roundToInt

/** The speaker's voice slider in words: quieter, normal, raised (+6 dB) or loud (+12 dB). */
enum class VoiceEffort { QUIETER, NORMAL, RAISED, LOUD }

fun voiceEffort(changeDb: Double): VoiceEffort = when {
    changeDb <= -2.0 -> VoiceEffort.QUIETER
    changeDb < Fixes.RAISED_VOICE_DB / 2 -> VoiceEffort.NORMAL
    changeDb < (Fixes.RAISED_VOICE_DB + Fixes.LOUD_VOICE_DB) / 2 -> VoiceEffort.RAISED
    else -> VoiceEffort.LOUD
}

/** Echo time over the speech bands (500 Hz–2 kHz): the one number people quote. */
fun speechEchoS(clarity: RoomClarity): Double = OctaveBands.SPEECH_BANDS.map { clarity.rt60S[it] }.average()

/**
 * Fixes tab (doc "Screens"): try changes before spending money. The sliders show [settings] as
 * they are now; [view] (worked out off the main thread) catches up a moment later.
 */
@Composable
fun FixesScreen(session: Session?, settings: FixSettings, view: FixesView?, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    val outcome = view?.outcome
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(AppTab.FIXES, if (outcome == null) R.string.fixes_intro else null)
        when {
            session == null -> InfoCard { Body(R.string.results_no_room) }
            view == null -> InfoCard { Body(R.string.results_working) }
            outcome == null -> InfoCard { Body(R.string.fixes_needs_measurements) }
            else -> {
                SummaryCard(outcome, settings)
                RankingCard(outcome)
                ControlsCard(view.session, settings, onEvent)
                RowComparisonCard(outcome)
                MorphCard(view.session, outcome.after)
                VoiceCard(outcome, settings.voiceChangeDb)
            }
        }
    }
}

@Composable
private fun SummaryCard(outcome: FixesOutcome, settings: FixSettings) {
    val now = outcome.now.worstSeat(outcome.now.rows - 1)
    val after = outcome.after.worstSeat(outcome.after.rows - 1)
    HeroCard {
        Text(stringResource(if (settings.isAsMeasured) R.string.fixes_summary_title_now else R.string.fixes_summary_title), style = MaterialTheme.typography.titleMedium)
        if (settings.isAsMeasured) {
            Text(stringResource(R.string.fixes_summary_back_now, stringResource(now.band.phraseRes())), style = MaterialTheme.typography.titleLarge)
            BandRange(now.band, now.stiLow, now.stiHigh, large = true)
            Text(stringResource(R.string.fixes_summary_echo_now, decimals(speechEchoS(outcome.now), 1)), style = MaterialTheme.typography.bodyMedium)
            Body(R.string.fixes_summary_hint)
        } else {
            Text(
                stringResource(R.string.fixes_summary_back, stringResource(now.band.phraseRes()), stringResource(after.band.phraseRes())),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.fixes_summary_now, rangeText(now.band, now.stiLow, now.stiHigh)), style = MaterialTheme.typography.bodyMedium)
            BandRange(after.band, after.stiLow, after.stiHigh, large = true)
            Text(
                stringResource(R.string.fixes_summary_echo, decimals(speechEchoS(outcome.now), 1), decimals(speechEchoS(outcome.after), 1)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MorphCard(session: Session, after: RoomClarity) {
    ChartCard(R.string.fixes_morph_title) {
        val description = stringResource(
            R.string.fixes_morph_description,
            stringResource(StiBand.from(after.frontRowSti).labelRes()),
            stringResource(StiBand.from(after.backRowSti).labelRes()),
        )
        ClarityMorph3D(session.room, after, description)
        BandLegend()
        Text(stringResource(R.string.fixes_morph_caption), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ControlsCard(session: Session, settings: FixSettings, onEvent: (UiEvent) -> Unit) {
    val room = session.room
    val change = { updated: FixSettings -> onEvent(UiEvent.FixesChanged(updated)) }
    ChartCard(R.string.fixes_controls_title) {
        val maxCurtain = Fixes.maxCurtainM2(room)
        LabelledSlider(
            label = stringResource(R.string.fixes_curtains, settings.curtainM2.roundToInt()),
            value = settings.curtainM2.toFloat(),
            range = 0f..maxCurtain.toFloat(),
            steps = (maxCurtain / CURTAIN_STEP_M2).toInt() - 1,
        ) { change(settings.copy(curtainM2 = (it / CURTAIN_STEP_M2).roundToInt() * CURTAIN_STEP_M2)) }

        val maxStudents = Fixes.maxStudents(room)
        LabelledSlider(
            label = stringResource(R.string.fixes_students, settings.studentsPresent),
            value = settings.studentsPresent.toFloat(),
            range = 0f..maxStudents.toFloat(),
        ) { change(settings.copy(studentsPresent = it.roundToInt())) }

        val fansWereOn = Fixes.fansWereOn(session)
        LabelledSwitch(stringResource(R.string.fixes_fans_off), checked = settings.fansOff && fansWereOn, enabled = fansWereOn) { change(settings.copy(fansOff = it)) }
        if (!fansWereOn) Text(stringResource(R.string.fixes_fans_already_off), style = MaterialTheme.typography.bodySmall)
        LabelledSwitch(stringResource(R.string.fixes_microphone), checked = settings.microphone) { change(settings.copy(microphone = it)) }

        LabelledSlider(
            label = stringResource(R.string.fixes_voice, stringResource(voiceEffort(settings.voiceChangeDb).textRes()), signedDb(settings.voiceChangeDb)),
            value = settings.voiceChangeDb.toFloat(),
            range = MIN_VOICE_DB..MAX_VOICE_DB,
            steps = (MAX_VOICE_DB - MIN_VOICE_DB).toInt() - 1,
        ) { change(settings.copy(voiceChangeDb = it.roundToInt().toDouble())) }

        val usual = SeatLayout.SPEAKER_FROM_FRONT_M
        val far = (room.lengthM - Fixes.WALL_GAP_M).toFloat()
        val speakerAt = (settings.speakerFromFrontM ?: usual).coerceIn(Fixes.WALL_GAP_M, far.toDouble())
        LabelledSlider(
            label = stringResource(R.string.fixes_speaker, speakerAt.formatMetres()),
            value = speakerAt.toFloat(),
            range = Fixes.WALL_GAP_M.toFloat()..far,
        ) { metres ->
            val snapped = (metres / SPEAKER_STEP_M).roundToInt() * SPEAKER_STEP_M
            change(settings.copy(speakerFromFrontM = snapped.takeIf { it != usual }))
        }

        OutlinedButton(onClick = { onEvent(UiEvent.ResetFixes) }, enabled = !settings.isAsMeasured) { Text(stringResource(R.string.fixes_reset)) }
    }
}

@Composable
private fun LabelledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
            // The unfilled part in grey: the theme's default for it is the accent, the same as the filled part at night.
            colors = SliderDefaults.colors(
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun LabelledSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun RankingCard(outcome: FixesOutcome) {
    val ranking = outcome.ranking
    if (ranking.isEmpty()) return
    ChartCard(R.string.fixes_ranking_title) {
        val names = ranking.map { stringResource(it.preset.nameRes()) }
        val values = ranking.map { stringResource(R.string.fixes_ranking_value, signedSti(it.gain), stringResource(it.preset.cost.textRes())) }
        val best = ranking.first()
        if (best.gain > 0.0) Text(stringResource(R.string.fixes_ranking_best, names.first(), signedSti(best.gain)), style = MaterialTheme.typography.titleSmall)
        FixImprovementChart(
            ranking,
            outcome.now.backRowSti,
            ranking.mapIndexed { index, fix -> FixRowWords(names[index], stringResource(fix.preset.cost.textRes()), signedSti(fix.gain)) },
            stringResource(R.string.fixes_chart_current),
            stringResource(R.string.fixes_chart_axis),
        )
        val backCause = outcome.now.worstSeat(outcome.now.rows - 1).cause
        if (backCause == Cause.ECHO) Text(stringResource(R.string.fixes_ranking_echo_note), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.fixes_ranking_caption), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RowComparisonCard(outcome: FixesOutcome) {
    if (outcome.now == outcome.after) return
    val rows = rowComparison(outcome.now, outcome.after)
    ChartCard(R.string.fixes_chart_row_compare) {
        RowCompareChart(
            rows,
            rows.map { stringResource(R.string.fixes_chart_row, it.row + 1) },
            rows.map { stringResource(R.string.fixes_chart_row_value,
                rangeText(it.now.band, it.now.stiLow, it.now.stiHigh),
                rangeText(it.after.band, it.after.stiLow, it.after.stiHigh)) },
        )
    }
}

@Composable
private fun VoiceCard(outcome: FixesOutcome, voiceChangeDb: Double) {
    ChartCard(R.string.fixes_voice_title) {
        val back = outcome.voice.last().need
        val advice = voiceAdvice(back)
        val caption = buildString {
            when (back) {
                is VoiceNeed.Required -> append(stringResource(advice.textRes(), back.changeDb.roundToInt()))
                VoiceNeed.Unreachable -> append(stringResource(advice.textRes()))
            }
            if (!outcome.after.voiceMeasured) append(' ').append(stringResource(R.string.fixes_voice_uncalibrated))
        }
        val rows = voiceRows(outcome, voiceChangeDb)
        VoiceRowsChart(
            rows,
            rows.map { stringResource(R.string.fixes_chart_row, it.row + 1) },
            rows.map { row ->
                val effort = when (row.advice) {
                    VoiceAdvice.NORMAL_IS_ENOUGH -> R.string.fixes_voice_normal
                    VoiceAdvice.RAISED -> R.string.fixes_voice_raised
                    VoiceAdvice.LOUD, VoiceAdvice.BEYOND_LOUD -> R.string.fixes_voice_loud
                    VoiceAdvice.UNREACHABLE -> R.string.fixes_chart_voice_unreachable
                }
                VoiceWords(stringResource(effort), row.neededDba?.let {
                    stringResource(R.string.fixes_chart_voice_detail, decimals(it, 0))
                } ?: stringResource(R.string.fixes_chart_voice_unreachable))
            },
            stringResource(R.string.fixes_chart_voice_axis),
        )
        Text(caption, style = MaterialTheme.typography.bodySmall)
    }
}

/** A change in score with its sign: "+0.08", "-0.01". */
internal fun signedSti(value: Double): String = (if (value >= 0) "+" else "") + decimals(value, 2)

private fun signedDb(value: Double): String = (if (value > 0) "+" else "") + decimals(value, 0)

@StringRes
internal fun FixPreset.nameRes(): Int = when (this) {
    FixPreset.CURTAINS -> R.string.fix_curtains
    FixPreset.FANS_OFF -> R.string.fix_fans_off
    FixPreset.MICROPHONE -> R.string.fix_microphone
    FixPreset.SPEAKER_CLOSER -> R.string.fix_speaker_closer
    FixPreset.RAISED_VOICE -> R.string.fix_raised_voice
}

@StringRes
internal fun CostTier.textRes(): Int = when (this) {
    CostTier.FREE -> R.string.cost_free
    CostTier.LOW -> R.string.cost_low
    CostTier.MEDIUM -> R.string.cost_medium
}

@StringRes
private fun VoiceEffort.textRes(): Int = when (this) {
    VoiceEffort.QUIETER -> R.string.effort_quieter
    VoiceEffort.NORMAL -> R.string.effort_normal
    VoiceEffort.RAISED -> R.string.effort_raised
    VoiceEffort.LOUD -> R.string.effort_loud
}

@StringRes
private fun VoiceAdvice.textRes(): Int = when (this) {
    VoiceAdvice.NORMAL_IS_ENOUGH -> R.string.fixes_voice_normal_enough
    VoiceAdvice.RAISED -> R.string.fixes_voice_needs_raised
    VoiceAdvice.LOUD -> R.string.fixes_voice_needs_loud
    VoiceAdvice.BEYOND_LOUD -> R.string.fixes_voice_beyond_loud
    VoiceAdvice.UNREACHABLE -> R.string.fixes_voice_unreachable
}

private const val CURTAIN_STEP_M2 = 5.0
private const val SPEAKER_STEP_M = 0.25
private const val MIN_VOICE_DB = -6f
private const val MAX_VOICE_DB = 12f
