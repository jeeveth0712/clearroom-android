package com.clearroom.app.daylog

import com.clearroom.app.audio.AudioClip
import com.clearroom.app.data.BandLevels
import com.clearroom.app.dsp.OctaveBank

/** The few sound kinds the day log names (FR-9); the model's own class names are never stored. */
enum class SoundLabel { FAN, TRAFFIC, HORN, TALKING, MUSIC, QUIET, OTHER }

/** One second of the day log: when, the seven band levels (dBFS) and, if heard, what it was. No audio (NFR-6). */
data class LogSecond(val timeMillis: Long, val bands: BandLevels, val label: SoundLabel?) {
    /** Approximate A-weighted level of the second, dBFS. */
    val dba: Double get() = OctaveBank.aWeightedDb(bands.valuesDb)
}

/** Names what a one-second clip sounds like, on the phone. Implementations hold native memory: [close] them. */
interface SoundLabeller {
    /** The clip's label, or null when nothing scored high enough. */
    fun label(clip: AudioClip): SoundLabel?

    fun close()
}

/**
 * YAMNet's AudioSet class names → the app's labels (noise-day-log skill). The strings are checked
 * against the model's own label list (`yamnet_label_list.txt` inside `assets/yamnet.tflite`).
 * YAMNet's plain "Honk" is a goose, so it's not a horn.
 */
object SoundLabels {
    private val FAN = setOf("Mechanical fan", "Air conditioning")
    private val TRAFFIC = setOf(
        "Traffic noise, roadway noise", "Vehicle", "Motor vehicle (road)", "Car", "Car passing by", "Truck", "Bus",
        "Motorcycle", "Engine", "Light engine (high frequency)", "Medium engine (mid frequency)", "Heavy engine (low frequency)",
    )
    private val HORN = setOf("Vehicle horn, car horn, honking", "Air horn, truck horn", "Train horn")
    private val TALKING = setOf(
        "Speech", "Child speech, kid speaking", "Conversation", "Narration, monologue", "Shout", "Children shouting",
        "Whispering", "Laughter", "Chatter", "Crowd", "Hubbub, speech noise, speech babble", "Children playing",
    )

    fun fromYamnet(className: String): SoundLabel = when {
        className in FAN -> SoundLabel.FAN
        className in TRAFFIC -> SoundLabel.TRAFFIC
        className in HORN -> SoundLabel.HORN
        className in TALKING -> SoundLabel.TALKING
        className == "Musical instrument" || className.contains("music", ignoreCase = true) -> SoundLabel.MUSIC
        className == "Silence" -> SoundLabel.QUIET
        else -> SoundLabel.OTHER
    }
}
