---
name: compose-charts-2d
description: Build or revise ClearRoom's 2D Compose Canvas charts, axes, legends, seat heatmaps, timelines, comparison charts, and chart accessibility. Use for V1–V7 and V13–V15 or shared chart styling.
---

# ClearRoom 2D visualizations

All 15 views exist. Change their presentation without changing the meaning of their underlying models. Start with `viz/ChartKit.kt` and the view's existing `viz/*.kt` file; the same `DrawScope` drawing functions are also used in the PDF report.

- Keep the model, `DrawScope` rendering, and composable wrapper separate. Preserve the pure Kotlin models and reuse `ChartStyle.fromTheme()` for light and dark; use `ChartStyle.print()` for the PDF.
- The four STI band colours come only from `ui/theme/StiColors.kt`, in both 2D and 3D. Do not use those hues for unrelated chrome or non-STI data. Every colour-coded chart also needs text, a legend, or a spoken summary.
- Keep units on axes and use the same band order everywhere: 125 Hz to 8 kHz. Show uncertainty whiskers/ranges where the model provides them; show the 0.60 STI target where a chart compares clarity against the goal.
- Seat and cause maps share room geometry, speaker position, and tap mapping. Tap targets must remain usable on the phone. Before/after views must share colour scales and axes.
- V3 plots the fitted decay region; V4 shows RT60 per band with the correct room target; V5 uses aligned noise and clarity time panels; V6 ranks fix gain with uncertainty; V7 distinguishes reachable from echo-limited voice levels; V13–V15 retain modulation, distance, and spectrum units.
- Make the conclusion readable before chart details. Keep a plain-language caption and a TalkBack description of the finding; handle empty data without drawing a broken axis.

Review a sample room in both themes and keep the PDF and device UI consistent after any shared renderer change.
