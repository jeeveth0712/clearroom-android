# ClearRoom audit — 2026-10-27 — whole project (M12)

## Summary

Four findings: two **High**, two **Low**. All four are fixed, and each fix has a test or check.

The first High one is a privacy leak through a library. MediaPipe, used for the day log's sound labels, merged the
**INTERNET** permission into the app and sends its own usage statistics to Google. That broke NFR-8 ("all
analysis runs on the phone") and the app's "no internet" promise. The permission is now removed, so Android
blocks every connection, and a phone test checks that the installed app never requests it.

The second High one: the **release APK crashed on start**. The release build's code shrinker was limited to
three package groups (the template's setting), which broke a renamed Kotlin class. Debug builds and every test
run unshrunk, so only installing the release APK showed it. The whole app is now shrunk, with keep rules for
MediaPipe and protobuf, and the release build was checked on the phone.

No Critical findings. No audio reaches storage, logs or the PDF (NFR-6 holds).

## Findings

### 1. [High] MediaPipe adds the INTERNET permission and sends usage statistics — CONFIRMED, fixed

- **Where:** the merged manifest. `com.google.mediapipe:tasks-core:1.1.1` depends on
  `com.google.android.datatransport:transport-backend-cct`, whose manifest declares `INTERNET` and
  `ACCESS_NETWORK_STATE`.
- **What goes wrong:** every `TaskRunner.create` (our `YamnetLabeller.create`) builds a
  `TasksStatsProtoLogger`. That logger sends task statistics to Google's Clearcut service through `RemoteLoggingClient`:
  session start and end, latency, app version and device information. These are never audio or sound levels.
  There is no option to switch it off.
- **Scenario:** start a day log (M10 onwards) with the phone online → MediaPipe statistics leave the phone.
- **Evidence:** `uses-permission android.permission.INTERNET` in
  `app/build/intermediates/merged_manifest/release/.../AndroidManifest.xml`; `javap` on
  `TaskRunner.create` → `TasksStatsLoggerFactory.create` → `TasksStatsProtoLogger` → `RemoteLoggingClient`
  (`TransportRuntime`, `CCTDestination`).
- **Fix:** `AndroidManifest.xml` removes the permission with
  `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`. Android then refuses
  every socket from the app, and the statistics stay queued on the phone until they expire.
  `ACCESS_NETWORK_STATE` stays: it can't send anything, and the library's retry scheduler needs it.
- **Test:** `PermissionsTest.theAppCantGoOnline` (phone) reads the installed package's requested permissions.
  `YamnetLabellerTest` still passes, so the labels work without the network.

### 2. [High] The release APK crashed on start — CONFIRMED, fixed

- **Where:** `app/build.gradle.kts`, `release { optimization { packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**") } }`
  (from the project template).
- **What goes wrong:** R8 optimised only those packages. It renamed a Kotlin collection class in a way a class
  outside the scope couldn't reach, so the app died while starting:
  `IllegalAccessError: Class kotlin.collections.j extended by class li is inaccessible`
  (in `androidx.startup.InitializationProvider`).
- **Scenario:** install `app-release.apk` (the APK to submit) and open it → crash before any screen.
- **Evidence:** the phone's crash log after `adb install -r app-release.apk`.
- **Fix:** no `packageScope`, so R8 shrinks the whole app; `app/proguard-rules.pro` keeps MediaPipe, protobuf,
  Flogger and `datatransport` whole (none ship keep rules; MediaPipe's native code calls Java by name), plus
  R8's four `-dontwarn` lines for classes MediaPipe refers to but never uses here. The APK also dropped to 34 MB.
- **Check:** the release APK installed over the debug app (same key, data kept) starts and shows the sample
  room exactly as the debug build does ("Fair · 0.57–0.58", main cause echo, front rows "Fair · 0.59"). It also
  writes the PDF and opens the share sheet, with no crash in the log.

### 3. [Low] Old protobuf and Guava inside MediaPipe — PLAUSIBLE (not reachable), fixed

- **Where:** `protobuf-javalite 4.26.1` (CVE-2024-7254, a crash on deeply nested input) and
  `guava 27.0.1-android` (CVE-2020-8908 and CVE-2023-2976, temporary-file permissions), both pulled in by MediaPipe 1.1.1.
- **Why Low:** ClearRoom never parses protobuf from outside the app (only MediaPipe's own graph and the bundled
  model), and never uses Guava's temporary-file helpers.
- **Fix:** Gradle dependency constraints pin `protobuf-javalite 4.36.2` and `guava 33.7.2-android`. osv.dev lists
  no known vulnerabilities for either. **Check:** the release build resolves to them, and the YAMNet phone tests pass.

### 4. [Low] Backups were the template's — CONFIRMED, fixed

- **Where:** `res/xml/data_extraction_rules.xml` and `backup_rules.xml` were Android Studio's samples, so
  everything in the app's storage was backed up, including the queued MediaPipe statistics.
- **Fix:** both files now include only `rooms/` and `logs/`. These are numbers and labels, never audio, and
  worth keeping when the phone changes. The PDF reports live in the cache, which is never backed up.
- **Test:** `BackupRulesTest` (laptop) parses both files.

## Accepted by the owner

- **Release signing:** the release APK is signed with the Android debug key, by the owner's choice (M12). It
  installs on any phone and leaves nothing secret to keep. A store release would need a real key.

## Tool results

| Tool | Result | Notes |
| --- | --- | --- |
| Android Lint | 0 issues | `ChromeOsAbiSupport` disabled on purpose (ARM phones only, APK cap) |
| Kotlin compiler | 0 warnings | release, laptop-test and phone-test sources recompiled with `--rerun` |
| Semgrep 1.182.1 (CLI) | 0 findings | 53 rules on 97 files: the project rules (validated), `p/kotlin`, `p/secrets`; metrics off |
| mobsfscan | 8 notes → 1 finding (#3) | 7 dropped, see below |
| osv.dev (OSV-Scanner's database) | 10 known issues → 1 finding (#2) | 227 resolved packages; 8 issues are in the laptop build tools, not the APK |
| Gradle wrapper | genuine | `gradle-wrapper.jar` matches Gradle's published checksum for 9.1.0; `distributionSha256Sum` is set |
| Unit tests (laptop) | all pass | see AGENTS.md for the counts |
| Phone tests | 133 pass | including `PermissionsTest` and the YAMNet tests with the newer protobuf |
| Release APK on the phone | starts, sample identical to debug, PDF + share sheet work | finding #2 found this way |

## Manual checklist (references/checklists.md)

- **Numeric:** every `log10` has an energy floor or a guard. `StiBand.from(NaN)` refuses loudly instead of
  returning a band. Day-log averages never see an empty period.
- **Concurrency:** the microphone, audio effects and the MediaPipe classifier are released in `finally`
  (M2, M10). No `GlobalScope`, no `runCatching` around suspend code. Only one recording at a time.
- **State:** no arrays in data classes, `update { }` everywhere, and derived results carry their inputs.
- **Storage:** `AtomicFile` for rooms; NaN is written as `null`; file names come from ids, never from typed
  names; day logs are append-only.
- **Security:** only the launcher activity is exported (plus AndroidX's profile installer, guarded by the
  system-only DUMP permission). The FileProvider exposes `cache/reports/` only, and a test proves the room files
  are unreachable.
- **Privacy (NFR-6):** the only file stream writes the PDF; no audio buffer is kept or logged; the PDF and the
  room files hold only numbers and labels.
- **Supply chain:** only Google, Maven Central and the Gradle plugin portal; project repositories are refused;
  no floating versions; the YAMNet model's SHA-256 is tested.
- **Release:** R8 on for the whole app (finding #2), not debuggable, `targetSdk 37`, and the APK is capped at
  95 MB by the build (34 MB now).

## Not run

- **detekt and ktlint:** running the downloaded `detekt-cli` jar was refused by this session's permission
  check. To enable: allow it, or add the Gradle plugins (a build change to agree first).
- **Snyk MCP:** needs a Snyk account.
- **Semgrep MCP:** didn't connect in this session; the Semgrep CLI ran the same rules instead.
- **OSV-Scanner binary:** needs Gradle lockfiles (a build change). The same osv.dev database was queried
  directly with the resolved dependency list instead.

## Dropped candidates (9)

- **mobsfscan `android_task_hijacking2` (StrandHogg 2.0):** only affects targetSdk < 29. ClearRoom targets 37
  and needs Android 10 (29) or later.
- **mobsfscan certificate transparency and SSL pinning:** the app has no network connections.
- **mobsfscan screenshot prevention, root detection, SafetyNet, tapjacking:** banking-app hardening, not
  ClearRoom's risks; nothing secret is on screen.
- **osv.dev hits in Bouncy Castle, jose4j, JDOM2, Commons Lang and the build-tool copy of Guava:** these are
  in the Android Gradle plugin's classpath on the laptop, not in the APK.
- **Wrapper jar checksum ≠ Gradle 9.6.0's:** it is Gradle 9.1.0's official wrapper jar (the template's). The
  wrapper jar is version-independent, and the distribution itself is pinned by `distributionSha256Sum`.
