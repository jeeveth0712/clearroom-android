package com.clearroom.app.viz

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clearroom.app.R
import com.clearroom.app.acoustics.FloorPoint
import com.clearroom.app.acoustics.RoomClarity
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.Room
import com.clearroom.app.data.SeatId
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

/** One seat's bar: where it stands and how clear the seat is (mid-animation in V12, so not always a real result). */
data class SeatBar(val seat: SeatId, val sti: Double)

/**
 * V8, the 3D clarity room (doc "3D scenes" table): the room to scale as a cut-away model, a tile
 * on the floor for every seat with a bar on it whose height is the seat's STI and whose colour is
 * its band, and the speaker as a cone where they stand. V12 reuses it with animated bars.
 */
object ClarityRoomScene {
    /** STI 1.0 reaches 80 % of the room height: tall enough to compare at a glance, never through the ceiling. */
    fun barHeightM(sti: Double, roomHeightM: Double): Double = max(MIN_BAR_M, sti * BAR_SHARE_OF_HEIGHT * roomHeightM)

    fun build(room: Room, clarity: RoomClarity, colours: RoomSceneColours, labels: RoomSceneLabels, bandColour: (StiBand) -> Argb): SceneGeometry =
        build(room, clarity.seats.map { SeatBar(it.seat, it.sti) }, clarity.speaker, colours, labels, bandColour)

    fun build(
        room: Room,
        bars: List<SeatBar>,
        speakerSpot: FloorPoint,
        colours: RoomSceneColours,
        labels: RoomSceneLabels,
        bandColour: (StiBand) -> Argb,
    ): SceneGeometry {
        val walls = Primitives.wallsFacingIn(room.widthM, room.lengthM, room.heightM, colours.walls)
        val floor = Primitives.floor(room.widthM, room.lengthM, colours.floor)
        val spots = bars.map { SeatLayout.position(room, it.seat) }
        val tileHalf = tileWidthM(room) / 2
        val tiles = spots.map { Primitives.floorRect(it.xM - tileHalf, it.yM - tileHalf, it.xM + tileHalf, it.yM + tileHalf, colours.seatTile) }
        val barHalf = barWidthM(room) / 2
        val solids = bars.zip(spots) { bar, spot ->
            val sti = bar.sti.coerceIn(0.0, 1.0)
            Solid(
                Primitives.box(
                    min = Vec3(spot.xM - barHalf, 0.0, spot.yM - barHalf),
                    max = Vec3(spot.xM + barHalf, barHeightM(sti, room.heightM), spot.yM + barHalf),
                    color = bandColour(StiBand.from(sti)),
                ),
            )
        }
        val speaker = Solid(Primitives.cone(Vec3(speakerSpot.xM, 0.0, speakerSpot.yM), SPEAKER_RADIUS_M, SPEAKER_HEIGHT_M, SPEAKER_SIDES, colours.speaker))
        return SceneGeometry(
            ground = walls + floor + tiles,
            solids = solids + speaker,
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
    val labels = roomSceneLabels()
    val geometry = remember(room, clarity, labels) { ClarityRoomScene.build(room, clarity, ROOM_COLOURS, labels) { it.color().argb() } }
    val home = remember(room) { OrbitCamera.homeView(room.widthM, room.lengthM, room.heightM) }
    FramedScene3D(geometry, home, description, modifier)
}

/**
 * V12, the what-if morph: V8's room whose bars glide to [clarity]'s scores whenever it changes, so a
 * fix is seen as bars rising out of the red. A bar changes colour as it crosses a band line.
 */
@Composable
fun ClarityMorph3D(room: Room, clarity: RoomClarity, description: String, modifier: Modifier = Modifier) {
    val labels = roomSceneLabels()
    val home = remember(room) { OrbitCamera.homeView(room.widthM, room.lengthM, room.heightM) }
    // One animation per seat, in seat order; keyed so a different grid starts afresh.
    key(room.id, clarity.seats.size) {
        val animated = clarity.seats.map { seat -> animateFloatAsState(seat.sti.toFloat(), tween(MORPH_MS), label = "seat ${seat.seat}") }
        val bars = clarity.seats.mapIndexed { i, seat -> SeatBar(seat.seat, animated[i].value.toDouble()) }
        val geometry = remember(room, bars, clarity.speaker, labels) { ClarityRoomScene.build(room, bars, clarity.speaker, ROOM_COLOURS, labels) { it.color().argb() } }
        FramedScene3D(geometry, home, description, modifier)
    }
}

@Composable
private fun roomSceneLabels() = RoomSceneLabels(
    speaker = stringResource(R.string.results_3d_label_speaker),
    back = stringResource(R.string.results_3d_label_back),
)

private val ROOM_COLOURS = RoomSceneColours(
    floor = SceneColors.Floor.argb(),
    seatTile = SceneColors.SeatTile.argb(),
    walls = SceneColors.Walls.argb(),
    speaker = SceneColors.Speaker.argb(),
)

private const val MORPH_MS = 700
