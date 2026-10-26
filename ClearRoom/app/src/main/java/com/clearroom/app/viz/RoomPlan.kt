package com.clearroom.app.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clearroom.app.R
import com.clearroom.app.acoustics.FloorPoint
import com.clearroom.app.acoustics.SeatLayout
import com.clearroom.app.data.Room
import com.clearroom.app.ui.formatMetres
import kotlin.math.max
import kotlin.math.min

/**
 * Metres → pixels for a floor plan: one uniform scale so the room keeps its true shape,
 * centred in the canvas, front wall at the top.
 */
data class PlanTransform(val pxPerM: Float, val offsetX: Float, val offsetY: Float) {
    fun toPx(point: FloorPoint) = Offset(offsetX + point.xM.toFloat() * pxPerM, offsetY + point.yM.toFloat() * pxPerM)

    companion object {
        fun fit(widthM: Double, lengthM: Double, canvas: Size, paddingPx: Float): PlanTransform {
            require(widthM > 0 && lengthM > 0) { "room must have a size: $widthM x $lengthM" }
            val usableW = max(canvas.width - 2 * paddingPx, 1f)
            val usableH = max(canvas.height - 2 * paddingPx, 1f)
            val pxPerM = min(usableW / widthM.toFloat(), usableH / lengthM.toFloat())
            return PlanTransform(
                pxPerM = pxPerM,
                offsetX = (canvas.width - widthM.toFloat() * pxPerM) / 2,
                offsetY = (canvas.height - lengthM.toFloat() * pxPerM) / 2,
            )
        }
    }
}

/** Room outline, speaker and seat grid, to scale. The base that the V1 seat heatmap colours in later. */
@Composable
fun RoomPlan(room: Room, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.room_plan_description,
        room.widthM.formatMetres(),
        room.lengthM.formatMetres(),
        pluralStringResource(R.plurals.room_plan_rows, room.rows, room.rows),
        pluralStringResource(R.plurals.room_plan_seats_per_row, room.cols, room.cols),
    )
    val outline = MaterialTheme.colorScheme.outline
    val speakerColour = MaterialTheme.colorScheme.primary
    val seatColour = MaterialTheme.colorScheme.secondary
    val seats = SeatLayout.seats(room)
    val speaker = SeatLayout.speaker(room)

    Canvas(modifier.fillMaxWidth().height(240.dp).semantics { contentDescription = description }) {
        val plan = PlanTransform.fit(room.widthM, room.lengthM, size, paddingPx = 8.dp.toPx())
        drawRect(
            color = outline,
            topLeft = plan.toPx(FloorPoint(0.0, 0.0)),
            size = Size(room.widthM.toFloat() * plan.pxPerM, room.lengthM.toFloat() * plan.pxPerM),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(speakerColour, radius = max(SPEAKER_RADIUS_M * plan.pxPerM, 5.dp.toPx()), center = plan.toPx(speaker))
        val seatPx = max(SEAT_SIZE_M * plan.pxPerM, 4.dp.toPx())
        seats.forEach { seat ->
            val centre = plan.toPx(seat.position)
            drawRect(seatColour, topLeft = Offset(centre.x - seatPx / 2, centre.y - seatPx / 2), size = Size(seatPx, seatPx))
        }
    }
}

private const val SPEAKER_RADIUS_M = 0.3f
private const val SEAT_SIZE_M = 0.45f
