package com.clearroom.app.viz

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.clearroom.app.acoustics.RoomClarity
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.Room
import com.clearroom.app.ui.theme.color
import com.clearroom.app.viz.math3d.Argb
import com.clearroom.app.viz.math3d.OrbitCamera
import com.clearroom.app.viz.math3d.Primitives
import com.clearroom.app.viz.math3d.SceneGeometry
import com.clearroom.app.viz.math3d.Vec3
import kotlin.math.max
import kotlin.math.min

/** Colours for the parts of V8 that aren't clarity bands (those are always the fixed band colours). */
data class RoomSceneColours(val floor: Argb, val walls: Argb, val speaker: Argb)

/**
 * V8, the 3D clarity room: the room to scale, one bar per seat whose height is its STI and whose
 * colour is its band, and the speaker as a cone at the front (doc "3D scenes" table).
 */
object ClarityRoomScene {
    /** STI 1.0 reaches half the room height, so bars never poke through the ceiling. */
    fun barHeightM(sti: Double, roomHeightM: Double): Double = max(MIN_BAR_M, sti * roomHeightM / 2)

    fun build(room: Room, clarity: RoomClarity, colours: RoomSceneColours, bandColour: (StiBand) -> Argb): SceneGeometry {
        val walls = Primitives.wireBox(Vec3(0.0, 0.0, 0.0), Vec3(room.widthM, room.heightM, room.lengthM), colours.walls)
        val floor = Primitives.floor(room.widthM, room.lengthM, colours.floor)
        val half = barWidthM(room) / 2
        val bars = clarity.seats.flatMap { seat ->
            val centre = SeatLayout.position(room, seat.seat)
            Primitives.box(
                min = Vec3(centre.xM - half, 0.0, centre.yM - half),
                max = Vec3(centre.xM + half, barHeightM(seat.sti, room.heightM), centre.yM + half),
                color = bandColour(seat.band),
            )
        }
        val speakerSpot = SeatLayout.speaker(room)
        val speaker = Primitives.cone(Vec3(speakerSpot.xM, 0.0, speakerSpot.yM), SPEAKER_RADIUS_M, SPEAKER_HEIGHT_M, sides = SPEAKER_SIDES, colours.speaker)
        return SceneGeometry(faces = listOf(floor) + bars + speaker, lines = walls)
    }

    /** 0.45 m, or narrower where seats are closer, always leaving a gap so neighbouring bars never merge. */
    fun barWidthM(room: Room): Double = min(MAX_BAR_WIDTH_M, BAR_SHARE_OF_SPACING * SeatLayout.spacingM(room))

    const val MAX_BAR_WIDTH_M = 0.45
    private const val BAR_SHARE_OF_SPACING = 0.8
    private const val MIN_BAR_M = 0.05 // a seat scoring 0 still shows as a red stub
    private const val SPEAKER_RADIUS_M = 0.3
    private const val SPEAKER_HEIGHT_M = 1.6 // about a standing person
    private const val SPEAKER_SIDES = 8
}

@Composable
fun ClarityRoom3D(room: Room, clarity: RoomClarity, description: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // The speaker is grey on purpose: any hue could be mistaken for one of the clarity band colours.
    val colours = RoomSceneColours(floor = scheme.surfaceVariant.argb(), walls = scheme.outline.argb(), speaker = scheme.onSurfaceVariant.argb())
    val geometry = remember(room, clarity, colours) { ClarityRoomScene.build(room, clarity, colours) { it.color().argb() } }
    val home = remember(room) { OrbitCamera.homeView(room.widthM, room.lengthM, room.heightM) }
    Scene3D(geometry, home, description, modifier.fillMaxWidth().height(320.dp))
}

private fun Color.argb() = Argb(toArgb())
