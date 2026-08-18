# HushWake

<p align="center">
  <img src="docs/assets/hushwake-hero.svg" alt="HushWake: sound, routed where it belongs" width="100%">
</p>

<p align="center">
  <a href="https://github.com/chenzhiyong1994/hush-wake/actions/workflows/android-ci.yml"><img src="https://github.com/chenzhiyong1994/hush-wake/actions/workflows/android-ci.yml/badge.svg" alt="Android CI"></a>
  <img src="https://img.shields.io/badge/status-0.3.4--beta-f6bf6f" alt="0.3.4 beta">
  <img src="https://img.shields.io/badge/Android-12%2B-e9ff70?logo=android&logoColor=09110f" alt="Android 12+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-8fb8a8" alt="Apache License 2.0"></a>
</p>

<p align="center">
  <strong>English</strong>
  ·
  <a href="README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  A safety-first Android alarm and sleep-sound app
</p>

Alarms are very good at waking one person—and often everyone else in the room.

HushWake tackles a deceptively simple problem: **use normal speaker output when no headphones are present, but play through headphones only after the current audio route has been verified as safe.** If the headphones disconnect, the route changes, or Android cannot provide enough evidence, HushWake mutes first and stops instead of silently falling back to a speaker.

> [!IMPORTANT]
> `0.3.4-beta` contains the complete core feature set, but it remains a functional beta rather than a publicly validated stable release. Real-device, zero-speaker-leakage testing is still a release gate; a successful build does not prove that every device is safe.

## Why HushWake

During a nap, commute, or shared living situation, people may need a reliable alarm without the risk of audio suddenly coming from the phone speaker. Android exposes concepts such as a preferred output device, but a preference is not proof of the actual and exclusive route. Bluetooth disconnects and system rerouting can also race with playback.

HushWake therefore takes a deliberately conservative approach:

- **Fail closed.** If a headphone session cannot verify its actual route, playback remains muted.
- **Keep intent separate from evidence.** The target device, current candidates, and actual route are evaluated independently.
- **Mute before stopping.** On disconnect or uncertainty, player gain is reduced to zero before playback stops and any user-approved vibration or notification fallback runs.
- **Keep private data local.** There is no account, cloud sync, or behavioral upload; raw headphone names and addresses are not persisted.

## What works today

| Area | Current implementation |
| --- | --- |
| Alarms | One-time and weekly schedules, exact scheduling, reboot/time-change recovery, lock-screen entry, high-priority notification, stop, one snooze, and maximum ringing duration |
| Smart output | Normal media output when no headphones are present; guarded routing when exactly one headphone device is detected; unsafe routes are blocked |
| Headphone verification | Dual-layer muted startup, low-volume confirmation on the current device, actual-route revalidation, and stop-on-disconnect/focus-loss/multiple-candidate behavior |
| Sleep sounds | Six real ambient recordings with pause, notification controls, 15–60 minute timer, fade-out, and instant sound switching without resetting the timer |
| Alarm sounds | Six AOSP alarm sounds with instant preview in the editor and continuous looping while ringing |
| Data | Local SQLite and SharedPreferences; Android backup and device transfer disabled; no network or account layer |

App volume follows the system media volume and never raises it automatically. All audio is bundled for offline use, with sources and redistribution terms documented in [Audio sources and licenses](docs/audio-credits.md).

## Project status

| Item | Status |
| --- | --- |
| Android 12 / API 31+ implementation | Complete |
| JVM state-machine, scheduling, and policy tests | Covered |
| Lint and Debug APK build | Local and CI gate |
| Emulator cold start, primary flow, and background launch | Automated regression scripts available |
| Wired / A2DP / USB / LE headphone matrix | **Real-device evidence is still being expanded** |
| Public stable release / app-store distribution | **Not yet available** |

HushWake cannot guarantee an alarm when a device is powered off, out of battery, or the app has been force-stopped. It does not claim to treat insomnia or provide any medical benefit. See the [PRD](docs/hush-wake-prd.md) and [real-device test guide](docs/device-test-guide.md) for the complete boundaries.

## Quick start

Requirements:

- JDK 17
- Android SDK 36
- Windows PowerShell (the repository's verification scripts currently target PowerShell)

Clone and build:

```powershell
git clone https://github.com/chenzhiyong1994/hush-wake.git
cd hush-wake
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The Debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

With an ADB device or emulator connected, run the relevant regression scripts:

```powershell
.\scripts\verify-app-launch.ps1
.\scripts\smoke-emulator.ps1
.\scripts\verify-background-alarm.ps1
```

Alarm-audio continuity checks require `ffmpeg` and `ffprobe`:

```powershell
.\scripts\verify-alarm-audio.ps1
```

Emulator results cannot prove zero speaker leakage on real headphones. Claims about private headphone playback must be validated with the [real-device test guide](docs/device-test-guide.md).

## Code map

```text
app/src/main/java/com/hushwake/app/
├── alarm/       Alarm scheduling, triggers, ringing service, and stop policy
├── audio/       Route checks, device fingerprints, safety engine, and player
├── data/        Local database and preferences
├── domain/      Alarm and device-verification domain rules
├── guard/       Fail-closed muting and action ordering
├── noise/       Sleep-sound catalog, timers, and foreground service
└── ui/          Lightweight native Android UI
```

Further reading:

- [Product requirements and safety constraints](docs/hush-wake-prd.md)
- [Android implementation and verification notes](docs/android-implementation.md)
- [Real-device test guide](docs/device-test-guide.md)
- [Audio sources and third-party licenses](docs/audio-credits.md)

## Contributing

The most valuable contribution is often not another button, but stronger evidence that HushWake behaves safely on a real device:

- Routing compatibility evidence for new Android versions and device vendors;
- Regression tests for headphone connect/disconnect races, audio-focus changes, and multiple output candidates;
- Diagnostics and observability that do not collect private information;
- Alarm reliability, accessibility, and native interaction improvements;
- Suggestions for clearly licensed audio that can be redistributed offline.

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Report security issues privately through [GitHub Private Vulnerability Reporting](SECURITY.md). Never include Bluetooth names, MAC addresses, exact sleep schedules, or stable device identifiers in issues or logs.

Issues and pull requests are welcome in either English or Chinese.

## License

HushWake source code is licensed under the [Apache License 2.0](LICENSE).

Audio assets under `app/src/main/res/raw/` retain their respective CC0, CC BY 4.0, or Apache-2.0 licenses and are not relicensed by the repository's main license. Authors, sources, modifications, and terms are documented in [docs/audio-credits.md](docs/audio-credits.md).

---

<p align="center">
  If this direction solves a problem you share, try a build, review the safety model, or bring evidence from a real device.
</p>
