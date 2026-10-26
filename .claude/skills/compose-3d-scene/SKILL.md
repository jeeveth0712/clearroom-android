---
name: compose-3d-scene
description: How to build ClearRoom's own small 3D renderer (Scene3D) on Jetpack Compose Canvas, plus the five 3D views. Covers the orbit camera, view and perspective projection maths, back-face culling, near-plane rejection, painter's depth sort without boxing, flat Lambert shading, drag-to-orbit, pinch-to-zoom and double-tap reset, tap picking, bar/box/mesh primitives, contour lines, and morph animation, for the views V8 3D clarity room, V9 clarity terrain, V10 echo waterfall, V11 noise landscape, V12 what-if morph. Also covers the hardened WebView + three.js fallback. Use it for anything in viz/Scene3D.kt or the 3D views, for jank or flicker in a 3D scene, or for faces drawn in the wrong order, even if the user only says "the 3D room" or "make it rotate".
---

# Scene3D: a small 3D renderer on Compose Canvas

The doc budgets about 250 lines for `Scene3D.kt`: camera, projection, gestures, painter's sort, flat shading. That's enough for bars, boxes and height meshes, which is all V8–V12 need. Keep the maths in plain Kotlin (no Compose imports) in `viz/math3d/`, so it can be unit-tested on the JVM.

## Coordinates

World: metres, right-handed, **y up**. x runs across the room width, z runs along the room length (0 = front wall, where the speaker is). Seats sit on y = 0, and bar height goes along +y. Pick this once. Mixing "z up" in one view and "y up" in another is the usual reason a scene appears lying on its side.

## Camera and projection

```kotlin
class OrbitCamera(val target: Vec3, var yawRad: Float, var pitchRad: Float, var distanceM: Float) {
    fun eye() = target + Vec3(
        distanceM * cos(pitchRad) * sin(yawRad),
        distanceM * sin(pitchRad),
        distanceM * cos(pitchRad) * cos(yawRad),
    )
}
```

- View matrix: `lookAt(eye, target, up = (0, 1, 0))`. Store matrices as `FloatArray(16)` in one documented order (column-major, like `android.opengl.Matrix`). A transposed matrix produces a mirrored or exploded scene.
- Perspective: `f = 1 / tan(fovY / 2)`, with fovY about 45°. For a view-space point (x, y, z) where z < 0 is in front:
  `ndcX = (f / aspect) * x / -z`, `ndcY = f * y / -z`, `screenX = (ndcX + 1) / 2 * width`, `screenY = (1 - ndcY) / 2 * height`.
- Clamp pitch to about 5°–85°, so the user can't flip under the floor or reach the gimbal pole where `lookAt` breaks. Clamp distance between 0.5× and 4× the room diagonal.
- Reset (double-tap) returns to a fixed "teacher's view": behind and above the back row, looking at the speaker.

## Per-frame pipeline

1. Transform each vertex to view space, into a preallocated `FloatArray`.
2. **Near-plane rejection:** skip a face if any vertex has z > -near (near = 0.05 m). There's no clipping, so faces through the camera are dropped instead of exploding across the screen.
3. **Back-face culling** for closed solids (bars, boxes): with counter-clockwise winding seen from outside, normal n = (v1 - v0) × (v2 - v0); the face is visible if n · (-v0) > 0. Don't cull meshes (V9, V10, V11): both sides can be visible.
4. **Depth key** = mean view-space z of the face.
5. **Painter's sort:** draw far to near. Avoid boxing: pack each face into a `Long` and sort a `LongArray` (a primitive sort, no allocation):
   `key = (floatToRawIntBits(-zMean).toLong() shl 32) or faceIndex.toLong()`. For positive floats the bit pattern sorts like the value. Sort ascending for near-first, then iterate from the end for far-first.
6. **Shading:** flat Lambert with a light fixed in **world** space: `shade = 0.35 + 0.65 * max(0, n̂_world · L̂)`. Because both the normal and the light are fixed in world space, compute each face's shade once when the geometry is built, not per frame. Multiply the base colour's RGB by the shade.
7. **Draw:** reuse one `Path`: `reset()`, `moveTo`/`lineTo`, `close()`, `drawPath(path, color)`. Add a thin darker outline on bars to make edges readable.

Painter's sorting fails on long faces that overlap in depth. Keep mesh cells small and sort per face, not per object. For these scenes that's enough. Don't add a z-buffer.

## Compose integration

```kotlin
@Composable
fun Scene3D(scene: SceneGeometry, modifier: Modifier = Modifier, onPick: (Int) -> Unit = {}) {
    val camera = remember(scene.bounds) { CameraState.teacherView(scene.bounds) }   // mutableFloatStateOf inside
    Canvas(
        modifier
            .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> camera.orbit(pan); camera.zoom(zoom) } }
            .pointerInput(scene) { detectTapGestures(onDoubleTap = { camera.reset() }, onTap = { onPick(scene.pick(it, camera)) }) }
    ) {
        renderScene(scene, camera.snapshot(), size)   // camera is read only here
    }
}
```

- Read the camera state **only inside the draw lambda**. Compose then redraws on drag without recomposing anything, which is what makes rotation smooth.
- Build `SceneGeometry` (vertices, faces, base colours, precomputed shades) in `remember(model)` or in the mapper, never per frame.
- Budget: up to about 5,000 faces stays smooth on a mid-range phone with this approach. V9 on a 0.25 m grid in a 10 × 8 m room is 1,280 cells. If a mesh is larger, downsample it. If profiling still shows it slow, draw the mesh with one `nativeCanvas.drawVertices(...)` call using per-vertex colours.
- Picking (tap a bar → seat detail): test the tap point against the projected top face of each bar, nearest first. Reuse the same projection code as rendering.
- Labels: project 3D anchor points and draw them with `drawText` after the geometry. Skip labels whose anchor is behind the camera.

## The five views

| View | Geometry | Encoding | Notes |
| --- | --- | --- | --- |
| V8 3D clarity room | Room as 12 wireframe edges plus a filled floor quad; one box bar per seat; a speaker cone (8-sided pyramid) at the front | Bar height = STI × (room height / 2); colour = STI band (shared `StiColors`) | The hero view. Add a translucent 0.60 plane only if it stays readable |
| V9 Clarity terrain | Height mesh over the floor: z = STI on a 0.25 m grid | Colour per cell by band; contour at 0.60 | Contour: marching squares on the grid. Draw it last, lifted 1 cm, so it's always on top |
| V10 Echo waterfall | Mesh: x = time 0–2 s, y-axis rows = 7 octave bands, height = level dB (-60 to 0) | Colour by level (sequential ramp, not STI colours) | Input is the downsampled 7 × 200 matrix from `acoustics-dsp`. Don't redo DSP here |
| V11 Noise landscape | Bars: hour (or minute) × band, height = noise dB | Sequential ramp by level | Normalise to the log's own min/max and show the dB range in a label |
| V12 What-if morph | V8's geometry | `t` from `animateFloatAsState`: height = lerp(before, after, t); colour = band of the interpolated STI | Bars change colour as they cross a band threshold, so the user sees seats "leave the red". Fallback (build plan): static before/after |

## Testing the maths (JVM)

- The target projects to the screen centre.
- A point straight above the target projects above the centre (y-flip check).
- Of two points on the view axis, the nearer one sorts later (drawn on top).
- `lookAt` at pitch 85° has no NaNs.
- The packed-long sort gives the same order as a reference `sortedBy`.

## Fallback: WebView + bundled three.js (only if Scene3D looks rough after Day 1)

The doc allows this. It adds a JavaScript bridge, which is attack surface, so harden it:

- Load the page with `WebViewAssetLoader` (androidx.webkit) from `https://appassets.androidplatform.net/assets/...`, not `file://`. Set `allowFileAccess = false` and `allowContentAccess = false`.
- Ship `three.min.js` in `assets/` at a pinned version, and load nothing from the network. The app has no INTERNET permission anyway.
- Pass data in with `evaluateJavascript("render(${json})")`, where `json` comes from `JSONObject.toString()`, never from string concatenation of user text (a room name could contain quotes).
- Prefer no `addJavascriptInterface` at all. If picking needs a callback, expose one method that accepts only an integer seat index, and validate it.
