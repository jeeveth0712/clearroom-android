---
name: storage-and-pdf-report
description: How ClearRoom stores rooms and sessions as JSON (org.json, one file per room) and produces the one-page PDF report (FR-12) and before/after comparison (FR-13). Covers the pure data model, value-equality pitfalls with DoubleArray in StateFlow, NaN/Infinity in JSON, schema versioning, atomic writes with AtomicFile, safe file names, testing org.json on the JVM, drawing Compose charts straight onto PdfDocument pages, report content rules, and sharing through FileProvider and ACTION_SEND. Use it for data/Models.kt, data/Store.kt, report/PdfReport.kt, save/load bugs, "room disappeared after restart", the share button or anything about the report, even if the user only says "save it" or "export".
---

# Storage and PDF report

## Data model (`data/Models.kt`, pure Kotlin)

The doc's model: `Room(name, lengthM, widthM, heightM, rows, cols, surfacePreset, students)`, `SeatMeasure(row, col, noiseDb[7], fansOn, rtTakes: List<DoubleArray?>)`, `Session(room, voiceDb[7]?, seats, whatIf, log: List<LogSecond>)`, `LogSecond(time, bandsDb[7], label)`.

Keep the fields, with two changes, and tell the user about both:

1. **Use `List<Double>` (or a small `BandLevels` value class) instead of `DoubleArray` in data classes.** A data class compares arrays by reference. Two sessions with identical numbers then aren't `equals`, and an array changed in place doesn't count as a change at all. `StateFlow` skips emissions that are `equals` to the current value, so in-place edits silently never reach the UI. Convert to `DoubleArray` at the DSP boundary.
2. **Give `Room` a stable `id: String` (UUID).** Use the id for file names and references, never the user-typed name.

The log rows themselves live in a separate append-only file (see `noise-day-log`); the session keeps only a pointer to it.

## Store (`data/Store.kt`, Android side)

```kotlin
interface RoomRepository {                        // defined next to the domain; Store implements it
    suspend fun save(session: Session)
    suspend fun load(roomId: String): Session?
    suspend fun list(): List<RoomSummary>
}
```

- Location: `context.filesDir/rooms/<id>.json`. That's app-private storage, which needs no permissions and is invisible to other apps.
- Write atomically with `androidx.core.util.AtomicFile`: `startWrite()` → write → `finishWrite(stream)`, and `failWrite(stream)` in `catch`. A crash mid-save then leaves the previous file intact, which is what "a room survives an app restart" needs.
- Do I/O on `Dispatchers.IO`.
- Start every file with `"schemaVersion": 1`. `load()` passes older versions through a `migrate(json, fromVersion)` function. Unknown future versions are refused with a clear message, not half-parsed.
- **NaN and Infinity are not valid JSON.** `JSONObject.put` throws on them, and an "STI with noise removed" computed from `SNR = +∞` can leak one into the session. Encode non-finite numbers as `null` and decode `null` as "missing". Rejected RT takes are already `null` in the doc's `List<DoubleArray?>`.
- Catch `JSONException` per file in `list()`. One corrupt room must not hide all the others. Show it as "damaged" with a delete option.
- Persist only numbers, labels and settings, never audio (NFR-6).
- The built-in sample room is created in code (`SampleData`) and isn't written to disk unless the user saves a copy.

### Testing org.json on the JVM

`org.json` comes from `android.jar`, which is a stub in local unit tests ("Method ... not mocked"). Add the real library for tests only: `testImplementation("org.json:json:<latest>")` in the version catalog. Then a JVM round-trip test (`save` → `load` → `assertEquals`) runs in milliseconds. The `List<Double>` change above is what makes that `assertEquals` meaningful.

## PDF report (FR-12)

One A4 page: `PdfDocument.PageInfo.Builder(595, 842, 1)` (points, at 72 per inch).

Content, top to bottom:

1. Title, room name, date and time.
2. **Verdict** in plain words with its band and range: "Back rows: fair (0.52–0.58). Main cause: echo."
3. The V1 seat clarity map, with its legend.
4. **Top 3 fixes** from the ranking, each with the predicted gain range and cost.
5. A small V4 RT60-by-band chart.
6. **Measurement conditions:** audio source (UNPROCESSED or VOICE_RECOGNITION), sample rate, seats measured, takes per seat accepted/total, fan state, occupancy, voice reference measured or "60 dBA fallback, uncalibrated" (NFR-5).
7. Footer: "Estimate from a phone measurement. Not a standards compliance test." (NFR-10: the app never claims compliance.)

### Drawing charts onto the page as vectors

The 2D charts are `DrawScope` functions (see `compose-charts-2d`), so draw them straight onto the PDF canvas. That gives sharp vector output and no bitmap memory:

```kotlin
val page = document.startPage(pageInfo)
val composeCanvas = androidx.compose.ui.graphics.Canvas(page.canvas)
val textMeasurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
page.canvas.save(); page.canvas.translate(mapLeft, mapTop)
CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, composeCanvas, Size(mapWidth, mapHeight)) {
    drawSeatHeatmap(model, ChartStyle.print(), textMeasurer)
}
page.canvas.restore()
document.finishPage(page)
```

Use a `ChartStyle.print()` with dark text on white, whatever the app theme is. If an API in this snippet doesn't match the Compose version in the project, look it up (Context7 MCP) rather than guessing. The bitmap fallback is the doc's original plan: draw into an `ImageBitmap` with the same `CanvasDrawScope` and place it with `drawBitmap`.

Generate the PDF on `Dispatchers.IO`, and `close()` the document in `finally`.

### Sharing

- Write to `cacheDir/reports/ClearRoom-<roomId>-<yyyyMMdd-HHmm>.pdf`. Delete older reports there on each export.
- Manifest provider: `androidx.core.content.FileProvider` with `android:authorities="${applicationId}.fileprovider"`, `android:exported="false"` and `android:grantUriPermissions="true"`. Its `res/xml/file_paths.xml` exposes only `<cache-path name="reports" path="reports/" />`. Don't expose the files root or "." paths, which would hand other apps every room file.
- Intent: `ACTION_SEND`, type `application/pdf`, `EXTRA_STREAM` = `FileProvider.getUriForFile(...)`, `FLAG_GRANT_READ_URI_PERMISSION`, started through `Intent.createChooser`.

## Before/after (FR-13)

When the user taps "Re-measure after a change", freeze the current results as an immutable `baseline` snapshot inside the session (Memento pattern). New measurements fill the "after" side. Show V1 and V4 side by side on the **same colour scale and axes**. Charts with separately autoscaled axes make a small change look big. The PDF can include both maps when a baseline exists.
