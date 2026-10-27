---
name: storage-and-pdf-report
description: Maintain ClearRoom's room JSON persistence, day-log references, one-page PDF report, share intent, FileProvider, or before/after comparison. Use for data storage and report code.
---

# Room storage and PDF report

The current implementation is in `data/Store.kt`, `data/RoomJson.kt`, and `report/`. Follow `AGENTS.md` section 7 and the requirements document's Implementation notes for the actual schema and report content.

- Save one room per `filesDir/rooms/<id>.json` with `AtomicFile` and `schemaVersion` 1. Use a validated ID for filenames, never a user-entered room name. Encode non-finite values as `null`; tolerate missing optional microphone, baseline, clap-curve, distance, and listening fields. A damaged entry should not hide the rest of the room.
- Keep `BandLevels` value-like, rather than mutable arrays in `UiState`. The built-in sample is never persisted; saving its layout creates a new room without copying made-up readings.
- Day logs live separately in `filesDir/logs/<roomId>/` as CSV. Deleting a room deletes its logs; no audio is stored.
- `Report.of(session, millis)` is pure Kotlin. The writer produces a single A4 page with the room, date, verdict and STI range, seat map, echo chart, worthwhile fixes, conditions, and non-compliance note. A before/after comparison shares chart scales and appears in both UI and report.
- Save only the newest PDF under `cacheDir/reports/`. FileProvider exposes that cache folder only, and the chooser grants read access to the selected PDF. Do not expose `files/rooms` or `files/logs`.
- Use the same `DrawScope` chart functions for the UI and PDF where applicable, with `ChartStyle.print()` on white. Check page fit and both themes when changing shared chart code.

Verify JSON round trips, partial/corrupt optional entries, share URI scope, and report page rendering after changes.
