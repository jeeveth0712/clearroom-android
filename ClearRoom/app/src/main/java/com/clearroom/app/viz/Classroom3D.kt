package com.clearroom.app.viz

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.FloorPoint
import com.clearroom.app.acoustics.RoomClarity
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.Room
import com.clearroom.app.data.SeatId
import com.clearroom.app.ui.theme.ClassroomColors
import com.clearroom.app.ui.theme.ClearRoomTheme
import com.clearroom.app.ui.theme.color
import com.clearroom.app.viz.math3d.Argb
import com.clearroom.app.viz.math3d.Face3
import com.clearroom.app.viz.math3d.Label3
import com.clearroom.app.viz.math3d.OrbitCamera
import com.clearroom.app.viz.math3d.Primitives
import com.clearroom.app.viz.math3d.SceneGeometry
import com.clearroom.app.viz.math3d.Solid
import com.clearroom.app.viz.math3d.Vec3
import kotlin.math.floor
import kotlin.math.min

/** The classroom model's colours as the 3D maths takes them (see [ClassroomColors]). */
data class ClassroomPalette(
    val floor: Argb,
    val walls: Argb,
    val furniture: Argb,
    val teacherDesk: Argb,
    val board: Argb,
    val boardFrame: Argb,
    val window: Argb,
    val door: Argb,
    val speaker: Argb,
)

/** One seat's desk and the colour of its top: the seat's clarity band, or plain wood before measuring. */
data class SeatDesk(val seat: SeatId, val top: Argb)

/** The bubble over one desk: "0.57–0.58" over the back row's worst seat. */
data class DeskCallout(val seat: SeatId, val text: String)

/**
 * V8 as a classroom (owner's concept 03): the room to scale as a cut-away model with a whiteboard
 * on the front wall, windows on the left, a door at the front right and a teacher's desk; every
 * seat a desk and chair, the desk top in the seat's clarity colour; the speaker as a cone where
 * they stand. The Room tab shows the same room with plain desks.
 */
object ClassroomScene {

    fun build(
        room: Room,
        desks: List<SeatDesk>,
        speakerSpot: FloorPoint?,
        palette: ClassroomPalette,
        labels: RoomSceneLabels?,
        callout: DeskCallout? = null,
    ): SceneGeometry {
        val scale = furnitureScale(room)
        val simple = desks.size > MAX_DETAILED_SEATS
        val spots = desks.map { SeatLayout.position(room, it.seat) }
        val furniture = desks.zip(spots).flatMap { (desk, spot) ->
            if (simple) listOf(deskBlock(spot, scale, desk.top)) else deskAndChair(spot, scale, desk.top, palette.furniture)
        }
        val teacher = teacherDesk(room, palette.teacherDesk)?.let(::listOf).orEmpty()
        val speaker = speakerSpot?.let {
            Solid(Primitives.cone(Vec3(it.xM, 0.0, it.yM), SPEAKER_RADIUS_M, ClarityRoomScene.SPEAKER_HEIGHT_M, SPEAKER_SIDES, palette.speaker))
        }
        val calloutLabel = callout?.let { c ->
            val spot = SeatLayout.position(room, c.seat)
            val top = Vec3(spot.xM, DESK_TOP_M, deskCentreZ(spot, scale))
            Label3(top + Vec3(0.0, CALLOUT_LIFT_M, 0.0), c.text, pointsAt = top)
        }
        val sceneLabels = labels?.let {
            listOfNotNull(
                speakerSpot?.let { s -> Label3(Vec3(s.xM, ClarityRoomScene.SPEAKER_HEIGHT_M + LABEL_LIFT_M, s.yM), it.speaker) },
                Label3(Vec3(room.widthM / 2, 0.0, room.lengthM), it.back),
            )
        }.orEmpty()
        return SceneGeometry(
            ground = Primitives.wallsFacingIn(room.widthM, room.lengthM, room.heightM, palette.walls) +
                whiteboard(room, palette) + windows(room, palette.window) + door(room, palette.door) +
                Primitives.floor(room.widthM, room.lengthM, palette.floor),
            solids = furniture + teacher + listOfNotNull(speaker),
            labels = sceneLabels + listOfNotNull(calloutLabel),
        )
    }

    /**
     * Furniture is full size (a 0.62 m desk) where seats are at least 0.95 m apart, and shrinks
     * with the spacing below that, so neighbouring desks and chairs never touch.
     */
    fun furnitureScale(room: Room): Double = min(1.0, SeatLayout.spacingM(room) / FULL_SIZE_SPACING_M)

    /** Where a seat's desk stands: just in front of the seat (towards the speaker), the chair behind it. */
    fun deskCentreZ(spot: FloorPoint, scale: Double): Double = spot.yM - DESK_FORWARD_M * scale

    private fun deskAndChair(spot: FloorPoint, scale: Double, top: Argb, frame: Argb): List<Solid> {
        val (x, z) = spot.xM to deskCentreZ(spot, scale)
        val halfW = DESK_WIDTH_M * scale / 2
        val halfD = DESK_DEPTH_M * scale / 2
        val leg = LEG_M * scale
        val chairZ = spot.yM + CHAIR_BACK_M * scale
        val chairHalfW = CHAIR_WIDTH_M * scale / 2
        val chairHalfD = CHAIR_DEPTH_M * scale / 2
        val post = POST_M * scale / 2
        return listOf(
            box(x - halfW, DESK_TOP_M - TOP_THICKNESS_M, z - halfD, x + halfW, DESK_TOP_M, z + halfD, top),
            box(x - halfW, 0.0, z - halfD + leg, x - halfW + leg, DESK_TOP_M - TOP_THICKNESS_M, z + halfD - leg, frame),
            box(x + halfW - leg, 0.0, z - halfD + leg, x + halfW, DESK_TOP_M - TOP_THICKNESS_M, z + halfD - leg, frame),
            box(x - chairHalfW, SEAT_M - TOP_THICKNESS_M, chairZ - chairHalfD, x + chairHalfW, SEAT_M, chairZ + chairHalfD, frame),
            box(x - chairHalfW, SEAT_M, chairZ + chairHalfD - leg, x + chairHalfW, BACKREST_TOP_M, chairZ + chairHalfD, frame),
            box(x - post, 0.0, chairZ - post, x + post, SEAT_M - TOP_THICKNESS_M, chairZ + post, frame),
        )
    }

    /** Big rooms (hundreds of seats) get one block per desk, so turning the scene stays smooth. */
    private fun deskBlock(spot: FloorPoint, scale: Double, top: Argb): Solid {
        val (x, z) = spot.xM to deskCentreZ(spot, scale)
        val halfW = DESK_WIDTH_M * scale / 2
        val halfD = DESK_DEPTH_M * scale / 2
        return box(x - halfW, 0.0, z - halfD, x + halfW, DESK_TOP_M, z + halfD, top)
    }

    /** At the front left, clear of the speaker's spot; left out when the room is too narrow for it. */
    private fun teacherDesk(room: Room, colour: Argb): Solid? {
        val centreX = room.widthM / 2 - TEACHER_FROM_MIDDLE_M
        if (centreX - TEACHER_WIDTH_M / 2 < WALL_GAP_M) return null
        return box(
            centreX - TEACHER_WIDTH_M / 2, 0.0, TEACHER_FROM_FRONT_M,
            centreX + TEACHER_WIDTH_M / 2, TEACHER_HEIGHT_M, TEACHER_FROM_FRONT_M + TEACHER_DEPTH_M,
            colour,
        )
    }

    /** A whiteboard in a grey frame, centred on the front wall. */
    private fun whiteboard(room: Room, palette: ClassroomPalette): List<Face3> {
        val half = min(MAX_BOARD_WIDTH_M, room.widthM * BOARD_SHARE_OF_WIDTH) / 2
        val bottom = BOARD_BOTTOM_M
        val top = min(BOARD_TOP_M, room.heightM - BOARD_CEILING_GAP_M)
        val middle = room.widthM / 2
        return listOf(
            frontWallRect(middle - half - FRAME_M, bottom - FRAME_M, middle + half + FRAME_M, top + FRAME_M, ON_WALL_M / 2, palette.boardFrame),
            frontWallRect(middle - half, bottom, middle + half, top, ON_WALL_M, palette.board),
        )
    }

    /** Evenly spaced windows along the left wall, one per 2.5 m of wall, at most four. */
    fun windowCount(room: Room): Int = floor(room.lengthM / WALL_PER_WINDOW_M).toInt().coerceIn(1, MAX_WINDOWS)

    private fun windows(room: Room, colour: Argb): List<Face3> {
        val count = windowCount(room)
        val width = min(WINDOW_WIDTH_M, room.lengthM / (2 * count))
        val top = min(WINDOW_TOP_M, room.heightM - WINDOW_CEILING_GAP_M)
        return List(count) { i ->
            val centre = room.lengthM * (i + 0.5) / count
            val (z0, z1) = centre - width / 2 to centre + width / 2
            // On the left wall (x = 0), facing into the room (+x).
            Face3(listOf(Vec3(ON_WALL_M, WINDOW_BOTTOM_M, z0), Vec3(ON_WALL_M, top, z0), Vec3(ON_WALL_M, top, z1), Vec3(ON_WALL_M, WINDOW_BOTTOM_M, z1)), colour, closedSolid = true)
        }
    }

    /** A door near the front of the right wall (x = width), facing into the room (−x). */
    private fun door(room: Room, colour: Argb): Face3 {
        val x = room.widthM - ON_WALL_M
        val top = min(DOOR_HEIGHT_M, room.heightM - ON_WALL_M)
        val (z0, z1) = DOOR_FROM_FRONT_M to DOOR_FROM_FRONT_M + DOOR_WIDTH_M
        return Face3(listOf(Vec3(x, 0.0, z0), Vec3(x, 0.0, z1), Vec3(x, top, z1), Vec3(x, top, z0)), colour, closedSolid = true)
    }

    /** A rectangle just in front of the front wall (z = 0), facing into the room (+z). */
    private fun frontWallRect(x0: Double, y0: Double, x1: Double, y1: Double, z: Double, colour: Argb) =
        Face3(listOf(Vec3(x0, y0, z), Vec3(x1, y0, z), Vec3(x1, y1, z), Vec3(x0, y1, z)), colour, closedSolid = true)

    private fun box(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double, colour: Argb) =
        Solid(Primitives.box(Vec3(x0, y0, z0), Vec3(x1, y1, z1), colour))

    /** Above this many seats each desk is one block, with no legs or chair. */
    const val MAX_DETAILED_SEATS = 120
    const val DESK_TOP_M = 0.74
    const val DESK_WIDTH_M = 0.62
    const val DESK_DEPTH_M = 0.42
    private const val FULL_SIZE_SPACING_M = 0.95
    private const val DESK_FORWARD_M = 0.08
    private const val TOP_THICKNESS_M = 0.04
    private const val LEG_M = 0.03
    private const val SEAT_M = 0.46
    private const val BACKREST_TOP_M = 0.78
    private const val CHAIR_WIDTH_M = 0.40
    private const val CHAIR_DEPTH_M = 0.32
    private const val CHAIR_BACK_M = 0.31
    private const val POST_M = 0.05
    private const val TEACHER_WIDTH_M = 1.2
    private const val TEACHER_DEPTH_M = 0.6
    private const val TEACHER_HEIGHT_M = 0.75
    private const val TEACHER_FROM_FRONT_M = 0.25
    private const val TEACHER_FROM_MIDDLE_M = 1.6
    private const val WALL_GAP_M = 0.2
    private const val MAX_BOARD_WIDTH_M = 3.0
    private const val BOARD_SHARE_OF_WIDTH = 0.5
    private const val BOARD_BOTTOM_M = 0.9
    private const val BOARD_TOP_M = 2.1
    private const val BOARD_CEILING_GAP_M = 0.3
    private const val FRAME_M = 0.06
    private const val ON_WALL_M = 0.02
    private const val WALL_PER_WINDOW_M = 2.5
    private const val MAX_WINDOWS = 4
    private const val WINDOW_WIDTH_M = 1.2
    private const val WINDOW_BOTTOM_M = 1.0
    private const val WINDOW_TOP_M = 2.2
    private const val WINDOW_CEILING_GAP_M = 0.4
    private const val DOOR_FROM_FRONT_M = 0.3
    private const val DOOR_WIDTH_M = 0.9
    private const val DOOR_HEIGHT_M = 2.1
    private const val SPEAKER_RADIUS_M = 0.3
    private const val SPEAKER_SIDES = 8
    private const val LABEL_LIFT_M = 0.5
    private const val CALLOUT_LIFT_M = 0.9
}

/**
 * V8 on the Results tab: the classroom with every desk top in its seat's clarity colour, and the
 * [callout] bubble over one desk (the back row's worst seat).
 */
@Composable
fun ClassroomClarity3D(room: Room, clarity: RoomClarity, callout: DeskCallout?, description: String, modifier: Modifier = Modifier) {
    val labels = RoomSceneLabels(stringResource(R.string.results_3d_label_speaker), stringResource(R.string.results_3d_label_back))
    val palette = classroomPalette(ClearRoomTheme.classroom)
    val geometry = remember(room, clarity, callout, labels, palette) {
        val desks = clarity.seats.map { SeatDesk(it.seat, StiBand.from(it.sti.coerceIn(0.0, 1.0)).color().argb()) }
        ClassroomScene.build(room, desks, clarity.speaker, palette, labels, callout)
    }
    val home = remember(room) { OrbitCamera.homeView(room.widthM, room.lengthM, room.heightM) }
    FramedScene3D(geometry, home, description, modifier)
}

/** The Room tab's picture of the room: the classroom with plain desks, small enough to sit beside the room's name. */
@Composable
fun ClassroomPicture3D(room: Room, description: String, modifier: Modifier = Modifier) {
    val colours = ClearRoomTheme.classroom
    val palette = classroomPalette(colours)
    val desk = colours.desk.argb()
    val geometry = remember(room, palette, desk) {
        ClassroomScene.build(room, SeatLayout.seats(room).map { SeatDesk(SeatId(it.row, it.col), desk) }, speakerSpot = null, palette, labels = null)
    }
    val home = remember(room) { OrbitCamera.homeView(room.widthM, room.lengthM, room.heightM) }
    Scene3D(geometry, home, description, modifier.aspectRatio(PICTURE_ASPECT).clip(RoundedCornerShape(16.dp)))
}

private fun classroomPalette(colours: ClassroomColors) = ClassroomPalette(
    floor = colours.floor.argb(),
    walls = colours.walls.argb(),
    furniture = colours.furniture.argb(),
    teacherDesk = colours.teacherDesk.argb(),
    board = colours.board.argb(),
    boardFrame = colours.boardFrame.argb(),
    window = colours.window.argb(),
    door = colours.door.argb(),
    speaker = colours.speaker.argb(),
)

private const val PICTURE_ASPECT = 1.2f
