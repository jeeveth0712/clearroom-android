# ClearRoom release APK

[ClearRoom-latest.apk](ClearRoom-latest.apk) is the signed release build for the assignment, built on 2026-10-27 from this repository. It was installed over the existing app on the Samsung Galaxy S24 Ultra; Room, Results and Fixes opened successfully.

| Detail | Value |
| --- | --- |
| Package | `com.clearroom.app` |
| Version | `1.0` (`versionCode` 1) |
| Android | API 29 or later; targets API 37 |
| CPU | ARM64 and ARMv7 |
| Size | 34,080,732 bytes (below the 95 MB project cap) |
| Signature | Android debug key, as chosen for this assignment; APK Signature Scheme v2 verified |
| SHA-256 | `3B6BF1939A8A17ED63C966317F9A3E9E548F9BBB5CFF14D2AAD9E579B56BAB05` |

Install with `adb install -r dist/ClearRoom-latest.apk` from the repository root. The same signature lets it replace the current debug or release app without clearing saved rooms.

The current source passes 582 JVM tests, 146 on-phone tests, and Android lint with no issues. The one-minute demo script and shot list are in [docs/ClearRoom-Demo-Video.md](../docs/ClearRoom-Demo-Video.md). M13's real-room fieldwork is still the owner's next step.
