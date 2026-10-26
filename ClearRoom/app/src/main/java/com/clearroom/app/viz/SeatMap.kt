package com.clearroom.app.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.clearroom.app.acoustics.Cause
import com.clearroom.app.acoustics.ClarityGrid
import com.clearroom.app.acoustics.FloorPoint
import com.clearroom.app.acoustics.RoomClarity
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.acoustics.StiBand
import com.clearroom.app.data.Room
import com.clearroom.app.data.SeatId
import com.clearroom.app.ui.theme.SceneColors
import com.clearroom.app.ui.theme.color
import kotlin.math.max
import kotlin.math.min

/** V1 colours the floor and seats by clarity; V2 adds each seat's main cause as an icon. */
enum class SeatMapMode { CLARITY, CAUSE }

/** What V1 and V2 draw: the room, each seat's result, and (V1) clarity across the whole floor. */
class SeatMapModel(val room: Room, val clarity: RoomClarity, val grid: ClarityGrid?)

/** Seats that are easy to follow or better (good or excellent), for the map's spoken summary. */
fun easySeatCount(clarity: RoomClarity): Int = clarity.seats.count { it.band >= StiBand.GOOD }

/** How many seats each cause limits, for V2's spoken summary. Every cause is listed, even at 0. */
fun causeCounts(clarity: RoomClarity): Map<Cause, Int> = Cause.entries.associateWith { cause -> clarity.seats.count { it.cause == cause } }

/** A seat's square on the plan: 0.6 m, smaller where seats are closer, never under [minPx]. */
fun seatSizePx(room: Room, pxPerM: Float, minPx: Float): Float =
    max(minPx, min(SEAT_M, SEAT_SHARE_OF_SPACING * SeatLayout.spacingM(room)).toFloat() * pxPerM)

/** The seat nearest [tap] if it's within [reachPx]: the same plan mapping as the drawing, reversed. */
fun hitSeat(room: Room, plan: PlanTransform, tap: Offset, reachPx: Float): SeatId? =
    SeatLayout.seats(room)
        .map { SeatId(it.row, it.col) to (plan.toPx(it.position) - tap).getDistance() }
        .filter { (_, distancePx) -> distancePx <= reachPx }
        .minByOrNull { (_, distancePx) -> distancePx }
        ?.first

@Composable
fun SeatMap(
    model: SeatMapModel,
    mode: SeatMapMode,
    selected: SeatId?,
    onSeatSelected: (SeatId) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val style = ChartStyle.fromTheme()
    val select by rememberUpdatedState(onSeatSelected)
    val aspect = (model.room.widthM / model.room.lengthM).toFloat().coerceIn(MIN_ASPECT, MAX_ASPECT)
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .pointerInput(model.room) {
                detectTapGestures { tap ->
                    val plan = PlanTransform.fit(model.room.widthM, model.room.lengthM, size.toSize(), PLAN_PADDING.toPx())
                    val reach = max(TAP_REACH.toPx(), seatSizePx(model.room, plan.pxPerM, MIN_SEAT.toPx()))
                    hitSeat(model.room, plan, tap, reach)?.let(select)
                }
            }
            .semantics { contentDescription = description },
    ) {
        drawSeatMap(model, mode, selected, style)
    }
}

fun DrawScope.drawSeatMap(model: SeatMapModel, mode: SeatMapMode, selected: SeatId?, style: ChartStyle) {
    val room = model.room
    val plan = PlanTransform.fit(room.widthM, room.lengthM, size, PLAN_PADDING.toPx())
    val origin = plan.toPx(FloorPoint(0.0, 0.0))
    val floorSize = Size(room.widthM.toFloat() * plan.pxPerM, room.lengthM.toFloat() * plan.pxPerM)
    drawRect(SceneColors.Floor, origin, floorSize)
    if (mode == SeatMapMode.CLARITY) model.grid?.let { drawClarityCells(it, plan) }
    drawRect(style.axis, origin, floorSize, style = Stroke(1.5.dp.toPx()))
    drawCircle(SceneColors.Speaker, radius = max(SPEAKER_M * plan.pxPerM, 6.dp.toPx()), center = plan.toPx(model.clarity.speaker))

    val seatPx = seatSizePx(room, plan.pxPerM, MIN_SEAT.toPx())
    val corner = CornerRadius(seatPx * 0.2f)
    val square = Size(seatPx, seatPx)
    model.clarity.seats.forEach { seat ->
        val centre = plan.toPx(SeatLayout.position(room, seat.seat))
        val topLeft = centre - Offset(seatPx / 2, seatPx / 2)
        drawRoundRect(seat.band.color(), topLeft, square, corner)
        drawRoundRect(Color.White, topLeft, square, corner, style = Stroke(1.dp.toPx()))
        when {
            mode == SeatMapMode.CAUSE -> drawCauseBadge(seat.cause, centre, seatPx * 0.42f)
            seat.isMeasured -> {
                drawCircle(Color.White, radius = seatPx * 0.14f, center = centre)
                drawCircle(SceneColors.Speaker, radius = seatPx * 0.14f, center = centre, style = Stroke(1.dp.toPx()))
            }
        }
        if (seat.seat == selected) {
            val ring = 3.dp.toPx()
            drawRoundRect(style.axis, topLeft - Offset(ring, ring), Size(seatPx + 2 * ring, seatPx + 2 * ring), CornerRadius(seatPx * 0.25f), style = Stroke(2.5.dp.toPx()))
        }
    }
}

/** STI across the floor as faint cells, so the colour flows between seats. */
private fun DrawScope.drawClarityCells(grid: ClarityGrid, plan: PlanTransform) {
    // Solid colours (not see-through), so the half-pixel overlap that hides seams never shows as a grid.
    val cell = Size(grid.cellWidthM.toFloat() * plan.pxPerM + 0.5f, grid.cellLengthM.toFloat() * plan.pxPerM + 0.5f)
    for (j in 0 until grid.rows) {
        for (i in 0 until grid.columns) {
            val colour = lerp(SceneColors.Floor, StiBand.from(grid.cell(i, j).coerceIn(0.0, 1.0)).color(), CELL_STRENGTH)
            drawRect(colour, plan.toPx(grid.cornerPoint(i, j)), cell)
        }
    }
}

/** A white disc with the cause drawn on it in dark grey, readable on all four band colours. */
fun DrawScope.drawCauseBadge(cause: Cause, centre: Offset, radius: Float) {
    drawCircle(Color.White, radius, centre)
    drawCauseGlyph(cause, centre, radius * 0.8f, SceneColors.Speaker)
}

/** Echo: sound waves. Noise: a jagged line. Distance: a two-headed arrow. */
fun DrawScope.drawCauseGlyph(cause: Cause, centre: Offset, radius: Float, colour: Color) {
    val stroke = Stroke(width = max(radius * 0.2f, 1.5f), cap = StrokeCap.Round)
    when (cause) {
        Cause.ECHO -> {
            val source = centre - Offset(radius * 0.7f, 0f)
            for (k in 1..3) {
                val r = radius * 0.45f * k
                drawArc(colour, startAngle = -40f, sweepAngle = 80f, useCenter = false, topLeft = source - Offset(r, r), size = Size(2 * r, 2 * r), style = stroke)
            }
        }
        Cause.NOISE -> {
            val path = Path()
            val xs = listOf(-0.8f, -0.4f, 0f, 0.4f, 0.8f)
            xs.forEachIndexed { k, x ->
                val y = if (k % 2 == 0) 0.35f else -0.35f
                val point = centre + Offset(x * radius, y * radius)
                if (k == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, colour, style = stroke)
        }
        Cause.DISTANCE -> {
            val left = centre - Offset(radius * 0.8f, 0f)
            val right = centre + Offset(radius * 0.8f, 0f)
            val head = radius * 0.35f
            drawLine(colour, left, right, stroke.width, StrokeCap.Round)
            drawLine(colour, left, left + Offset(head, -head), stroke.width, StrokeCap.Round)
            drawLine(colour, left, left + Offset(head, head), stroke.width, StrokeCap.Round)
            drawLine(colour, right, right + Offset(-head, -head), stroke.width, StrokeCap.Round)
            drawLine(colour, right, right + Offset(-head, head), stroke.width, StrokeCap.Round)
        }
    }
}

private const val SEAT_M = 0.6
private const val SEAT_SHARE_OF_SPACING = 0.75
private const val SPEAKER_M = 0.3f
private const val CELL_STRENGTH = 0.5f
private const val MIN_ASPECT = 0.7f
private const val MAX_ASPECT = 1.6f
private val PLAN_PADDING = 8.dp
private val MIN_SEAT = 12.dp
private val TAP_REACH = 24.dp
