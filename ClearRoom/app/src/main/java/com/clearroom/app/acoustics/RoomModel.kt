package com.clearroom.app.acoustics

import com.clearroom.app.data.BandLevels
import com.clearroom.app.data.Room
import com.clearroom.app.dsp.OctaveBands
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The room as Sabine's equation sees it (doc "Analysis methods" step 2): T60 = 0.161·V / A, with
 * the absorption A per band backed out of the measured echo time, so what-if changes start from
 * the real room rather than guessed materials.
 */
data class SabineRoom(val volumeM3: Double, val surfaceM2: Double, val absorptionM2: List<Double>) {
    init {
        require(volumeM3 > 0.0 && surfaceM2 > 0.0) { "room needs a volume and surface: $volumeM3, $surfaceM2" }
        require(absorptionM2.size == OctaveBands.COUNT && absorptionM2.all { it.isFinite() && it > 0.0 }) {
            "absorption must be positive in all ${OctaveBands.COUNT} bands: $absorptionM2"
        }
    }

    fun t60S(band: Int): Double = SABINE_S_PER_M * volumeM3 / absorptionM2[band]

    val t60S: List<Double> get() = List(OctaveBands.COUNT, ::t60S)

    /** Mean absorption coefficient, capped below 1 because the room constant divides by 1 − ᾱ. */
    fun meanAlpha(band: Int): Double = min(absorptionM2[band] / surfaceM2, MAX_MEAN_ALPHA)

    /** Room constant R = S·ᾱ / (1 − ᾱ) (doc step 3). */
    fun roomConstantM2(band: Int): Double = meanAlpha(band).let { surfaceM2 * it / (1 - it) }

    /** FR-8: [areaM2] of extra absorbing material with coefficients [alpha] per band (Sabine: A' = A + S·α). */
    fun withAbsorber(areaM2: Double, alpha: List<Double>): SabineRoom {
        require(areaM2 >= 0.0 && alpha.size == OctaveBands.COUNT && alpha.all { it in 0.0..1.0 }) { "bad absorber: $areaM2 m², $alpha" }
        return copy(absorptionM2 = absorptionM2.mapIndexed { band, a -> a + areaM2 * alpha[band] })
    }

    /** FR-8: [count] people, each adding [perPersonM2] m² of absorption per band. */
    fun withPeople(count: Int, perPersonM2: List<Double>): SabineRoom {
        require(count >= 0 && perPersonM2.size == OctaveBands.COUNT) { "bad people: $count x $perPersonM2" }
        return copy(absorptionM2 = absorptionM2.mapIndexed { band, a -> a + count * perPersonM2[band] })
    }

    companion object {
        /** Sabine's constant in s/m (doc formula: 0.161). */
        const val SABINE_S_PER_M = 0.161

        const val MAX_MEAN_ALPHA = 0.95

        /** Fits the room to measured echo times: A = 0.161·V / T60 per band. */
        fun fitted(room: Room, t60S: List<Double>): SabineRoom {
            require(t60S.size == OctaveBands.COUNT && t60S.all { it.isFinite() && it > 0.0 }) { "bad echo times: $t60S" }
            return SabineRoom(room.volumeM3, room.surfaceAreaM2, t60S.map { SABINE_S_PER_M * room.volumeM3 / it })
        }
    }
}

/**
 * Speech level at a distance (doc step 3): direct sound falls with distance, reverberant sound
 * stays roughly constant. Talker directivity Q = 2 (doc "Room defaults").
 */
object SpeechPropagation {
    const val TALKER_DIRECTIVITY_Q = 2.0

    /** Closer than this the free-field formula blows up; nobody sits that close to a talker. */
    const val MIN_DISTANCE_M = 0.5

    /** Direct energy density factor Q/(4πr²) at [distanceM]. */
    fun directFactor(distanceM: Double): Double {
        val r = max(distanceM, MIN_DISTANCE_M)
        return TALKER_DIRECTIVITY_Q / (4 * PI * r * r)
    }

    /** Reverberant energy density factor 4/R. */
    fun reverberantFactor(roomConstantM2: Double): Double = 4 / roomConstantM2

    /** L_s(r) = L_1m + 10·log10((Q/4πr² + 4/R) / (Q/4π + 4/R)): exactly L_1m at 1 m. */
    fun levelDb(distanceM: Double, levelAt1mDb: Double, roomConstantM2: Double): Double {
        val reverberant = reverberantFactor(roomConstantM2)
        return levelAt1mDb + 10 * log10((directFactor(distanceM) + reverberant) / (directFactor(1.0) + reverberant))
    }

    /** Direct-to-reverberant energy ratio at [distanceM]; 1 at the critical distance. */
    fun directToReverberant(distanceM: Double, roomConstantM2: Double): Double =
        directFactor(distanceM) / reverberantFactor(roomConstantM2)

    /** Doc step 5: r_c = 0.057·√(Q·V / T60), where direct and reverberant sound are equally loud. */
    fun criticalDistanceM(volumeM3: Double, t60S: Double): Double {
        require(volumeM3 > 0.0 && t60S > 0.0) { "bad room: $volumeM3 m³, $t60S s" }
        return 0.057 * sqrt(TALKER_DIRECTIVITY_Q * volumeM3 / t60S)
    }
}

/**
 * Background noise at any point, from the seats where it was measured: inverse-distance-squared
 * weighting of energies (not dB), per band. A measured seat keeps its own reading; with one
 * measured seat its noise is used everywhere.
 */
object NoiseField {
    private const val SAME_PLACE_M = 1e-6

    fun at(point: FloorPoint, readings: List<Pair<FloorPoint, BandLevels>>): BandLevels {
        require(readings.isNotEmpty()) { "need at least one noise reading" }
        readings.firstOrNull { it.first.distanceTo(point) < SAME_PLACE_M }?.let { return it.second }
        val weights = readings.map { 1 / point.distanceTo(it.first).pow(2) }
        val totalWeight = weights.sum()
        return BandLevels(
            List(OctaveBands.COUNT) { band ->
                val energy = readings.indices.sumOf { weights[it] * 10.0.pow(readings[it].second[band] / 10) } / totalWeight
                10 * log10(energy)
            },
        )
    }
}
