# ClearRoom: Speech Clarity Checker — Requirements and Build Plan

Oct 24, 2026 · @Jeeveth

ClearRoom merges the Room Echo Checker (#21) and the Noise Dose Tracker (#22) into one question that no free app answers: **can people at every seat understand the speaker, and what cheap change fixes it?** It measures echo (RT60) and background noise with one phone, combines them into a Speech Transmission Index (STI) per seat, and simulates fixes such as curtains or switching off fans.

## Why it is different

Existing apps each measure one number at one spot; ClearRoom is the first to turn echo + noise into a **per-seat, per-hour clarity map with tested fixes**, on a free Android app with no test-signal hardware.

| What users need | Echo apps (RevMeter, VQ AcousticCheck) | Noise apps (NIOSH SLM, dB meters) | STIPA apps (AudioTools STIPA) | ClearRoom |
| --- | --- | --- | --- | --- |
| Echo (RT60) | Yes, per octave | No | Indirectly | Yes, per octave, from a clap |
| Background noise | No | Yes, one spot | Level only | Yes, as a timeline across the day |
| Speech clarity score (STI) | No | No | Yes, one spot, needs the standard test signal | Yes, computed from echo + noise |
| Clarity at every seat | No | No | No | Yes, seat heatmap |
| When in the day it is worst | No | Partly (log) | No | Yes, hourly clarity timeline |
| "What fixes it?" | No | No | No | Yes, what-if simulator (curtains, fans off, mic) |
| How loud the speaker must talk | No | No | No | Yes, required voice level per seat |
| Platform / cost | iOS, paid | Mostly iOS (NIOSH) | iOS, paid add-on | Android, free |

**The engineering trick that makes this better than #22 alone:** STI depends on the signal-to-noise *difference* and the echo *decay rate*. Both are measured with the same microphone, so the phone's absolute calibration error (several dB per NIOSH) largely cancels. The weakest point of a phone noise meter stops mattering.

## Problem, users and goals

In many classrooms, tuition centres and meeting halls, students at the back miss words. Two causes stack up: echo from bare walls and floors, and background noise from fans, coolers and traffic. Teachers respond by raising their voice for hours. Nobody can tell which cause dominates, which seats are affected, or which cheap change would help.

**Primary users:** teachers and school or college administrators. **Secondary:** tuition-centre owners, meeting-room and small-hall managers, home-studio and online-class creators.

**Goals**

1. Give a clarity verdict (STI band: poor / fair / good / excellent) for each seat zone in under 10 minutes of measuring.
2. Show *why*: echo-limited, noise-limited, or distance-limited.
3. Show *when*: how clarity changes across a school day as noise changes.
4. Rank fixes by predicted STI gain: switch off or slow fans, close windows, add curtains or soft boards, move the speaker, use a mic.
5. Tell the speaker how loud they must talk to reach the target at the back row.

**Out of scope:** certified compliance testing, hearing assessment, recording or storing speech content.

## How it works

&#91;embedded content: ClearRoom pipeline · 3 measurements, 1 engine, 3 outputs\]

Three short phone measurements feed one STI engine; the what-if loop reuses the same engine with changed inputs, so every suggested fix carries a predicted score before anyone spends money.

## Functional requirements

Must = core, Should = full feature; both (FR-1 to FR-13) are built in the 2-day sprint, leaving only testing; Could = future work.

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-1 | **Room setup:** enter length, width, height (m); sketch speaker position and seat grid (rows × columns); tag surfaces (concrete, tile, glass, wood, curtain, soft board, seated students) from a pick list. | Must |
| FR-2 | **Echo test:** guided clap or balloon pop at the speaker position, recorded at 3+ seats; repeat 3× per seat. Reject takes with clipping or less than 25 dB of decay above the noise floor. | Must |
| FR-3 | **RT60 per octave:** compute T20 and T30 for 250 Hz–4 kHz octave bands; report mean and spread across takes. | Must |
| FR-4 | **Background noise snapshot:** 30-s A-weighted and octave-band Leq at each measured seat, with fans/AC state tagged (on/off/speed). | Must |
| FR-5 | **Speech level reference:** speaker reads a given paragraph for 20 s at normal voice, phone at 1 m; app stores octave-band speech level. Fallback: assume 60 dBA normal speech. | Must |
| FR-6 | **STI estimate per seat:** combine RT60, noise and distance-adjusted speech level into STI (method in Analysis). | Must |
| FR-7 | **Seat clarity map:** interpolate STI over the seat grid; show bands and the limiting cause per zone (echo / noise / distance). | Must |
| FR-8 | **What-if simulator:** change surfaces, fan state, speaker position, voice level or a mic gain; predict new RT60 and STI and show the change. | Must |
| FR-9 | **Day noise log (from #22):** leave the phone in the room for 1–8 h; log 1-s levels (no audio stored); classify events (fan, traffic, horn, chatter) and compute hourly Leq and L90. | Should |
| FR-10 | **Clarity timeline:** recompute back-row STI each hour from the logged noise; flag hours below target. | Should |
| FR-11 | **Voice effort advice:** required speech level at 1 m to reach STI ≥ 0.60 at the back row; show it against normal / raised / loud voice levels. | Should |
| FR-12 | **Report:** one-page PDF with verdict, map, top 3 fixes and measurement conditions. | Should |
| FR-13 | **Fix verification:** re-measure after a change; before/after comparison. | Should |
| FR-14 | **Community benchmark:** anonymised room scores compared across rooms in one school. | Could |

## Analysis methods

The core is the **indirect STI method**: estimate how much of speech's slow loudness modulation survives echo and noise, per octave band, then weight the bands. Each step below is a short, testable function.

**1. RT60 from a clap.** Band-pass the recording into octaves, square it, and apply Schroeder backward integration to get a smooth decay curve. Fit a line between −5 and −25 dB (T20) or −5 and −35 dB (T30) and extrapolate to 60 dB.

```latex
E(t) = \int_t^{\infty} p^2(\tau)\, d\tau, \qquad T_{60} = \frac{-60}{\text{slope of } 10\log_{10} E(t) \ \text{(dB/s)}}
```

**2. Room model fitted to the measurement.** Use Sabine's equation to back out the room's effective absorption from the measured RT60, so the what-if simulator starts from reality, not guesses.

```latex
T_{60} = \frac{0.161\, V}{A}, \qquad A = \sum_i S_i\, \alpha_i \ \ (\text{m}^2)
```

A what-if change adds or removes absorption (for example, 10 m² of curtain with α ≈ 0.5 at 1 kHz) and recomputes T60.

**3. Speech level at each seat.** Direct sound falls with distance; reverberant sound stays roughly constant. With speaker directivity Q ≈ 2 and room constant R:

```latex
L_s(r) = L_{1\text{m}} + 10\log_{10}\!\left(\frac{\frac{Q}{4\pi r^2} + \frac{4}{R}}{\frac{Q}{4\pi} + \frac{4}{R}}\right), \qquad R = \frac{S\,\bar{\alpha}}{1-\bar{\alpha}}
```

SNR per band and seat = L\_s(r) − measured noise level at that seat.

**4. Modulation transfer function and STI.** For each octave band k and the 14 modulation frequencies F (0.63–12.5 Hz):

```latex
m_k(F) = \frac{1}{\sqrt{1 + \left(\frac{2\pi F\, T_{60,k}}{13.8}\right)^2}} \cdot \frac{1}{1 + 10^{-\mathrm{SNR}_k/10}}
```

```latex
\mathrm{SNR}_{\text{app}} = 10\log_{10}\frac{m}{1-m}\ \text{(clipped to } \pm 15\text{ dB)}, \qquad \mathrm{TI} = \frac{\mathrm{SNR}_{\text{app}} + 15}{30}
```

Average TI over F to get each band's MTI, then combine bands with the IEC 60268-16 band weights and redundancy factors to get STI (0–1). The app ships a **self-test screen** that runs the whole chain on synthetic decays with known RT60 and SNR (generated on the phone), so correctness is checked without any computer.

**5. Limiting cause per seat.** Recompute STI twice: with noise removed (echo-only) and with echo removed (noise-only). Whichever gives the larger gain is labelled the dominant cause; distance-limited when the seat is beyond the critical distance r\_c = 0.057 √(QV/T60).

**6. Noise events (day log).** 1-s Leq stream; on-device YAMNet (TensorFlow Lite) labels fan, traffic, horn, speech and music in 1-s windows; only labels and levels are stored.

## Visualizations

Fifteen views, each tied to a decision: seven core 2D charts (below), five interactive 3D scenes and three engineering 2D charts (next table). The seat map, cause map, 3D clarity room and what-if are what no existing app shows.

| # | Visual | Data and axes | What it reveals | Decision it supports |
| --- | --- | --- | --- | --- |
| V1 | **Seat clarity heatmap** | Room plan (m); colour = STI band per seat; speaker marked | Which rows lose words | Where to seat students who need it most; whether to use a mic |
| V2 | **Limiting-cause map** | Same plan; icon or hatch per zone: echo / noise / distance | Why each zone is poor | Buy curtains (echo) vs. fix fans (noise) vs. move speaker (distance) |
| V3 | **Decay curve with fit** | x: time (s); y: level (dB); Schroeder curve + T20 line, per band | Measurement quality and RT60 | Trust the echo number; retake if the fit is poor |
| V4 | **RT60 by octave vs. target** | x: octave band (Hz); y: RT60 (s); bar per band, target line at 0.6 s with spread whiskers | Which frequencies ring too long | Which absorber type (thin curtain helps highs, thick panels help lows) |
| V5 | **Day clarity timeline** | x: clock time; y1: noise Leq (dBA); y2: back-row STI; target band shaded; noise events marked | Hours when teaching is hardest | Timetable important lectures in quiet hours; close windows at peak traffic |
| V6 | **What-if fix ranking** | Horizontal bars: predicted back-row STI gain per fix, labelled with cost (₹) | Best value fix | Which single change to make first |
| V7 | **Required voice level** | x: seat row; y: dBA at 1 m needed for STI 0.60; reference lines for normal, raised and loud voice | How much a speaker strains | When a mic is needed instead of a louder voice |

Before/after (FR-13) reuses V1 and V4 side by side on the same colour scale.

### 3D scenes and engineering charts

Every 3D scene can be rotated with one finger, zoomed with a pinch and reset with a double-tap. Colours use the same STI bands as the 2D views, so a red seat is red everywhere.

| # | View | Type | Data and axes | What it reveals |
| --- | --- | --- | --- | --- |
| V8 | **3D clarity room** | 3D | Room box to scale (m); a bar on each seat, height = STI (0–1), colour = band; speaker shown as a cone at the front | The whole room's clarity at a glance; the hero view of the app |
| V9 | **Clarity terrain** | 3D surface | x, y = floor (m); z = STI computed on a 0.25 m grid; contour line at 0.60 | How clarity falls off with distance and where the 0.60 line crosses the room |
| V10 | **Echo waterfall** | 3D surface | x = time after clap (0–2 s); y = octave band (125 Hz–8 kHz); z = level (dB) | Which frequencies ring longest; the visual signature of the room |
| V11 | **Noise landscape** | 3D bars | x = hour (or minute) of the log; y = octave band; z = noise level (dB) | When and at what pitch noise peaks (fan hum low, traffic mid, horns high) |
| V12 | **What-if morph** | 3D animation | V8 bars animate from "now" to "with fixes" as sliders move | The effect of a fix seen as bars rising out of the red |
| V13 | **MTF curves** | 2D | x = modulation frequency (0.63–12.5 Hz, log); y = modulation index m (0–1); one line per octave band | How echo and noise smear speech rhythm; the engineering core of STI |
| V14 | **Speech vs. noise by distance** | 2D | x = distance from speaker (m); y = level (dB rel.); speech curve, noise line, critical-distance marker | Why back rows fail: where speech sinks into noise |
| V15 | **Octave spectrum compare** | 2D | x = octave band; y = dB rel.; grouped bars for voice, noise and SNR at the selected seat | Which pitch range is masked |

## Non-functional requirements

| ID | Area | Requirement |
| --- | --- | --- |
| NFR-0 | Mobile-only | Capture, analysis, charts, validation and reports all run on one smartphone. No laptop, external microphone, second speaker or reference meter is required at any stage. |
| NFR-1 | Ease of use | A first-time user completes a 3-seat room check in ≤ 10 minutes with on-screen guidance only. |
| NFR-2 | Plain language | Every verdict shows a one-sentence explanation and its supporting chart; no unexplained acoustics terms on the main screen. |
| NFR-3 | Accuracy — echo | RT60 within ±10% (or ±0.1 s, whichever is larger) on the in-app synthetic test decays, and repeat-take spread ≤ 10% in real rooms (500 Hz–2 kHz). |
| NFR-4 | Accuracy — clarity | STI within ±0.02 of the expected value on the in-app synthetic test cases. |
| NFR-5 | Calibration-robust | Speech and noise measured with the same phone; results state "uncalibrated" when the fallback 60 dBA voice is used. |
| NFR-6 | Privacy | No speech audio leaves the device or is stored beyond the 20-s reference clip, which is deleted after analysis; the day log keeps only levels and event labels. |
| NFR-7 | Platform | Android 10+; 48 kHz (or 44.1 kHz) mono capture with automatic gain control and noise suppression turned off (`UNPROCESSED` or `VOICE_RECOGNITION` audio source). |
| NFR-8 | Offline | All analysis runs on the phone; internet only for optional report sharing. |
| NFR-9 | Performance | Result in ≤ 5 s per clap on a mid-range phone; day log uses ≤ 5% battery per hour with screen off on charger. |
| NFR-10 | Honesty | Every STI value shows its uncertainty band from repeat takes; the app never claims standards compliance. |

## Android app design

A native Kotlin app with Jetpack Compose, one Activity, no backend. The DSP and acoustics code is plain Kotlin with no Android imports, so it can be unit-tested on a laptop JVM later without changing a line.

### Tech stack

| Layer | Choice | Why |
| --- | --- | --- |
| Language / UI | Kotlin, Jetpack Compose, Material 3 | Fastest way to build many custom screens and charts |
| Project start | Android Studio "Empty Activity" (Compose) template | Gets AGP, Kotlin and Compose versions that already work together; don't hand-pick versions |
| SDK levels | minSdk 29 (Android 10), target and compile = template default | Matches NFR-7; `UNPROCESSED` audio source is available |
| Audio capture | `AudioRecord`, 48 kHz, mono, `ENCODING_PCM_FLOAT`; source `UNPROCESSED` if `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` is true, else `VOICE_RECOGNITION` | Avoids automatic gain control and noise suppression that corrupt decays |
| Concurrency | Kotlin coroutines (`Dispatchers.Default` for DSP), `StateFlow` in one `AppViewModel` | Keeps the UI responsive during analysis |
| 2D charts | Compose `Canvas`, drawn by hand (6 small composables) | Full control of heatmaps and annotations; no library version risk |
| 3D scenes | Own mini 3D renderer on Compose `Canvas` (`Scene3D`): 4×4 view matrix, perspective projection, painter's sort, flat shading by face normal, drag-to-orbit, pinch-to-zoom | Native, offline, about 250 lines; handles bars, boxes and surface meshes, which is all V8–V12 need |
| Sound labels (FR-9) | MediaPipe Tasks Audio `AudioClassifier` with the YAMNet `.tflite` model in `assets/` | One dependency, runs on-device |
| Storage | One JSON file per room in app storage (`org.json`, built in) | No database setup; numbers only, never audio |
| PDF report (FR-12) | Android `PdfDocument` + Compose chart bitmaps; share with `Intent.ACTION_SEND` | Built into the platform |

Fallback if the custom 3D renderer looks rough by the end of Day 1: a `WebView` loading a bundled `three.min.js` from `assets/` with data passed as JSON. It stays offline and on-device, but adds a bridge to maintain.

### Screens (bottom navigation, 5 tabs)

| Tab | Contents | Main actions |
| --- | --- | --- |
| Room | Dimensions, rows × columns, surface preset, occupancy; saved rooms list | Create, edit, switch room; load sample room |
| Measure | Seat grid picker; per seat: echo takes and noise status; voice reference status | Clap test, noise snapshot, voice reference, retake |
| Results | Verdict card; V8 3D room / V1 heatmap / V2 cause toggle; V9 terrain; V3, V4, V10, V13, V14, V15 in a scroll of cards | Tap a seat for its details; share PDF |
| Fixes | What-if sliders; V12 morph; V6 fix ranking; V7 required voice | Adjust, compare before/after, save a scenario |
| Log | Live level meter; V5 timeline; V11 noise landscape; event list | Start / stop day log (screen kept on, phone on charger) |

### Code modules

| File | Responsibility |
| --- | --- |
| `MainActivity.kt` | Mic permission, Compose scaffold, bottom navigation |
| `AppViewModel.kt` | Holds current room, measurements, what-if settings and derived results as `StateFlow` |
| `data/Models.kt`, `data/Store.kt` | Room, Seat, SeatMeasure, Session data classes; JSON save/load |
| `audio/Recorder.kt` | Record N seconds or stream 1-s blocks; live level; source selection |
| `dsp/Biquad.kt`, `dsp/OctaveBank.kt` | Stateful biquad; 7 octave band-pass filters; band levels |
| `dsp/Reverb.kt` | Clap → RT60 per band, Schroeder curves, waterfall matrix for V10, quality checks |
| `dsp/Sti.kt` | MTF, STI with IEC band weights, echo-only and noise-only variants |
| `acoustics/RoomModel.kt` | Sabine fit, speech level vs. distance, seat STI grid, causes, what-if, required voice |
| `audio/NoiseLog.kt` | 1-s band levels, YAMNet labels, hourly Leq and L90 |
| `viz/Scene3D.kt` | Camera, projection, gestures, mesh and bar primitives |
| `viz/*.kt` | One composable per view V1–V15 |
| `report/PdfReport.kt` | One-page PDF |

### Exact parameters (so nothing is guessed while coding)

| Item | Value |
| --- | --- |
| Octave bands | 125, 250, 500, 1000, 2000, 4000, 8000 Hz |
| Band filter | RBJ band-pass (0 dB peak), Q = 1.414, two in series per band |
| Recording lengths | Clap 5 s (3-s countdown, clap at \~1 s); noise 10 s; voice 20 s at 1 m |
| Noise floor (clap) | Lower of: mean energy of the 0.3 s before the clap, mean of the last 0.5 s |
| Decay fit | 10 ms smoothing; truncate at noise + 5 dB; fit Schroeder curve from −5 to −25 dB (T20); reject if dynamic range < 30 dB or T60 outside 0.1–6 s |
| Modulation frequencies | 0.63, 0.8, 1, 1.25, 1.6, 2, 2.5, 3.15, 4, 5, 6.3, 8, 10, 12.5 Hz |
| STI band weights (male, verify against IEC 60268-16:2020) | α = 0.085, 0.127, 0.230, 0.233, 0.309, 0.224, 0.173; β = 0.085, 0.078, 0.065, 0.011, 0.047, 0.095 |
| STI bands | < 0.45 poor · 0.45–0.60 fair · 0.60–0.75 good · ≥ 0.75 excellent; target 0.60 |
| Room defaults | Speaker 1 m from front wall, centred; directivity Q = 2; seats spread from 2.5 m to (length − 0.8 m), 0.8 m side margins |
| What-if absorbers (typical, per band 125–8k) | Heavy curtain α = 0.14, 0.35, 0.55, 0.72, 0.70, 0.65, 0.65; seated student 0.15, 0.25, 0.35, 0.45, 0.50, 0.50, 0.50 m² each |
| Preset fixes for ranking | 20 m² curtains; 15 m² soft boards; fans off (−6 dB noise, or the measured difference if both states were recorded); portable mic (+10 dB voice) |
| Levels | All in dB relative to the phone's full scale; only differences are shown as results |

### Data model

`Room(name, lengthM, widthM, heightM, rows, cols, surfacePreset, students)` · `SeatMeasure(row, col, noiseDb[7], fansOn, rtTakes: List<DoubleArray?>)` · `Session(room, voiceDb[7]?, seats, whatIf, log: List<LogSecond>)` · `LogSecond(time, bandsDb[7], label)`.

## Two-day build plan

Day 1 builds the engine and the first 3D scene; Day 2 builds the remaining 14 views and all features. That is about 12 focused hours a day, so use an AI coding assistant for boilerplate and keep these rules:

- Build against the **sample room** first: every chart works with sample data before you touch the microphone again.
- Make every view exist before polishing any of them.
- One commit per task below, so you can roll back.

### Day 1 — engine, measurements, 3D core

- [ ] **09:00–10:00 · Project skeleton.** Template project, `RECORD_AUDIO` permission flow, Material 3 theme with light and dark, 5-tab bottom navigation. *Done when:* the app installs and the tabs switch.
- [ ] **10:00–11:30 · Recorder.** `Recorder.kt`: source selection, record N seconds, stream 1-s blocks, live level meter. *Done when:* Measure tab records 5 s and shows a moving level bar.
- [ ] **11:30–12:30 · Data and Room tab.** Models, JSON store, Room form, seat grid, built-in sample room. *Done when:* a room survives an app restart.
- [ ] **13:30–15:00 · Filters and levels.** `Biquad`, `OctaveBank`, noise snapshot and voice reference saving 7 band levels. *Done when:* noise and voice show per-band numbers.
- [ ] **15:00–17:30 · Echo analysis.** `Reverb.kt`: RT60 per band, Schroeder curve, waterfall matrix, quality messages ("clap louder", "too noisy", "clipped"), 3 takes per seat. *Done when:* a real clap gives RT60 values between 0.3 and 2 s in a normal room.
- [ ] **17:30–19:00 · Clarity engine.** `Sti.kt` and `RoomModel.kt`: Sabine fit, speech vs. distance, STI grid, causes, what-if, required voice. *Done when:* the sample room gives a back-row STI below its front-row STI.
- [ ] **19:00–21:00 · 3D core.** `Scene3D.kt` (camera, projection, orbit, pinch, shading) and V8 3D clarity room. *Done when:* the sample room's bars rotate smoothly.

**End of Day 1:** one real seat measured end to end; numbers flow into the 3D room.

### Day 2 — all views and features

- [ ] **09:00–10:30 · Results core.** Verdict card, V1 seat heatmap, V2 cause map, seat detail sheet.
- [ ] **10:30–12:00 · 3D surfaces.** V9 clarity terrain and V10 echo waterfall (mesh on `Scene3D`).
- [ ] **12:00–13:00 · 2D engineering charts.** Shared axis helper; V3 decay, V4 RT60 bars, V13 MTF curves, V14 speech vs. distance, V15 octave spectrum.
- [ ] **14:00–15:30 · Fixes tab.** What-if sliders, V12 morph animation, V6 fix ranking, V7 required voice.
- [ ] **15:30–17:30 · Log tab.** `NoiseLog.kt`, YAMNet labels, keep-screen-on, V5 day timeline, V11 noise landscape.
- [ ] **17:30–18:30 · Report and before/after.** PDF report with share, before/after comparison (FR-13).
- [ ] **18:30–20:00 · Finish.** Empty states, error text, app icon and name, release APK, install on two phones, record a demo video.
- [ ] **20:00–21:00 · Buffer.**

**End of Day 2 (definition of done):** all of FR-1 to FR-13 and V1 to V15 work on a real phone in a real room; nothing is left but the testing in the next section.

**If you fall behind,** move these into the testing phase in this order: YAMNet labels (keep the log without labels), PDF report, V12 animation (show a static before/after instead).

## Testing after the build

Testing starts once the 2-day MVP installs and runs. Build the in-app self-test screen (Analysis step 4) on the first testing day, then work through this table.

All tests fit in three or four rooms you can access (a classroom, a small seminar room, a tiled bathroom or corridor, a furnished bedroom), so fieldwork stays at Level 1.

| Test | How (all on the phone) | Pass if |
| --- | --- | --- |
| Echo self-test | App generates synthetic decays with known RT60 (0.3–2.0 s) plus noise and analyses them | NFR-3 met |
| STI self-test | App feeds known RT60 and SNR pairs into the STI engine and compares with stored expected values (hand-worked in the report) | NFR-4 met |
| Echo repeatability | 5 claps per seat, 3 seats, 3 rooms | Coefficient of variation of RT60 ≤ 10% at 500 Hz–2 kHz |
| Distance model | Same phone records your voice at 1, 3, 5, 8 m; app compares measured vs. predicted L\_s(r) | Mean error ≤ 3 dB |
| What-if realism | Measure a room, hang 2–3 bedsheets or curtains, re-measure; app compares predicted vs. measured RT60 change | Predicted change within ±25% of measured |
| Fan effect | Same seat, fans off vs. full speed | Noise and STI change in the expected direction; reported cause flips to "noise" at full speed |
| Listening check | 2–3 volunteers write down 20 short words read from the front, at a front and back seat; scores entered in the app | Seats the app rates poorer get lower word scores (rank agreement) |
| Usability | 3 people unfamiliar with the app run a check | NFR-1 met; each can say the top fix in their own words |

**Demo scenario:** measure a seminar room live with three claps, show the seat map with a red back zone labelled "echo", run the what-if for curtains, then hang a blanket and re-measure to show the predicted improvement come true.

## Risks

| Risk | Effect | Mitigation |
| --- | --- | --- |
| Phone audio processing (AGC, noise suppression) distorts decay | Wrong RT60 | Use the unprocessed audio source; reject devices that don't support it; test with a known clap |
| Clap is weak in low bands | Poor 125–250 Hz fit | Report 500 Hz–4 kHz as primary; offer balloon pop as a stronger source |
| Sabine is inaccurate in very absorbent or oddly shaped rooms | What-if errors | Fit the model to the measured RT60 first; show predictions as a range |
| No external reference instrument (mobile-only) | Absolute accuracy can't be proven | Rely on synthetic self-tests, repeatability, the before/after curtain test and the listening check; state this limit in the report |
| Simplified STI differs from the full standard method | Bias in scores | Present bands, not 2-decimal certainty |
| Privacy concerns about microphone in classrooms | Users refuse the day log | Store levels and labels only; show a visible "recording levels, not audio" banner |
| Occupied rooms differ from empty ones | Echo shorter when full | Let users tag occupancy; add the seated-student absorption in the model |

## Sources

- [ANSI S12.60 classroom limits (Larson Davis summary)](https://larsondavis.com/learn/building-acoustics/ansi-standards-for-classroom-acoustics): RT60 ≤ 0.6 s (< 10,000 ft³) or ≤ 0.7 s (10,000–20,000 ft³); background noise 35 dBA, unoccupied
- [MATLAB speechTransmissionIndex](https://www.mathworks.com/help/audio/ref/speechtransmissionindex.html): indirect STI per IEC 60268-16:2020, seven octave bands, 14 modulation frequencies, STI rating categories (G = 0.50 voice-alarm target)
- [AudioTools STIPA Pro manual (Studio Six Digital)](https://studiosixdigital.com/audiotools-modules-2/speaker-test-modules/sti-pa/stipa-pro-user-manual): iOS, needs the STIPA test signal, single-point result, no seat maps or fix advice
- [RevMeter Pro](https://apps.apple.com/us/app/-/id357421594) and [VQ AcousticCheck](https://apps.apple.com/app/id6737261712): iOS RT60 apps, no noise or clarity
- [Kardous & Shaw, smartphone sound app accuracy (NIOSH)](https://pmc.ncbi.nlm.nih.gov/articles/PMC4545478): why absolute phone dB readings are unreliable
- [Browser-based reverberation time with smartphones (Aalborg University)](https://vbn.aau.dk/da/publications/92e37e33-5327-4621-92b1-65c4551ccc42): built-in phone mics gave results comparable to ISO 3382-2 reference measurements on Android
- [YAMNet on-device audio classification](https://developers.google.com/codelabs/tflite-audio-classification-basic-android)

## Implementation notes

Kept up to date during the build. The sections above are the original plan; this section records where the built app differs from it, and why. Progress per milestone is tracked in `docs/ClearRoom-Setup-and-Build-Plan.md`.

### Decisions where the plan left a choice

| Topic | Plan said | Built | Why |
| --- | --- | --- | --- |
| Noise snapshot length | FR-4: 30 s · parameters table: 10 s | **30 s** | Steadier reading when noise comes and goes (owner's choice) |
| Surfaces | Data model: one `surfacePreset` · FR-1: tag surfaces from a pick list | Floor, walls and ceiling each tagged from the FR-1 pick list; seated students as a count | Follows FR-1, which is the functional requirement |
| Clap rejection limit | FR-2: 25 dB of decay above noise · parameters table: 30 dB | **30 dB** | A T20 fit down to −25 dB needs margin above the noise floor |

### Changes made during the build

| Area | Change | Why |
| --- | --- | --- |
| Data model | Band levels are stored as a 7-value list type (`BandLevels`), not `DoubleArray`; every `Room` has a stable id used for its file name | Arrays compare by reference, so changed readings could silently fail to update the screen; file names never come from user-typed text |
| Measuring | The built-in sample room can be explored but not measured into; saving it makes the user's own copy | Keeps the reference example unchanged |
| Toolchain | Android Studio Rabbit 1 (2026.2.1), AGP 9.4.1, Kotlin raised from the template's 2.2.10 to 2.4.21, Compose BOM 2026.09.00 | Kotlin 2.2.10 is only tested up to AGP 8.10; 2.4.21 is tested with this Gradle and much closer to this AGP |
| Icons | Tab icons drawn as small vector files | Material 3 1.4 no longer bundles the Material icon library |
| Testing | On-phone tests run in a separate test app (`com.clearroom.app.uitest`) | The test runner uninstalls the app it tests; this way it never erases the user's saved rooms |
| Clap countdown | 3 s | 3 s by default, **10 s optional** | With the phone on a back seat, one person can't reach the front in 3 s |
| Clap filtering | Band-pass each octave | Band-pass the **time-reversed** clip | Moves the filters' own ringing before the clap instead of onto the decay (ISO 3382-1 allows this) |
| Clap onset | Noise from the 0.3 s before the clap | The clap starts where the energy first comes within 20 dB of its loudest moment; the noise window ends 50 ms before that | The loudest moment can be a later reflection; a test caught the noise window swallowing the decay |
| Which claps count | Not specified | A clap counts when 500 Hz, 1 kHz and 2 kHz all give an echo time; outer bands that fail are left empty and later borrowed from the nearest band, marked "estimated" | Speech bands matter most; claps are weak at 125 Hz (risk table) |
| Decay straightness | Not specified (ISO 3382 calls ξ > 10 ‰ curved) | A band is refused only above ξ = 40 ‰ | Short decays in the narrow 125–250 Hz bands reach 20–40 ‰ while still giving the right slope; every band at or below 40 ‰ met NFR-3 in the synthetic tests |
| How NFR-3 is checked | ±10 % or ±0.1 s on synthetic decays | Checked on what the app reports, the **mean of the 3 claps** (FR-2, FR-3), for 250 Hz–4 kHz and 8 kHz; single claps must also meet it at 500 Hz–2 kHz; 125 Hz (outside FR-3) within ±25 % or ±0.15 s | One clap's echo time scatters randomly around the truth (no bias), most in narrow bands; see the table below |

### Measured on the test phone (Samsung Galaxy S24 Ultra, Android 16)

- The phone does **not** offer Android's raw `UNPROCESSED` microphone source, so ClearRoom uses the `VOICE_RECOGNITION` source at 48 kHz, 32-bit float, as NFR-7 allows. Android attaches a noise suppressor and an echo canceller to that source; ClearRoom switches both off. No automatic gain control is attached.
- During a phone call Android gives other apps silence instead of microphone sound. ClearRoom detects this and says "No sound came through" instead of saving a wrong reading.

### Echo-time accuracy on synthetic decays (40 random claps per case, Oct 26)

Each synthetic clap is decaying white noise with a known echo time, like a real room's response. The table shows how much one clap's measured echo time scatters around the true value (standard deviation). The average error (bias) was under 3.5 % in every band and about 0 % from 250 Hz up, so the method itself is accurate. The scatter is why three claps are averaged per seat: averaging divides it by √3.

| Band | True echo 0.3 s | 0.8 s | 2.0 s |
| --- | --- | --- | --- |
| 125 Hz | 12.4 % | 10.0 % | 5.8 % |
| 250 Hz | 8.7 % | 6.3 % | 3.7 % |
| 500 Hz | 8.3 % | 5.2 % | 3.0 % |
| 1 kHz | 5.0 % | 3.5 % | 1.9 % |
| 2 kHz | 3.7 % | 2.3 % | 1.7 % |
| 4 kHz | 2.7 % | 1.3 % | 1.1 % |
| 8 kHz | 1.7 % | 1.3 % | 1.0 % |
