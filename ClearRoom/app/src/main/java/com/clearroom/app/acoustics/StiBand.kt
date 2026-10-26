package com.clearroom.app.acoustics

/**
 * Plain-language clarity band for a Speech Transmission Index value.
 *
 * Doc "Exact parameters": < 0.45 poor · 0.45–0.60 fair · 0.60–0.75 good · ≥ 0.75 excellent.
 * Each band starts at [lowerBound] (inclusive) and ends where the next band starts.
 */
enum class StiBand(val lowerBound: Double) {
    POOR(0.0),
    FAIR(0.45),
    GOOD(0.60),
    EXCELLENT(0.75);

    companion object {
        /** Band for [sti], which must be a finite value in [0, 1]. */
        fun from(sti: Double): StiBand {
            require(sti.isFinite() && sti in 0.0..1.0) { "STI must be a finite value in [0, 1], was $sti" }
            return entries.last { sti >= it.lowerBound }
        }
    }
}
