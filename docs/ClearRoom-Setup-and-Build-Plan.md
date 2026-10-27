# ClearRoom: Setup Guide and Build Plan

Oct 24, 2026 · for @Jeeveth · companion to `ClearRoom Speech Clarity Checker — Requirements.md`

This document has two jobs. Parts A–C get your laptop and your Galaxy S24 Ultra ready to build Android apps. Parts D–F are the plan for building ClearRoom itself, milestone by milestone. The technical recipes for each milestone are in the project skills under `.claude/skills/`, so this plan points at them rather than repeating them.

**Contents:** [Your laptop](#your-laptop-at-a-glance) · [A. Install Android Studio](#part-a--install-android-studio) · [B. Connect the S24 Ultra](#part-b--connect-your-galaxy-s24-ultra) · [C. Create the project](#part-c--create-the-project) · [D. Build plan](#part-d--build-plan) · [E. Decisions](#part-e--decisions-to-confirm) · [F. Risks](#part-f--risks-on-this-laptop) · [Troubleshooting](#troubleshooting)

---

## Your laptop at a glance

Measured on Oct 24, 2026, against Android Studio's published Windows requirements.

| Item | Your laptop | Android Studio needs | Verdict |
| --- | --- | --- | --- |
| OS | Windows 11 Home 64-bit (build 26200) | 64-bit Windows 10 or newer | ✅ OK |
| CPU | Intel Core i5-10210U, 4 cores / 8 threads | Intel 8th gen i5 or newer. U-series chips are "not recommended" for speed | ✅ Works, builds are slower |
| RAM | 11.8 GB | Studio: 8 GB. Studio + emulator: 16 GB | ✅ Studio · ❌ emulator |
| GPU | Intel UHD (integrated) | Emulator: a GPU with 4 GB VRAM | ❌ emulator |
| Disk | Samsung NVMe SSD. C: 31.4 GB free, D: 93.8 GB free | 8 GB minimum, 32 GB recommended | ✅ Put the big folders on **D:** |
| Screen | 1920 × 1080 | 1280 × 800 minimum | ✅ OK |
| Virtualization | Hypervisor running; *Windows Hypervisor Platform* off | Only needed for the emulator | Leave it off |

**The key decision: develop on your Galaxy S24 Ultra, not the emulator.** The emulator needs more RAM and GPU than this laptop has. ClearRoom also measures real sound through the phone's microphone, which an emulator can't do. So a real phone is needed anyway, and skipping the emulator saves about 8 GB of disk and several GB of RAM.

---

## Part A — Install Android Studio

Allow 45–60 minutes, mostly for downloads. Keep the laptop on the charger and on good Wi-Fi.

### A1. Download the right version

- **Install Android Studio Rabbit 1 | 2026.2.1** (the current stable release, build 2026.2.1.8).
- Get the Windows **`.exe` installer** from <https://developer.android.com/studio>.
- Don't install *Rabbit 2* (release candidate) or *Rabbit 3* (Canary). Those are previews and can break builds.

### A2. Prepare folders on D:

Your C: drive has enough room for Studio itself (about 3 GB plus a few GB of caches). Everything large goes to D:, which has about 94 GB free. Create these two empty folders in File Explorer:

```
D:\Android\Sdk
D:\gradle-home
```

The SDK grows to 5–10 GB. The Gradle cache (downloaded libraries) grows to 3–6 GB.

### A3. Run the installer

1. Double-click `android-studio-rabbit1-windows.exe` and allow the admin prompt.
2. On **Choose Components**, keep *Android Studio*. **Untick *Android Virtual Device*** (that's the emulator).
3. Keep the default install folder: `C:\Program Files\Android\Android Studio`.
4. Finish, and tick *Start Android Studio*.

### A4. First-run setup wizard

1. "Import settings": choose *Do not import settings*.
2. Install type: choose **Custom**, not Standard. Standard puts the SDK on C:.
3. Choose your theme.
4. **SDK Components Setup:**
   - Set **Android SDK Location** to `D:\Android\Sdk`.
   - Keep ticked: *Android SDK*, *Android SDK Platform* (latest), *Android SDK Platform-Tools* / *Build-Tools*.
   - **Untick** anything named *Android Virtual Device*, *Android Emulator* or *Performance (Android Emulator hypervisor driver)*.
5. Accept the licences (click each licence on the left, then *Accept*) and *Finish*. The downloads take 10–20 minutes.

### A5. Check the SDK Manager

Open *More Actions → SDK Manager* on the Welcome screen, or *Tools → SDK Manager* inside a project.

- **SDK Platforms:** the latest stable Android version should already be ticked. That's all ClearRoom needs to compile. It still runs on Android 10+ through `minSdk 29`.
- **SDK Tools:** *Android SDK Build-Tools*, *Android SDK Platform-Tools*. You don't need the *Google USB Driver* (it's for Pixel phones only; Samsung has its own, see B5).

### A6. Set environment variables

These let Claude build and install the app from the terminal with the same Java that Android Studio uses. Then only one Gradle background process runs instead of two, which matters with 12 GB of RAM.

Start menu → type **"Edit environment variables for your account"** → under *User variables* click **New** for each:

| Variable | Value |
| --- | --- |
| `ANDROID_HOME` | `D:\Android\Sdk` |
| `GRADLE_USER_HOME` | `D:\gradle-home` |
| `JAVA_HOME` | `C:\Program Files\Android\Android Studio\jbr` |

Then select **Path** → *Edit* → *New* → `%ANDROID_HOME%\platform-tools` → OK on all dialogs.

> Your PC also has Oracle **Java 8** on the system Path. Leave it alone: the build tools use `JAVA_HOME`, not whatever `java` is on the Path, so typing `java -version` will still show 1.8 and that's fine.

### A7. Tune Android Studio for this laptop

| Setting | Where | Value | Why |
| --- | --- | --- | --- |
| IDE memory | *Help → Change Memory Settings* | **2048 MB** | Enough for this project and leaves RAM for Gradle |
| Defender exclusions | When Studio shows *"Microsoft Defender may affect performance"*, click **Exclude folders** (needs admin) | project folder, `D:\Android\Sdk`, `D:\gradle-home` | The biggest single build-speed gain on Windows |
| Power mode | Windows *Settings → System → Power* | **Best performance** while plugged in | U-series chips throttle hard on Balanced |
| Plugins | *File → Settings → Plugins* | Disable ones you don't use | Less RAM, faster start-up |
| Gemini in Android Studio | Optional | Your choice | Not needed for this project |

Claude adds these Gradle settings to the project's `gradle.properties` at milestone M1 (listed here so you know what they are):

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true
```

### A8. Check the install

Open a **new** PowerShell window (old windows don't see the new variables) and run:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -version   # should print a JDK 17 or newer (JetBrains Runtime)
adb version                                # should print "Android Debug Bridge version ..."
echo $env:ANDROID_HOME                     # D:\Android\Sdk
```

✅ **Part A is done** when all three commands work.

---

## Part B — Connect your Galaxy S24 Ultra

The S24 Ultra is well above ClearRoom's minimum of Android 10. First install any pending update: *Settings → Software update → Download and install*.

### B1. Turn on Developer options

*Settings → About phone → Software information* → tap **Build number** 7 times → enter your PIN. You'll see "Developer mode has been turned on."

### B2. Turn off Auto Blocker (Samsung-specific, important)

*Settings → Security and privacy → **Auto Blocker*** → turn it **off** (asks for your PIN).
While Auto Blocker is on, it blocks commands sent over a USB cable and greys out USB debugging. That's the most common reason a Samsung phone won't connect. **Turn it back on when you've finished developing.**

### B3. Turn on debugging

*Settings → Developer options* → turn on **USB debugging** and **Wireless debugging**.

### B4. Connect wirelessly (recommended)

This needs no Windows driver and no cable. The laptop and phone must be on the **same Wi-Fi**.

1. In Android Studio: the device dropdown in the toolbar → **Pair Devices Using Wi-Fi** (also in *Device Manager*).
2. On the phone: *Developer options → Wireless debugging → **Pair device with QR code*** → scan the code on the laptop screen.
3. The phone appears in Studio's device dropdown as *Samsung SM-S928…*.

Wireless pairing can drop when the laptop sleeps or the Wi-Fi changes. Just pair again.

### B5. Or connect by USB

1. Install the **Samsung Android USB Driver for Windows** from <https://developer.samsung.com/android-usb-driver>.
2. Use a USB-C cable that carries data (some charging cables don't).
3. On the phone, tap **Allow** on *"Allow USB debugging?"*, and tick *Always allow from this computer*.

### B6. Check the connection

```powershell
adb devices
```

You should see one line ending in **`device`**. If it says `unauthorized`, unlock the phone and accept the prompt. If nothing is listed, see [Troubleshooting](#troubleshooting).

### B7. Phone settings for accurate measurements (needed from milestone M2 onwards)

- *Settings → Apps → ClearRoom → Battery → **Unrestricted***. Samsung's battery manager otherwise puts long noise logs to sleep.
- Turn off *Power saving* while measuring.
- The microphones are on the bottom edge. Don't cover them, and hold the phone the same way for every take. With a thick case, keep it on (or off) consistently.
- On first launch ClearRoom reports whether your S24 Ultra supports the `UNPROCESSED` microphone source (no automatic gain or noise suppression). If it doesn't, the app uses `VOICE_RECOGNITION`, which also has no automatic gain.

✅ **Part B is done** when `adb devices` shows the phone as `device`.

---

## Part C — Create the project

You do this once, in Android Studio. It takes about 5 minutes plus the first sync.

1. Welcome screen → **New Project** → *Phone and Tablet* → **Empty Activity** (the Compose one, with the Compose logo) → *Next*.
2. Fill in:

   | Field | Value |
   | --- | --- |
   | Name | `ClearRoom` |
   | Package name | `com.clearroom.app` |
   | Save location | `C:\Users\DELL\Desktop\assignment_ed5518\ClearRoom` |
   | Minimum SDK | **API 29 ("Q"; Android 10.0)** |
   | Build configuration language | Kotlin DSL (the default) |

3. *Finish*. If asked whether to trust the project, choose **Trust**.
4. Wait for the first **Gradle sync**. Expect 10–20 minutes the first time, because it downloads libraries into `D:\gradle-home`. The status bar at the bottom shows progress.
5. Pick your **S24 Ultra** in the device dropdown and press **Run ▶**. The template's *"Hello Android!"* screen appears on the phone.
6. Tell Claude it worked. Claude then:
   - checks the build from the terminal (`gradlew.bat assembleDebug`);
   - runs `git init` in `assignment_ed5518\`, so the requirements, the skills and the app are in one repository;
   - makes the first commit.

✅ **Part C is done** when "Hello Android!" shows on the phone and the first commit exists.

---

## Part D — Build plan

### How each milestone runs

1. **Claude** writes the code for the milestone, following the listed skills, **with tests for every new function in the same change** (the `test-every-function` skill). A milestone isn't done while `find_untested.py` still lists untested functions without a stated reason.
2. **Claude** runs `gradlew.bat assembleDebug test lintDebug` from the terminal and fixes anything that fails. Tests that need the phone (microphone, files, UI) run with `gradlew.bat connectedAndroidTest` at the end of each milestone. They run in a separate test copy of the app (`com.clearroom.app.uitest`), which is installed and removed by the test run, so **your own ClearRoom and its saved rooms are never touched**.
3. **You** install it on the phone (press *Run* in Studio, or Claude runs `adb install`), try the "done when" check, and say what you see.
4. Claude fixes what you report, then makes **one commit** for the milestone. One commit per milestone means any one of them can be rolled back.

Builds on this laptop: the first full build takes a few minutes, later builds about 30–90 seconds. While Claude builds from the terminal, avoid also pressing *Build* in Studio, because two builds at once can run out of RAM.

### Rules from the requirements doc

- Build against the **sample room** first. Every chart must work with sample data before the microphone is used again.
- Make every view exist before polishing any of them.
- If time runs short, push these to the testing phase, in this order: YAMNet sound labels → PDF report → V12 animation.

### Milestones

**Day 1 of the doc's plan: engine, measurements, 3D core**

- [x] **M1 — Skeleton.** Material 3 theme (light and dark), fixed clarity-band colours, 5-tab bottom navigation (Room, Measure, Results, Fixes, Log), microphone permission flow, Gradle tuning. *(Done Oct 26: 12 laptop + 8 phone tests pass.)*
  *Skills:* clearroom-android-foundation, kotlin-solid-clean-code.
  *Done when:* the app installs on the phone, the tabs switch, and the permission prompt appears on the first measurement.
- [x] **M2 — Recorder.** `AppContainer` and `AppViewModel` (moved from M1: they first hold real state here), `AudioRecord` at 48 kHz mono, microphone source selection, auto-gain and noise suppression off, live level meter, clipping and busy-microphone checks. *(Done Oct 26: 54 laptop + 19 phone tests pass. Finding: the S24 Ultra doesn't offer the raw `UNPROCESSED` source, so ClearRoom uses `VOICE_RECOGNITION` at 48 kHz float and switches off the noise suppressor and echo canceller it finds attached; no auto-gain is attached on that source. A phone call takes the microphone and other apps get silence, which ClearRoom reports as "No sound came through".)*
  *Skills:* android-audio-capture.
  *Done when:* the Measure tab records 5 s with a moving level bar and shows which microphone source the S24 Ultra uses.
- [x] **M3 — Data and Room tab.** Data models, JSON store, room form, seat grid, built-in sample room. *(Done Oct 26: 117 laptop + 40 phone tests pass. Rooms are saved atomically as one JSON file each, named by id; the last saved room reopens on start; a damaged file is listed separately and can be deleted. Surfaces follow FR-1's pick list per floor, walls and ceiling.)*
  *Skills:* storage-and-pdf-report, clearroom-android-foundation.
  *Done when:* a room survives closing and reopening the app, and the sample room loads.
- [x] **M4 — Filters and levels.** Biquad filters, 7-band octave bank, noise snapshot, voice reference. *(Done Oct 26: 153 laptop + 50 phone tests pass. Measure tab: pick the seat, set the fan state, measure 30 s of noise (FR-4; Part E decision 1) or 20 s of voice at 1 m (FR-5); each reading is saved into the room as 7 band levels plus an approximate A-weighted total. The sample room can't be measured into: save your own copy first. Phone tests now run in a separate test app so they never erase your rooms.)*
  *Skills:* acoustics-dsp.
  *Done when:* the noise and voice measurements each show 7 band numbers.
- [x] **M5 — Echo analysis.** RT60 per band from a clap, Schroeder curve, quality messages ("clap louder", "too noisy", "clipped"), 3 takes per seat. *(Done Oct 26: 201 laptop + 58 phone tests pass. 160 synthetic claps show no bias in any band; the 3-clap seat result meets NFR-3 in 250 Hz–4 kHz and 8 kHz. Clap countdown 3 s by default, 10 s optional. Waiting on your real-clap check.)*
  *Skills:* acoustics-dsp, clearroom-testing.
  *Done when:* unit tests on synthetic claps meet NFR-3 (±10 % or ±0.1 s), and a real clap in a normal room gives RT60 between 0.3 and 2 s.
- [x] **M6 — Clarity engine.** STI, Sabine fit, speech level vs distance, limiting cause, what-if, required voice level. *(Done Oct 26: 254 laptop tests pass, including all 15 golden STI values from the independent reference and 4 new direct-sound ones. Sample room: front row 0.59, back row 0.58, fair, limited by echo; 20 m² of curtains lifts the back row to 0.65. 58 phone tests pass on the S24 Ultra. Cause rule awaiting your confirmation.)*
  *Skills:* sti-room-model, clearroom-testing.
  *Done when:* the golden STI tests pass within ±0.02 (NFR-4), and the sample room's back row scores below its front row.
- [x] **M7 — 3D core.** `Scene3D` (camera, projection, drag to rotate, pinch to zoom, shading) and V8, the 3D clarity room. *(Done Oct 26: 314 laptop + 69 phone tests pass. The Results tab shows V8 with a colour legend and names each band in words; drag turns the room, pinch zooms, double-tap returns to the home view behind the back row. TalkBack reads the front and back row bands. Before a clap and a noise reading exist, the tab lists what's still missing. Waiting on your smooth-rotation check on the phone.)*
  *Skills:* compose-3d-scene.
  *Done when:* the sample room's bars rotate smoothly on the phone.

*End of Day 1 in the doc's terms:* one real seat measured end to end, with its numbers showing in the 3D room.

**Day 2 of the doc's plan: all views and features**

- [x] **M8 — Results tab.** Verdict card, V1 seat heatmap, V2 cause map, seat detail sheet, V9 clarity terrain, V10 echo waterfall, V3 decay curve, V4 RT60 bars, V13 MTF curves, V14 speech vs distance, V15 octave spectrum. *(Done Oct 26: 407 laptop + 77 phone tests pass. Results tab: verdict card in plain words with its range; 3D room / seat map (V1) / cause map (V2) toggle, tap a seat for its details and V15; then V9 terrain, V3 decay curve, V4 echo time by pitch, V10 echo waterfall, V13 speech rhythm and V14 voice vs noise by distance. Every chart checked on the S24 Ultra with the sample room in dark mode. Each seat now keeps its latest clap's curves (numbers only) for V3 and V10.)*
  *Skills:* compose-charts-2d, compose-3d-scene.
  *Done when:* every Results card renders with the sample room in light and dark mode.
- [x] **M9 — Fixes tab.** What-if sliders, V6 fix ranking, V7 required voice, V12 what-if morph animation. *(Done Oct 26: 438 laptop + 86 phone tests pass. Fixes tab: a before/after summary, the room in 3D with bars gliding to their new scores (V12), sliders and switches for curtains, students, fans, a portable mic, the speaker's voice and where they stand, the ranking of single fixes for the back row (V6) and the voice needed per row (V7). Sample room: 40 m² of curtains plus a mic lift the back row from fair 0.57 to good 0.70. Waiting on your real ₹ prices for the fixes.)*
  *Skills:* sti-room-model, compose-charts-2d, compose-3d-scene.
  *Done when:* moving a slider changes the predicted STI and the 3D bars animate.
- [x] **M10 — Log tab.** All-day noise log (1-s levels, no audio stored), YAMNet sound labels, keep-screen-on, V5 day timeline, V11 noise landscape. *(Done Oct 27: 499 laptop + 101 phone tests pass. Log tab: start/stop with the "Recording levels, not audio" banner, the screen kept on while logging, 1-s band levels with filters that keep running, YAMNet sound labels on the phone (MediaPipe Tasks Audio 1.1.1, model bundled, SHA-256 checked by a test), per-period Leq and L90, merged events, back-row STI per period (FR-10), V5 timeline, V11 landscape, and Clear logs. Checked on the S24 Ultra with a 71-second real log (fan labelled, about 72 dBA); a 10-minute log in a classroom is part of the M13 field tests.)*
  *Skills:* noise-day-log.
  *Done when:* a 10-minute test log shows hourly stats and labelled events, with the "Recording levels, not audio" banner visible.
- [x] **M11 — Report and before/after.** One-page PDF with share, before/after comparison (FR-13). *(Done Oct 27: 530 laptop + 131 phone tests pass. "Share report (PDF)" on the Results tab writes one A4 page (verdict with range, seat map, echo time by pitch, top 3 fixes, measurement conditions, the STI scale explained) and opens the share sheet through a FileProvider that reaches only the report folder. "Re-measure after a change" keeps the readings as 'before'; the Results tab and the PDF then show both seat maps and both echo charts on one scale. Checked on the S24 Ultra: the share sheet opened with the PDF attached, and the PDFs for the sample room and a before/after room were each one A4 page that fitted.)*
  *Skills:* storage-and-pdf-report.
  *Done when:* the PDF opens from the share sheet, and before/after maps use the same colour scale.
- [x] **M12 — Finish.** Empty states, error text, app icon and name, release APK, full bug and security audit. *(Done Oct 27: 536 laptop + 133 phone tests pass, lint clean. New look chosen by the owner: light and dark with the phone, lime on ink, Manrope; hero cards for the verdicts, a floating tab bar and a new adaptive icon; the empty states and error messages every tab already had now use it. APK capped at 95 MB by the build (ARM only: release 34 MB, debug 65 MB). Release signed with the debug key (owner's choice) and shrunk as a whole with keep rules. Audit in docs/ClearRoom-Audit-M12.md: 2 High (MediaPipe added the INTERNET permission; the template's partial shrinking made the release APK crash on start) and 2 Low, all fixed with tests. The release APK installs over the debug app, starts, shows the sample exactly as the debug build does, shares the PDF, and runs a day log with sound labels (a 16-second log in a throwaway room labelled the fan, about 67 dBA; the room was deleted afterwards).)*
  *Skills:* bug-vulnerability-audit.
  *Done when:* the audit reports no Critical or High findings and the release APK installs on the phone.

**After the build: testing**

- [ ] **M13 — Self-test and field tests.** In-app self-test screen (synthetic claps and known STI cases), then the doc's field protocol in 3–4 rooms: echo repeatability, distance model, curtain test, fan test, listening check, usability. *(In progress, Oct 27: the self-test (30 cases, all passing on the S24 Ultra) and the field-test tools are built; 573 laptop + 146 phone tests pass. The fieldwork in 3–4 rooms is next, following docs/ClearRoom-Field-Tests.md.)*
  *Skills:* clearroom-testing.
  *Done when:* the doc's testing table has a pass/fail result for every row.

**Demo scenario (from the doc):** measure a seminar room live with three claps, show the seat map with a red back zone labelled "echo", run the what-if for curtains, then hang a blanket and re-measure to show the predicted improvement come true.

---

## Part E — Decisions to confirm

The requirements doc contradicts itself or leaves gaps in a few places. Claude uses the defaults below unless you change them. Each value is a single constant in the code, so changing one later is a one-line edit.

| # | Question | Default | Why | Your call |
| --- | --- | --- | --- | --- |
| 1 | Noise snapshot length: FR-4 says 30 s, the parameters table says 10 s | **30 s** | Steadier reading with intermittent noise; still fits the 10-minute check | |
| 2 | Clap rejection threshold: FR-2 says 25 dB, the parameters table says 30 dB | **30 dB** | The echo fit needs headroom above the noise floor | |
| 3 | Day log: build plan says screen on, NFR-9 says screen off | **Screen on, phone on the charger** | Screen-off needs an extra background service; can be added later | |
| 4 | Main cause: does "too far from the speaker" override echo vs noise? | **Yes, distance first** | Reads the doc's wording literally | |
| 5 | Back-row score: worst seat or average of the row? | **Worst seat** | The goal is that *every* seat can follow | |
| 6 | Fix costs in ₹, and absorption values for tile, glass, wood and soft board | **Placeholders, clearly marked** | Not in the doc; please supply real numbers or a source table | |

---

## Part F — Risks on this laptop

| Risk | What you'd notice | What to do |
| --- | --- | --- |
| Low RAM (12 GB) | The laptop freezes during builds; "Out of memory" in the Build window | One build at a time; close browser tabs; keep the 2 GB IDE and Gradle limits from A7 |
| C: drive filling up | Windows warns about low space | Keep the SDK and Gradle cache on D: (A2 and A6). Keep at least 10 GB free on C: |
| Slow first sync | 10–20 min on "Gradle sync" | One-time. Do it on good Wi-Fi with the charger connected |
| Wireless debugging drops | The phone disappears from the device list | Pair again (B4), or use a USB cable (B5) |
| Samsung blocks USB | USB debugging greyed out | Auto Blocker is still on (B2) |

---

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `adb` is not recognised | Open a new terminal after A6. Check that `D:\Android\Sdk\platform-tools\adb.exe` exists |
| `adb devices` shows `unauthorized` | Unlock the phone and accept the debugging prompt. If no prompt appears: Developer options → *Revoke USB debugging authorizations*, then reconnect |
| `adb devices` shows nothing over USB | Install the Samsung USB driver (B5); try another cable or port; turn off Auto Blocker (B2) |
| USB debugging is greyed out | Auto Blocker is on (B2) |
| "SDK location not found" | Studio: *File → Project Structure → SDK Location* → `D:\Android\Sdk` |
| Gradle says it needs Java 17 or newer | `JAVA_HOME` isn't set, or the terminal predates A6. In Studio: *Settings → Build Tools → Gradle → Gradle JDK* → *jbr* |
| Build is very slow | Check the Defender exclusions (A7), Best performance power mode, charger connected |
| The app installs but the screen is blank | Tell Claude: it will read the crash log with `adb logcat` |
| Phone tests fail with "No compose hierarchies found" | The phone locked during the run. Keep it unlocked; set *Screen timeout* to 10 minutes or turn on *Developer options → Stay awake* while charging |
| Gradle says "There were failing tests" but the report shows all passed | Seen when the phone was connected as `IP:port` (manual `adb connect`). Run `adb disconnect`, let it reconnect by itself (`adb devices` shows `adb-…_adb-tls-connect._tcp`), and rerun |

---

## Sources

- [Android Studio download (Rabbit 1, 2026.2.1)](https://developer.android.com/studio)
- [Install Android Studio and system requirements](https://developer.android.com/studio/install)
- [Android Emulator hardware acceleration](https://developer.android.com/studio/run/emulator-acceleration), the reason the emulator is skipped on this laptop
- [Samsung Android USB Driver](https://developer.samsung.com/android-usb-driver)
- [Samsung forum: S24 Ultra USB debugging greyed out by Auto Blocker](https://forum.developer.samsung.com/t/s24-ultra-developer-option-usb-debugging-greyed-out/29526)
