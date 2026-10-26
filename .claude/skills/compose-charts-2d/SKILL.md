---
name: compose-charts-2d
description: How to build ClearRoom's hand-drawn 2D charts on Jetpack Compose Canvas, with no chart library. Covers the shared axis helper (nice ticks, linear and log scales), the chart-model / DrawScope-function / composable split that lets the same drawing go into the PDF, the fixed STI band colours, text with TextMeasurer, tap hit-testing, plain-language captions, uncertainty whiskers, empty states, and per-view specs for V1-V7 and V13-V15 (seat heatmap, cause map, decay curve, RT60 bars, day timeline, fix ranking, required voice, MTF curves, speech vs distance, octave spectrum). Use it whenever writing or fixing anything in viz/ that is 2D, any Canvas drawing, chart colours, axes or legends in ClearRoom, even if the user just says "the graph" or "the heatmap".
---

# Compose Canvas charts (2D)

The doc picks hand-drawn Canvas charts for full control and no library version risk. The cost is that each chart rebuilds axes, text and hit-testing. The structure below keeps that cost paid once.

## Three layers per chart

```kotlin
// 1. Model: pure Kotlin, built outside composition, unit-testable, no Compose types.
data class Rt60ChartModel(
    val bandsHz: List<Int>, val meanS: List<Double>, val sdS: List<Double>,
    val targetS: Double = 0.6, val caption: String,          // NFR-2 one-sentence explanation
)

// 2. Drawing: an extension on DrawScope, so the UI and the PDF report call the same code.
fun DrawScope.drawRt60Chart(model: Rt60ChartModel, style: ChartStyle, text: TextMeasurer) { ... }

// 3. Composable: thin wrapper; caption + semantics + gestures.
@Composable
fun Rt60Chart(model: Rt60ChartModel, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val style = ChartStyle.fromTheme(MaterialTheme.colorScheme)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = model.caption }) {
            drawRt60Chart(model, style, text)
        }
        Text(model.caption, style = MaterialTheme.typography.bodySmall)
    }
}
```

- Build models in the ViewModel or a mapper (`ResultsMapper.toRt60Model(session)`), never inside the composable body. Then the math is tested, and recomposition only redraws.
- `ChartStyle` holds axis, grid and label colours from `MaterialTheme.colorScheme` (`onSurface`, `outlineVariant`), so light and dark mode work for free. Data colours don't come from the theme (next section).
- Because drawing is a `DrawScope` function, `report/PdfReport.kt` can draw it straight onto the PDF page (see `storage-and-pdf-report`).

## Colours

- **STI bands are fixed semantic colours**, defined once in `ui/theme/StiColors.kt` and used by 2D, 3D and the PDF. Use an ordered, colour-blind-safe red → blue ramp: poor `#D7301F`, fair `#FC8D59`, good `#91BFDB`, excellent `#4575B4`. "A red seat is red everywhere" (doc). Red to blue works for red-green colour blindness, where red to green does not.
- Never use colour alone. Seat cells also carry the band's text on tap, V2 uses icons or hatching per cause, and the legend shows band names.
- The 7 octave bands in V13 use a separate categorical palette that never reuses the STI band colours, so "red" can't mean both "poor" and "125 Hz".
- For deeper colour or form questions, the global `dataviz` skill has a palette validator and form heuristics.

## Shared axis helper (`viz/Axis.kt`)

```kotlin
class Axis(val min: Double, val max: Double, val log: Boolean = false) {
    fun toPx(value: Double, startPx: Float, endPx: Float): Float { ... }  // linear or log10
    fun ticks(maxCount: Int = 5): List<Double> { ... }                    // 1-2-5 "nice" steps; decades if log
}
```

- Nice ticks: step = 10^floor(log10(range / maxCount)) × the first of {1, 2, 5, 10} that gives at most maxCount ticks.
- V13's x axis is logarithmic (0.63–12.5 Hz). Label 0.63, 1, 2, 4, 8, 12.5 instead of decade ticks only.
- Write unit tests for `toPx` (both ends map to the edges) and `ticks` (round numbers, inside the range). Off-by-one axis bugs are the most common chart bug.
- Insets: reserve left and bottom margins from the measured label widths (`TextMeasurer.measure`), not fixed dp values, or long labels get clipped on small phones.

## Text, gestures and performance

- `drawText(textMeasurer, text, topLeft, style)` inside DrawScope. With many labels, measure once in `remember(model) { ... }` or `Modifier.drawWithCache`, not on every frame.
- Tap a seat: `Modifier.pointerInput(model) { detectTapGestures { offset -> onSeatTapped(hitTest(offset)) } }`, where `hitTest` reverses the same plan-to-pixel mapping used for drawing. Make seat cells at least 48 dp, or use the nearest seat within 24 dp.
- Don't allocate `Path`s or lists inside the draw lambda for static geometry. Use `drawWithCache { val path = ...; onDrawBehind { drawPath(path, ...) } }`.
- Every chart has an empty state ("No day log yet — start one in the Log tab") instead of drawing broken axes or crashing on an empty list.

## Room plan scaling (V1, V2)

Fit the room's L × W metres into the canvas with one uniform scale `s = min(w / W, h / L)` and centre it, so the aspect ratio is true and the room isn't stretched. Put the speaker at the top or bottom edge and keep it the same in every plan view.

## View specs

| View | Marks and encoding | Annotations | Watch out for |
| --- | --- | --- | --- |
| V1 Seat clarity heatmap | Model STI evaluated on a 0.25 m grid as fine cells, seats drawn on top as rounded rects in band colours | Speaker marker; band legend; tap → seat sheet | Use the same grid and colour scale for before/after (FR-13) |
| V2 Limiting-cause map | Same plan; one icon per zone (echo, noise, distance), hatch for low confidence | Short legend: "Echo → curtains, Noise → fans, Distance → move closer or use a mic" | Icons need contrast on all 4 band colours |
| V3 Decay curve with fit | Schroeder curve line (dB vs s), fit line extended to -60 dB, shaded -5 to -25 dB fit range, noise-floor line | Band selector chips; T20 value and ξ | Clip the y axis to 0 to -70 dB |
| V4 RT60 by octave | Bars per band with ±sd whiskers; target line at 0.6 s | Bars over target in a warning tint; "estimated" badge on filled-in bands | Use the ANSI S12.60 limit (0.6 or 0.7 s by volume) if you show room-size-specific targets |
| V5 Day clarity timeline | Two stacked panels sharing the time axis: noise Leq (dBA) on top, back-row STI below with the ≥ 0.60 zone shaded; event markers | Hours below target highlighted | The doc says y1/y2. Stacked panels show the same data without a confusing dual axis; mention the choice |
| V6 What-if fix ranking | Horizontal bars, sorted descending by predicted back-row STI gain | Label "+0.08 · ₹ cost"; range whisker from the uncertainty | Negative or zero gains are shown, not hidden |
| V7 Required voice level | Points/line per row: dBA at 1 m needed for STI 0.60 | Reference lines: normal, raised, loud; "not reachable" marker above the plot | Mark the results uncalibrated when the 60 dBA fallback is used |
| V13 MTF curves | One line per band: m vs modulation frequency (log x, 0–1 y) | Legend with band names | 7 lines: use direct labels at line ends if the legend crowds |
| V14 Speech vs distance | Speech curve L_s(r), horizontal noise line, vertical r_c marker, seat positions as ticks | Shaded gap where speech is below noise + 15 dB | Use the 1 kHz band, or the A-weighted sum; say which in the caption |
| V15 Octave spectrum | Grouped bars per band: voice, noise, SNR at the selected seat | Zero baseline (SNR can be negative) | Use the same band order everywhere: 125 Hz on the left |

## Every chart checklist

1. A caption sentence in plain words (NFR-2), for example "High pitches ring too long; thin curtains would help most."
2. Uncertainty shown where it exists (NFR-10): whiskers, bands or "±".
3. Works in light and dark mode. Preview both with `SampleData`: `@Preview(uiMode = UI_MODE_NIGHT_YES)`.
4. Axis titles include units: "RT60 (s)", "Level (dB rel.)", "Distance (m)".
5. An empty state.
6. A content description for TalkBack that states the finding, not the chart type.
