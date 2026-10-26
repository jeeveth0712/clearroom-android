package com.clearroom.app.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clearroom.app.viz.math3d.OrbitCamera
import com.clearroom.app.viz.math3d.SceneGeometry
import com.clearroom.app.viz.math3d.ScenePass

/** Drag across the full width ≈ a half turn. */
private const val RADIANS_PER_PIXEL = 0.006

/**
 * The small 3D renderer (doc "Tech stack": Scene3D on Compose Canvas). One finger orbits, two
 * fingers pinch to zoom, double-tap returns to [home]. The camera lives in Compose state but is
 * read only inside the draw step, so turning the scene redraws without recomposing anything.
 */
@Composable
fun Scene3D(geometry: SceneGeometry, home: OrbitCamera, description: String, modifier: Modifier = Modifier) {
    var camera by remember(home) { mutableStateOf(home) }
    val path = remember { Path() }
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
        drawScene(geometry, camera, path)
    }
}

private fun DrawScope.drawScene(geometry: SceneGeometry, camera: OrbitCamera, path: Path) {
    val frame = ScenePass.project(geometry, camera, size.width, size.height)
    val lineWidth = 1.5.dp.toPx()
    // Room edges first: bars and the speaker are painted over them.
    frame.lines.forEach { drawLine(Color(it.color.packed), Offset(it.x1, it.y1), Offset(it.x2, it.y2), lineWidth) }
    val outline = Stroke(width = 0.75.dp.toPx())
    frame.faces.forEach { face ->
        path.reset()
        path.moveTo(face.points[0], face.points[1])
        for (i in 2 until face.points.size step 2) path.lineTo(face.points[i], face.points[i + 1])
        path.close()
        drawPath(path, Color(face.color.packed))
        drawPath(path, OUTLINE, style = outline) // a thin dark edge keeps neighbouring faces readable
    }
}

private val OUTLINE = Color(0x40000000)
