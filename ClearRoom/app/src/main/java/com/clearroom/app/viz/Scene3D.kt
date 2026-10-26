package com.clearroom.app.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.clearroom.app.viz.math3d.OrbitCamera
import com.clearroom.app.viz.math3d.SceneGeometry
import com.clearroom.app.viz.math3d.ScenePass
import com.clearroom.app.viz.math3d.ScreenFace
import com.clearroom.app.viz.math3d.ScreenLabel

/** Drag across the full width ≈ a half turn. */
private const val RADIANS_PER_PIXEL = 0.006

/** Face edges are drawn in this shade of the face's own colour: crisp outlines without black lines. */
private const val EDGE_SHADE = 0.78f

/**
 * The small 3D renderer (doc "Tech stack": Scene3D on Compose Canvas). One finger orbits, two
 * fingers pinch to zoom, double-tap returns to [home]. The camera lives in Compose state but is
 * read only inside the draw step, so turning the scene redraws without recomposing anything.
 */
@Composable
fun Scene3D(
    geometry: SceneGeometry,
    home: OrbitCamera,
    description: String,
    modifier: Modifier = Modifier,
    labelText: Color = MaterialTheme.colorScheme.onSurface,
    labelBackground: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
) {
    var camera by remember(home) { mutableStateOf(home) }
    val path = remember { Path() }
    val measurer = rememberTextMeasurer()
    val labels = LabelPaint(measurer, MaterialTheme.typography.labelMedium.copy(color = labelText), labelBackground)
    Canvas(
        modifier
            .pointerInput(home) {
                detectTransformGestures { _, pan, zoom, _ ->
                    camera = camera.orbitBy(-pan.x * RADIANS_PER_PIXEL, pan.y * RADIANS_PER_PIXEL).zoomBy(zoom.toDouble())
                }
            }
            .pointerInput(home) { detectTapGestures(onDoubleTap = { camera = home }) }
            .semantics { contentDescription = description },
    ) {
        drawScene(geometry, camera, path, labels)
    }
}

private class LabelPaint(val measurer: TextMeasurer, val style: TextStyle, val background: Color)

private fun DrawScope.drawScene(geometry: SceneGeometry, camera: OrbitCamera, path: Path, labels: LabelPaint) {
    val frame = ScenePass.project(geometry, camera, size.width, size.height)
    val edge = Stroke(width = 1.dp.toPx())
    frame.ground.forEach { drawFace(it, path, edge) }
    frame.faces.forEach { drawFace(it, path, edge) }
    val lineWidth = 1.5.dp.toPx()
    frame.lines.forEach { drawLine(Color(it.color.packed), Offset(it.x1, it.y1), Offset(it.x2, it.y2), lineWidth) }
    frame.labels.forEach { drawLabel(it, labels) }
}

private fun DrawScope.drawFace(face: ScreenFace, path: Path, edge: Stroke) {
    path.reset()
    path.moveTo(face.points[0], face.points[1])
    for (i in 2 until face.points.size step 2) path.lineTo(face.points[i], face.points[i + 1])
    path.close()
    drawPath(path, Color(face.color.packed))
    drawPath(path, Color(face.color.shaded(EDGE_SHADE).packed), style = edge)
}

/** The label's text centred on its point, on a rounded pill so it reads over any colour. */
private fun DrawScope.drawLabel(label: ScreenLabel, paint: LabelPaint) {
    val text = paint.measurer.measure(label.text, paint.style)
    val padding = Offset(6.dp.toPx(), 2.dp.toPx())
    val textTopLeft = Offset(label.x - text.size.width / 2f, label.y - text.size.height / 2f)
    drawRoundRect(
        color = paint.background,
        topLeft = textTopLeft - padding,
        size = Size(text.size.width + 2 * padding.x, text.size.height + 2 * padding.y),
        cornerRadius = CornerRadius(text.size.height / 2f + padding.y),
    )
    drawText(text, topLeft = textTopLeft)
}
