# HushWake

<p align="center">
  <img src="docs/assets/hushwake-hero.svg" alt="HushWake: sound, routed where it belongs" width="100%">
</p>

<p align="center">
  <a href="https://github.com/chenzhiyong1994/hush-wake/actions/workflows/android-ci.yml"><img src="https://github.com/chenzhiyong1994/hush-wake/actions/workflows/android-ci.yml/badge.svg" alt="Android CI"></a>
  <a href="https://github.com/chenzhiyong1994/hush-wake/actions/workflows/ios-ci.yml"><img src="https://github.com/chenzhiyong1994/hush-wake/actions/workflows/ios-ci.yml/badge.svg" alt="iOS CI"></a>
  <img src="https://img.shields.io/badge/Android_beta-0.4.8-f6bf6f" alt="Android 0.4.8 beta">
  <img src="https://img.shields.io/badge/iOS_beta-0.1.0-55cfc2" alt="iOS 0.1.0 beta">
  <img src="https://img.shields.io/badge/Android-12%2B-e9ff70?logo=android&logoColor=09110f" alt="Android 12+">
  <img src="https://img.shields.io/badge/iOS_%2F_iPadOS-17%2B-55cfc2?logo=apple&logoColor=09110f" alt="iOS / iPadOS 17+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-8fb8a8" alt="Apache License 2.0"></a>
</p>

<p align="center">
  <strong>English</strong>
  ·
  <a href="README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  A safety-first alarm and sleep-sound app for Android and iOS
</p>

<p align="center">
  <a href="https://chenzhiyong1994.github.io/hush-wake/"><strong>Explore the project homepage</strong></a>
  ·
  <a href="https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-0.4.8-beta.apk"><strong>Download Android APK</strong></a>
  ·
  <a href="https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-iOS-0.1.0-beta-unsigned.ipa"><strong>Download iOS IPA (unsigned)</strong></a>
</p>

Alarms are very good at waking one person—and often everyone else in the room.

HushWake tackles a deceptively simple problem: **use normal speaker output when no headphones are present, but play through headphones only after the current audio route has been verified as safe.** If the headphones disconnect, the route changes, or the operating system cannot provide enough evidence, HushWake mutes first and stops instead of silently falling back to a speaker.

> [!IMPORTANT]
> Android `0.4.8-beta` and iOS `0.1.0-beta` are open-source functional betas. Real-device, zero-speaker-leakage testing remains a stable-release gate; passing builds and automated tests do not prove safety on every device.

## Choose your platform

| Platform | Version and requirements | Download and install |
| --- | --- | --- |
| Android | `0.4.8-beta` · Android 12+ | [Signed APK](https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-0.4.8-beta.apk), ready to install |
| iPhone / iPad | `0.1.0-beta` · iOS / iPadOS 17+ | [Unsigned IPA](https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-iOS-0.1.0-beta-unsigned.ipa), requires your own signing; [installation guide](docs/ios-install.md) |
| iOS development and simulator | macOS / Xcode | [iOS source archive](https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-iOS-0.1.0-beta-source.zip) · [Simulator build](https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-iOS-0.1.0-beta-simulator.zip) |

Both platforms are distributed as open source through GitHub; iOS does not require App Store submission. **Audible iOS alarms require the app to stay in the foreground. Background and lock-screen reminders are silent notifications; sleep audio supports background playback.** Review this limit before installing. Checksums are on the [release download page](https://github.com/chenzhiyong1994/hush-wake/releases/tag/v0.4.8-beta).

## Why HushWake

During a nap, commute, or shared living situation, people may need a reliable alarm without the risk of audio suddenly coming from the phone speaker. Android exposes concepts such as a preferred output device, but a preference is not proof of the actual and exclusive route. Bluetooth disconnects and system rerouting can also race with playback.

HushWake therefore takes a deliberately conservative approach:

- **Fail closed.** If a headphone session cannot verify its actual route, playback remains muted.
- **Keep intent separate from evidence.** The target device, current candidates, and actual route are evaluated independently.
- **Mute before stopping.** On disconnect or uncertainty, player gain is reduced to zero before playback stops and any user-approved vibration or notification fallback runs.
- **Keep private data local.** There is no account, cloud sync, or behavioral upload; raw headphone names and addresses are not persisted.

## What works today

The feature table below describes **Android**. The native iOS beta and its platform limits are described in the next section.

| Area | Current implementation |
| --- | --- |
| Alarms | One-time and weekly schedules, exact scheduling, a single next-alarm focus card, reboot/time-change recovery, lock-screen entry, high-priority notification, stop, one snooze, and maximum ringing duration |
| Smart output | Normal media output when no headphones are present; guarded routing when exactly one headphone device is detected; unsafe routes are blocked |
| Headphone routing | Automatic dual-layer muted startup and actual-route verification; no separate manual test page; disconnect, focus loss, or multiple candidates stop playback |
| Sleep sounds | Eight real ambient recordings with textured cards, pause, notification controls, 5–120 minute timer, fade-out, and instant sound switching without resetting the timer |
| Alarm sounds | Six AOSP alarm sounds with instant preview in the editor and continuous looping while ringing |
| Identity | Adaptive B1-A launcher icon with mask-safe spacing, Android themed icon, and a branded animated cold-start transition |
| Data | Local SQLite and SharedPreferences; Android backup and device transfer disabled; no network or account layer |

App volume follows the system media volume and never raises it automatically. All audio is bundled for offline use, with sources and redistribution terms documented in [Audio sources and licenses](docs/audio-credits.md).

## iOS 0.1.0-beta

The repository now includes a native **SwiftUI app for iOS / iPadOS 17+**: one-time and weekly alarms, one snooze, six alarm sounds, eight offline sleep sounds, a sleep timer with fade-out, and local storage. **Audible alarms require the app to remain in the foreground; background reminders are silent system notifications.** Sleep audio supports normal background playback.

Only the built-in speaker or one explicitly identified wired-headphone route may play. Ambiguous Bluetooth/USB routes, AirPlay, car audio and unknown outputs are blocked. Route changes mute before stopping; real-device zero-leakage validation is still pending.

Download the iOS attachments from the [current beta download page](https://github.com/chenzhiyong1994/hush-wake/releases/tag/v0.4.8-beta), or get the build artifacts from [iOS CI](https://github.com/chenzhiyong1994/hush-wake/actions/workflows/ios-ci.yml). The device **IPA is unsigned and requires your own signing before installation**. A simulator build and dedicated iOS source archive are also provided; the existing Android release tag's automatic source archives predate iOS support. No App Store submission is required.

See [download and installation instructions](docs/ios-install.md) and [iOS architecture, tests and device checks](docs/ios-implementation.md). On a Mac, start with `swift test --package-path ios/Core`; use XcodeGen to generate the Xcode project after preparing the bundled audio.

## Project status

| Item | Status |
| --- | --- |
| Android 12 / API 31+ implementation | Complete |
| JVM state-machine, scheduling, and policy tests | Covered |
| Lint and Debug APK build | Local and CI gate |
| Emulator cold start, primary flow, and background launch | Automated regression scripts available |
| Wired / A2DP / USB / LE headphone matrix | **Real-device evidence is still being expanded** |
| Native iOS / iPadOS 17+ app | `0.1.0-beta` published with IPA, simulator and source downloads |
| iOS core, integration and UI tests | 19 passing tests; Release arm64 build passed |
| iOS personal signing and zero-speaker-leakage checks | **Real-device validation pending** |
| Distribution | GitHub Releases; no App Store submission for iOS |
| Public stable release | **Not yet available; both platforms remain beta** |

HushWake cannot guarantee an alarm when a device is powered off, out of battery, or the app has been force-stopped. It does not claim to treat insomnia or provide any medical benefit. See the [PRD](docs/hush-wake-prd.md) and [real-device test guide](docs/device-test-guide.md) for the complete boundaries.

## Build from source

### Android

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

### iOS / iPadOS

Requires macOS, Xcode 16.4, XcodeGen and FFmpeg. From the repository root:

```bash
brew install xcodegen ffmpeg
swift test --package-path ios/Core
python3 scripts/ios/prepare_resources.py
(cd ios && xcodegen generate)
open ios/HushWake.xcodeproj
```

Select your own signing Team and device in Xcode to run the app. See the [iOS installation guide](docs/ios-install.md) for the full steps.

## Code map

```text
app/src/main/java/com/hushwake/app/
├── alarm/       Alarm scheduling, triggers, ringing service, and stop policy
├── audio/       Smart-output policy, actual-route checks, and guarded player
├── data/        Local database and preferences
├── domain/      Alarm domain rules
├── noise/       Sleep-sound catalog, timers, and foreground service
└── ui/          Lightweight native Android UI
```

```text
ios/
├── Core/        Scheduling, sleep timers and audio-route state machine
├── HushWake/    SwiftUI, guarded audio, local data and silent notifications
├── Tests/       iOS integration tests
├── UITests/     Alarm creation, screen access and snooze regression
└── project.yml  XcodeGen project definition
```

Further reading:

- [Product requirements and safety constraints](docs/hush-wake-prd.md)
- [Android implementation and verification notes](docs/android-implementation.md)
- [iOS implementation and verification notes](docs/ios-implementation.md)
- [iOS downloads and installation](docs/ios-install.md)
- [Real-device test guide](docs/device-test-guide.md)
- [Audio sources and third-party licenses](docs/audio-credits.md)
- [Project homepage and GitHub Pages maintenance](docs/project-homepage.md)

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
