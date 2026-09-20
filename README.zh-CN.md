# HushWake（悄醒）

<p align="center">
  <img src="docs/assets/hushwake-hero.svg" alt="HushWake 悄醒：声音，去往正确的地方" width="100%">
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
  <a href="README.md">English</a>
  ·
  <strong>简体中文</strong>
</p>

<p align="center">
  安全优先的 Android 与 iOS 智能输出闹钟与助眠声
</p>

<p align="center">
  <a href="https://chenzhiyong1994.github.io/hush-wake/"><strong>访问项目主页</strong></a>
  ·
  <a href="https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-0.4.8-beta.apk"><strong>下载 Android APK</strong></a>
  ·
  <a href="https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-iOS-0.1.0-beta-unsigned.ipa"><strong>下载 iOS IPA（未签名）</strong></a>
</p>

闹钟很擅长叫醒一个人，却也常常顺便叫醒一屋子人。

HushWake 想解决的是一个看似简单、实际很容易出错的问题：**没有耳机时正常外放；检测到耳机时，只在确认当前实际音频路径安全后播放。** 如果耳机断开、路由改变或系统给出的证据不足，应用会先静音再停止，而不是为了“继续有声”悄悄切回扬声器。

> [!IMPORTANT]
> Android `0.4.8-beta` 与 iOS `0.1.0-beta` 均为开源功能测试版。真实耳机的零扬声器串音验证仍是稳定版门禁；构建和自动测试通过不等于所有设备已完成验收。

## 选择你的平台

| 平台 | 当前版本与要求 | 下载与安装 |
| --- | --- | --- |
| Android | `0.4.8-beta` · Android 12+ | [签名 APK](https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-0.4.8-beta.apk)，下载后安装 |
| iPhone / iPad | `0.1.0-beta` · iOS / iPadOS 17+ | [未签名 IPA](https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-iOS-0.1.0-beta-unsigned.ipa)，需自行签名；[安装说明](docs/ios-install.md) |
| iOS 开发与模拟器 | macOS / Xcode | [iOS 源码包](https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-iOS-0.1.0-beta-source.zip) · [模拟器包](https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.8-beta/HushWake-iOS-0.1.0-beta-simulator.zip) |

两个平台均通过 GitHub 开源分发；iOS 不需要上架 App Store。**iOS 有声闹钟需保持应用前台，后台和锁屏仅无声通知；助眠声可后台播放。** 下载前请了解这一平台边界。校验文件见 [Release 下载页](https://github.com/chenzhiyong1994/hush-wake/releases/tag/v0.4.8-beta)。

## 为什么做 HushWake

午休、通勤或合住场景里，用户既希望被可靠叫醒，也不希望声音突然从手机扬声器放出来。Android 提供了“首选输出设备”等能力，但首选并不等于已经验证的实际唯一输出；蓝牙断连和系统重路由之间还存在竞态。

HushWake 因此选择一条更克制的路线：

- **失败时保持安静。** 耳机会话无法确认实际路由，就不解除应用层静音。
- **把偏好和事实分开。** 目标设备、当前候选设备与实际路由分别检查。
- **先静音，再停止。** 断连或路由不确定时，先把播放器增益归零，再停止和执行用户允许的振动/通知兜底。
- **隐私默认留在本机。** 没有账号、云同步或行为上传；耳机原始名称和地址不落盘。

## 现在可以做什么

下表描述 **Android** 能力；原生 iOS beta 及其平台限制见下一节。

| 能力 | 当前实现 |
| --- | --- |
| 闹钟 | 一次性/周重复、精确调度、唯一下一次响铃焦点、开机和时间变化后重排、锁屏入口、高优先级通知、停止、一次稍后提醒、最长响铃 |
| 智能输出 | 无耳机时允许系统媒体外放；检测到唯一耳机时进入路由守卫，不安全则阻断 |
| 耳机路由 | 自动双层静音起播并确认播放器实际路由；不再提供人工测试页；断连、焦点丢失或多候选时停止 |
| 助眠声 | 8 种带环境底纹的真实录音，支持暂停、通知控制、5–120 分钟定时、渐隐和不重置计时的即时换声 |
| 闹铃声音 | 6 种 AOSP 闹铃素材，编辑页即时试听，正式响铃持续循环 |
| 品牌体验 | 具有遮罩安全留白的 B1-A 自适应桌面图标、Android 主题单色图标与品牌冷启动动效 |
| 数据 | 本地 SQLite / SharedPreferences；Android 备份与设备迁移关闭；无网络和账号层 |

应用音量完全跟随系统媒体音量，不会擅自调高系统音量。所有音频离线打包，来源和再分发许可可在[音频来源与许可](docs/audio-credits.md)中逐项审查。

## iOS 0.1.0-beta

已新增 **iOS / iPadOS 17+ 原生 SwiftUI 应用**：单次与周重复闹钟、一次稍后提醒、6 种闹铃、8 种离线助眠声、睡眠定时与渐隐、本地保存。**有声闹钟需要应用保持前台，后台或锁屏仅发送无声系统通知。** 助眠声支持正常后台音频播放。

仅内置扬声器或系统明确识别的唯一有线耳机可通过本次路由检查后播放；不明确的蓝牙/USB、AirPlay、车载及未知输出保持阻断。路由变化先静音再停止，实体机零串音验证仍待完成。

在[当前 beta 下载页](https://github.com/chenzhiyong1994/hush-wake/releases/tag/v0.4.8-beta)获取 iOS 专用附件，或从 [iOS CI](https://github.com/chenzhiyong1994/hush-wake/actions/workflows/ios-ci.yml) 获取成功构建的产物。**IPA 未签名，需要使用自己的身份签名后才能安装。** 同时提供模拟器包及独立 iOS 源码附件；页面中原 Android 标签的自动源码归档早于 iOS 支持，不包含新工程。不涉及 App Store 上架。

具体步骤见 [iOS 下载与安装](docs/ios-install.md)，架构、测试和设备验收见 [iOS 实现说明](docs/ios-implementation.md)。Mac 上可先运行 `swift test --package-path ios/Core`，准备音频后通过 XcodeGen 生成 Xcode 工程。

## 项目状态

| 项目 | 状态 |
| --- | --- |
| Android 12 / API 31 及以上构建 | 已实现 |
| JVM 状态机、调度与策略测试 | 已覆盖 |
| Lint 与 Debug APK 构建 | 本地与 CI 门禁 |
| 模拟器冷启动、主流程与后台拉起 | 已有自动回归脚本 |
| 有线 / A2DP / USB / LE 耳机矩阵 | **待持续补充实体机证据** |
| iOS / iPadOS 17+ 原生应用 | 已发布 `0.1.0-beta`，IPA / 模拟器包 / 源码可下载 |
| iOS 核心、集成与界面测试 | 19 项通过；Release arm64 构建通过 |
| iOS 个人签名安装与耳机零串音 | **待实体机验收** |
| 分发渠道 | GitHub Releases；iOS 不涉及 App Store 上架 |
| 公开稳定版 | **尚未发布；两个平台均为 beta** |

HushWake 不能在关机、无电、被系统强制停止等条件下保证唤醒，也不提供失眠治疗或其他医疗效果。完整边界见 [PRD](docs/hush-wake-prd.md) 与[实体机测试指南](docs/device-test-guide.md)。

## 从源码构建

### Android

需要：

- JDK 17
- Android SDK 36
- Windows PowerShell（仓库验证脚本当前以 PowerShell 为主）

克隆并构建：

```powershell
git clone https://github.com/chenzhiyong1994/hush-wake.git
cd hush-wake
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

生成的 Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

连接 ADB 设备或模拟器后，可按需运行：

```powershell
.\scripts\verify-app-launch.ps1
.\scripts\smoke-emulator.ps1
.\scripts\verify-background-alarm.ps1
```

闹铃素材连续性检查需要 `ffmpeg` / `ffprobe`：

```powershell
.\scripts\verify-alarm-audio.ps1
```

模拟器回归不能证明真实耳机没有扬声器泄漏；相关结论必须按[实体机测试指南](docs/device-test-guide.md)验证。

### iOS / iPadOS

需要 macOS、Xcode 16.4、XcodeGen 和 FFmpeg。在仓库根目录运行：

```bash
brew install xcodegen ffmpeg
swift test --package-path ios/Core
python3 scripts/ios/prepare_resources.py
(cd ios && xcodegen generate)
open ios/HushWake.xcodeproj
```

在 Xcode 中选择自己的签名 Team 和设备运行。完整步骤见 [iOS 下载与安装](docs/ios-install.md)。

## 代码地图

```text
app/src/main/java/com/hushwake/app/
├── alarm/       闹钟调度、触发、响铃服务与停止策略
├── audio/       智能输出策略、实际路由检查与守卫播放器
├── data/        本地数据库与偏好
├── domain/      闹钟领域规则
├── noise/       助眠声目录、定时与前台服务
└── ui/          轻量原生 Android UI
```

```text
ios/
├── Core/        调度、定时与音频路由状态机
├── HushWake/    SwiftUI、音频守卫、本地数据与无声通知
├── Tests/       iOS 集成测试
├── UITests/     创建闹钟、页面访问与稍后提醒回归
└── project.yml  XcodeGen 工程定义
```

进一步阅读：

- [产品需求文档与安全约束](docs/hush-wake-prd.md)
- [Android 实现与验证说明](docs/android-implementation.md)
- [iOS 实现与验证说明](docs/ios-implementation.md)
- [iOS 下载与安装](docs/ios-install.md)
- [实体机测试指南](docs/device-test-guide.md)
- [音频来源与第三方许可](docs/audio-credits.md)
- [项目主页与 GitHub Pages 维护](docs/project-homepage.md)

## 一起完善

最有价值的贡献往往不是“再加一个按钮”，而是让某个真实设备上的行为更可证明：

- 新 Android 版本或厂商设备的路由兼容性证据；
- 耳机接入/断连竞态、焦点变化与多候选输出的回归测试；
- 不收集隐私信息的诊断与可观测性改进；
- 闹钟可靠性、无障碍与原生交互优化；
- 许可清晰、可离线再分发的声音素材建议。

请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。安全问题请通过 [GitHub Private Vulnerability Reporting](SECURITY.md) 私下报告；Issue 和日志中不要放入蓝牙名称、MAC 地址、精确作息或稳定设备标识。

Issue 和 Pull Request 均欢迎使用中文或英文。

## 开源许可

HushWake 源代码采用 [Apache License 2.0](LICENSE)。

`app/src/main/res/raw/` 下的音频素材保留各自的 CC0、CC BY 4.0 或 Apache-2.0 许可，不因主仓库许可证而重新授权。具体作者、来源、修改方式与许可见 [docs/audio-credits.md](docs/audio-credits.md)。

---

<p align="center">
  如果这个方向也恰好解决了你的问题，欢迎试着构建、审查或带来一份真实设备证据。
</p>
