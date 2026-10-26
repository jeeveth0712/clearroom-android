package com.clearroom.app.acoustics

import com.clearroom.app.data.FanState
import com.clearroom.app.data.Room
import com.clearroom.app.data.Session
import kotlin.math.max
import kotlin.math.min

/**
 * The Fixes tab's controls (FR-8), as the user set them; everything starts "as measured".
 * [toWhatIf] turns them into the clarity engine's [WhatIf].
 */
data class FixSettings(
    /** Heavy curtain hung in the room, m² (doc α table). */
    val curtainM2: Double = 0.0,
    /** Students seated in the room; the measurement is taken as an empty room. */
    val studentsPresent: Int = 0,
    val fansOff: Boolean = false,
    val microphone: Boolean = false,
    /** Speaker's voice relative to the measured (or typical) normal voice, dB. */
    val voiceChangeDb: Double = 0.0,
    /** Where the speaker stands, metres from the front wall; null = the usual spot (1 m). */
    val speakerFromFrontM: Double? = null,
) {
    init {
        require(curtainM2 >= 0.0 && curtainM2.isFinite()) { "curtain area must be ≥ 0, was $curtainM2" }
        require(studentsPresent >= 0) { "students can't be negative: $studentsPresent" }
        require(voiceChangeDb.isFinite()) { "voice change must be finite" }
        require(speakerFromFrontM == null || (speakerFromFrontM.isFinite() && speakerFromFrontM > 0.0)) { "bad speaker spot: $speakerFromFrontM" }
    }

    val isAsMeasured: Boolean get() = this == FixSettings()

    /** The speaker spot is kept inside the room, so an edited (shorter) room can't put it through a wall. */
    fun toWhatIf(room: Room): WhatIf = WhatIf(
        absorbers = if (curtainM2 > 0.0) listOf(Absorber(curtainM2, Absorbers.HEAVY_CURTAIN_ALPHA)) else emptyList(),
        extraStudents = studentsPresent,
        noiseChangeDb = if (fansOff) Fixes.FANS_OFF_NOISE_DB else 0.0,
        voiceChangeDb = voiceChangeDb,
        micGainDb = if (microphone) Fixes.MIC_GAIN_DB else 0.0,
        speaker = speakerFromFrontM?.let { FloorPoint(room.widthM / 2, it.coerceIn(Fixes.WALL_GAP_M, room.lengthM - Fixes.WALL_GAP_M)) },
    )
}

/** A rough price class until real ₹ prices are entered (doc V6 labels fixes with their cost). */
enum class CostTier { FREE, LOW, MEDIUM }

/** One ready-made change for the fix ranking (V6), with roughly what it costs. */
enum class FixPreset(val cost: CostTier) {
    CURTAINS(CostTier.MEDIUM),
    FANS_OFF(CostTier.FREE),
    MICROPHONE(CostTier.LOW),
    SPEAKER_CLOSER(CostTier.FREE),
    RAISED_VOICE(CostTier.FREE);

    val settings: FixSettings
        get() = when (this) {
            CURTAINS -> FixSettings(curtainM2 = Fixes.PRESET_CURTAIN_M2)
            FANS_OFF -> FixSettings(fansOff = true)
            MICROPHONE -> FixSettings(microphone = true)
            SPEAKER_CLOSER -> FixSettings(speakerFromFrontM = SeatLayout.SPEAKER_FROM_FRONT_M + Fixes.PRESET_SPEAKER_STEP_M)
            RAISED_VOICE -> FixSettings(voiceChangeDb = Fixes.RAISED_VOICE_DB)
        }
}

/** How much one preset alone lifts the back row's STI, with its range from the echo-time spread (NFR-10). */
data class RankedFix(val preset: FixPreset, val gain: Double, val gainLow: Double, val gainHigh: Double)

/** The Fixes tab's results: the room as measured, the room with the chosen fixes, the ranking and the voice needed per row. */
data class FixesOutcome(val now: RoomClarity, val after: RoomClarity, val ranking: List<RankedFix>, val voice: List<RowVoice>)

/** FR-8 what-if and FR-11 voice advice for the Fixes tab, on top of [ClarityModel]. */
object Fixes {
    /** Skill presets: 20 m² of heavy curtain, fans off ≈ 6 dB quieter, a portable mic ≈ 10 dB louder at every seat. */
    const val PRESET_CURTAIN_M2 = 20.0
    const val FANS_OFF_NOISE_DB = -6.0
    const val MIC_GAIN_DB = 10.0

    /** ISO 9921 vocal effort steps at 1 m: raised = normal + 6 dB, loud = normal + 12 dB. */
    const val RAISED_VOICE_DB = 6.0
    const val LOUD_VOICE_DB = 12.0

    /** "Stand 2 m closer to the class": from the usual 1 m to 3 m from the front wall. */
    const val PRESET_SPEAKER_STEP_M = 2.0

    /** The speaker stays at least this far from any wall. */
    const val WALL_GAP_M = 0.5

    private const val MAX_CURTAIN_M2 = 60.0

    /** Up to half the wall area, and never more than 60 m²: windows and one wall, not the whole room. */
    fun maxCurtainM2(room: Room): Double = min(MAX_CURTAIN_M2, room.wallAreaM2 / 2)

    /** Room for a full class: the usual number of students, or one per seat, whichever is more. */
    fun maxStudents(room: Room): Int = max(room.students, room.rows * room.cols)

    /** "Fans off" only means something if fans were on during a noise reading. */
    fun fansWereOn(session: Session): Boolean = session.seats.any { it.noise != null && it.fans != null && it.fans != FanState.OFF }

    /** The presets that make sense for this room: fans off only if fans were on. */
    fun presets(session: Session): List<FixPreset> = FixPreset.entries.filter { it != FixPreset.FANS_OFF || fansWereOn(session) }

    /** Each preset applied alone to the room as measured, best back-row gain first (V6). Negative gains stay in. */
    fun rank(session: Session): List<RankedFix> {
        val now = ready(session, FixSettings()) ?: return emptyList()
        val before = now.worstSeat(now.rows - 1)
        return presets(session).mapNotNull { preset ->
            val after = ready(session, preset.settings)?.let { it.worstSeat(it.rows - 1) } ?: return@mapNotNull null
            val gain = after.sti - before.sti
            val ends = listOf(after.stiLow - before.stiLow, after.stiHigh - before.stiHigh, gain)
            RankedFix(preset, gain, ends.min(), ends.max())
        }.sortedByDescending { it.gain }
    }

    /**
     * Everything the Fixes tab shows; null until the room has enough measurements. The voice
     * needed (V7) includes the other changes (a mic lowers it) but not the voice slider itself,
     * because it answers "how loud must the speaker be?".
     */
    fun outcome(session: Session, settings: FixSettings): FixesOutcome? {
        val now = ready(session, FixSettings()) ?: return null
        val after = ready(session, settings) ?: return null
        val voiceWhatIf = settings.copy(voiceChangeDb = 0.0).toWhatIf(session.room)
        val voice = ClarityModel.requiredVoice(session, voiceWhatIf) ?: return null
        return FixesOutcome(now, after, rank(session), voice)
    }

    private fun ready(session: Session, settings: FixSettings): RoomClarity? =
        (ClarityModel.evaluate(session, settings.toWhatIf(session.room)) as? ClarityResult.Ready)?.room
}
