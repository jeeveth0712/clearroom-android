---
name: compose-3d-scene
description: Maintain ClearRoom's own Compose Canvas 3D renderer and five 3D views, including camera math, gestures, geometry, picking, rendering order, and smooth what-if morphing.
---

# ClearRoom 3D scenes

The app already uses its own renderer in `viz/Scene3D.kt` and `viz/math3d/`. The approved classroom scene should remain recognisable while surrounding UI evolves. No WebView or three.js is present.

- Preserve the cut-away rendering order: room floor and inward-facing walls first, solids such as clarity bars and speaker next, loose mesh faces next, then lines and labels. The ground layer prevents walls and floors from hiding bars.
- The room and seat geometry is to scale. Clarity bars use fixed STI band colours; a speaker cone and front/back labels anchor the scene. V9 terrain, V10 waterfall, V11 noise landscape, and V12 morph reuse the same renderer.
- One-finger sideways drag rotates; a vertical single-finger move scrolls the page; two fingers zoom and tilt; double-tap resets. Preserve accessibility summaries for front/back rows and tap handling where the view supports picking.
- Build geometry when model inputs change rather than for every frame. Keep face-order, projection, camera-limit, and non-finite-coordinate tests around renderer changes. Check interaction and text legibility on the phone.

If an alternative renderer is proposed, first compare it with the current offline implementation, privacy rule, APK size cap, and the owner's request; do not introduce a network dependency by default.
