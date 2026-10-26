package com.clearroom.app.viz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clearroom.app.R
import com.clearroom.app.acoustics.RoomClarity
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.Room
import com.clearroom.app.ui.theme.SceneColors
import com.clearroom.app.ui.theme.color
import com.clearroom.app.viz.math3d.Argb
import com.clearroom.app.viz.math3d.Label3
import com.clearroom.app.viz.math3d.OrbitCamera
import com.clearroom.app.viz.math3d.Primitives
import com.clearroom.app.viz.math3d.SceneGeometry
import com.clearroom.app.viz.math3d.Solid
import com.clearroom.app.viz.math3d.Vec3
import kotlin.math.max
import kotlin.math.min

/** Colours for the parts of V8 that aren't clarity bands (those are always the fixed band colours). */
data class RoomSceneColours(val floor: Argb, val seatTile: Argb, val walls: Argb, val speaker: Argb)

/** The scene's words, passed in so the builder needs no Android resources. */
data class RoomSceneLabels(val speaker: String, val back: String)

/**
 * V8, the 3D clarity room (doc "3D scenes" table): the room to scale as a cut-away model, a tile
 * on the floor for every seat with a bar on it whose height is the seat's STI and whose colour is
 * its band, and the speaker as a cone at the front.
 */
object ClarityRoomScene {
    /** STI 1.0 reaches 80 % of the room height: tall enough to compare at a glance, never through the ceiling. */
    fun barHeightM(sti: Double, roomHeightM: Double): Double = max(MIN_BAR_M, sti * BAR_SHARE_OF_HEIGHT * roomHeightM)

    fun build(
        room: Room,
        clarity: RoomClarity,
        colours: RoomSceneColours,
        labels: RoomSceneLabels,
        bandColour: (StiBand) -> Argb,
    ): SceneGeometry {
        val walls = Primitives.wallsFacingIn(room.widthM, room.lengthM, room.heightM, colours.walls)
        val floor = Primitives.floor(room.widthM, room.lengthM, colours.floor)
        val spots = clarity.seats.map { SeatLayout.position(room, it.seat) }
        val tileHalf = tileWidthM(room) / 2
        val tiles = spots.map { Primitives.floorRect(it.xM - tileHalf, it.yM - tileHalf, it.xM + tileHalf, it.yM + tileHalf, colours.seatTile) }
        val barHalf = barWidthM(room) / 2
        val bars = clarity.seats.zip(spots) { seat, spot ->
            Solid(
                Primitives.box(
                    min = Vec3(spot.xM - barHalf, 0.0, spot.yM - barHalf),
                    max = Vec3(spot.xM + barHalf, barHeightM(seat.sti, room.heightM), spot.yM + barHalf),
                    color = bandColour(seat.band),
                ),
            )
        }
        val speakerSpot = SeatLayout.speaker(room)
        val speaker = Solid(Primitives.cone(Vec3(speakerSpot.xM, 0.0, speakerSpot.yM), SPEAKER_RADIUS_M, SPEAKER_HEIGHT_M, SPEAKER_SIDES, colours.speaker))
        return SceneGeometry(
            ground = walls + floor + tiles,
            solids = bars + speaker,
            labels = listOf(
                Label3(Vec3(speakerSpot.xM, SPEAKER_HEIGHT_M + LABEL_LIFT_M, speakerSpot.yM), labels.speaker),
                Label3(Vec3(room.widthM / 2, 0.0, room.lengthM), labels.back),
            ),
        )
    }

    /** 0.45 m, or narrower where seats are closer, always leaving a gap so neighbouring bars never merge. */
    fun barWidthM(room: Room): Double = min(MAX_BAR_WIDTH_M, BAR_SHARE_OF_SPACING * SeatLayout.spacingM(room))

    /** The seat's square on the floor: wider than its bar, so the seat grid shows even under short bars. */
    fun tileWidthM(room: Room): Double = min(MAX_TILE_WIDTH_M, TILE_SHARE_OF_SPACING * SeatLayout.spacingM(room))

    const val MAX_BAR_WIDTH_M = 0.45
    const val MAX_TILE_WIDTH_M = 0.7
    private const val BAR_SHARE_OF_HEIGHT = 0.8
    private const val BAR_SHARE_OF_SPACING = 0.8
    private const val TILE_SHARE_OF_SPACING = 0.9
    private const val MIN_BAR_M = 0.05 // a seat scoring 0 still shows as a red stub
    private const val SPEAKER_RADIUS_M = 0.3
    const val SPEAKER_HEIGHT_M = 1.6 // about a standing person
    private const val SPEAKER_SIDES = 8
    private const val LABEL_LIFT_M = 0.5
}

@Composable
fun ClarityRoom3D(room: Room, clarity: RoomClarity, description: String, modifier: Modifier = Modifier) {
    val colours = RoomSceneColours(
        floor = SceneColors.Floor.argb(),
        seatTile = SceneColors.SeatTile.argb(),
        walls = SceneColors.Walls.argb(),
        speaker = SceneColors.Speaker.argb(),
    )
    val labels = RoomSceneLabels(
        speaker = stringResource(R.string.results_3d_label_speaker),
        back = stringResource(R.string.results_3d_label_back),
    )
    val geometry = remember(room, clarity, labels) { ClarityRoomScene.build(room, clarity, colours, labels) { it.color().argb() } }
    val home = remember(room) { OrbitCamera.homeView(room.widthM, room.lengthM, room.heightM) }
    FramedScene3D(geometry, home, description, modifier)
}
