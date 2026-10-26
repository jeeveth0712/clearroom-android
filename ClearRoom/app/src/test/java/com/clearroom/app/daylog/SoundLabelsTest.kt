package com.clearroom.app.daylog

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundLabelsTest {

    /** The bundled model, as the app ships it (JVM tests run from the app module's folder). */
    private val model = File("src/main/assets/yamnet.tflite")

    @Test
    fun `YAMNet classes map onto the app's few labels`() {
        assertEquals(SoundLabel.FAN, SoundLabels.fromYamnet("Mechanical fan"))
        assertEquals(SoundLabel.FAN, SoundLabels.fromYamnet("Air conditioning"))
        assertEquals(SoundLabel.TRAFFIC, SoundLabels.fromYamnet("Traffic noise, roadway noise"))
        assertEquals(SoundLabel.HORN, SoundLabels.fromYamnet("Vehicle horn, car horn, honking"))
        assertEquals(SoundLabel.TALKING, SoundLabels.fromYamnet("Hubbub, speech noise, speech babble"))
        assertEquals(SoundLabel.MUSIC, SoundLabels.fromYamnet("Music of Bollywood"))
        assertEquals(SoundLabel.MUSIC, SoundLabels.fromYamnet("Musical instrument"))
        assertEquals(SoundLabel.QUIET, SoundLabels.fromYamnet("Silence"))
        assertEquals(SoundLabel.OTHER, SoundLabels.fromYamnet("Dog"))
    }

    @Test
    fun `a goose's honk is not a car horn`() {
        assertEquals(SoundLabel.OTHER, SoundLabels.fromYamnet("Honk"))
    }

    @Test
    fun `every class name the map uses is really in the bundled model`() {
        val modelLabels = ZipFile(model).use { zip ->
            zip.getInputStream(zip.getEntry("yamnet_label_list.txt")).bufferedReader().readLines().toSet()
        }
        val used = listOf(
            "Mechanical fan", "Air conditioning", "Traffic noise, roadway noise", "Vehicle", "Motor vehicle (road)", "Car",
            "Car passing by", "Truck", "Bus", "Motorcycle", "Engine", "Vehicle horn, car horn, honking", "Air horn, truck horn",
            "Train horn", "Speech", "Conversation", "Chatter", "Hubbub, speech noise, speech babble", "Children shouting", "Music", "Silence",
        )
        val missing = used.filterNot { it in modelLabels }
        assertTrue(missing.isEmpty(), "not in the model: $missing")
        // And each of those maps to something other than "other".
        assertTrue(used.none { SoundLabels.fromYamnet(it) == SoundLabel.OTHER })
    }

    @Test
    fun `the bundled model is the one that was checked`() {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(model.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(YAMNET_SHA256, sha256, "assets/yamnet.tflite changed: check where the new file came from")
    }

    private companion object {
        /** MediaPipe's float32 YAMNet, downloaded 2026-10-26 from storage.googleapis.com/mediapipe-models. */
        const val YAMNET_SHA256 = "4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf"
    }
}
