# HushWake（悄醒）

<p align="center">
  <img src="docs/assets/hushwake-hero.svg" alt="HushWake 悄醒：声音，去往正确的地方" width="100%">
</p>

<p align="center">
  <a href="https://github.com/chenzhiyong1994/hush-wake/actions/workflows/android-ci.yml"><img src="https://github.com/chenzhiyong1994/hush-wake/actions/workflows/android-ci.yml/badge.svg" alt="Android CI"></a>
  <img src="https://img.shields.io/badge/status-0.4.2--beta-f6bf6f" alt="0.4.2 beta">
  <img src="https://img.shields.io/badge/Android-12%2B-e9ff70?logo=android&logoColor=09110f" alt="Android 12+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-8fb8a8" alt="Apache License 2.0"></a>
</p>

<p align="center">
  <a href="README.md">English</a>
  ·
  <strong>简体中文</strong>
</p>

<p align="center">
  安全优先的 Android 智能输出闹钟与助眠声
</p>

<p align="center">
  <a href="https://github.com/chenzhiyong1994/hush-wake/releases/download/v0.4.3-beta/HushWake-0.4.3-beta.apk"><strong>下载正式签名的 0.4.3-beta APK</strong></a>
</p>

闹钟很擅长叫醒一个人，却也常常顺便叫醒一屋子人。

HushWake 想解决的是一个看似简单、实际很容易出错的问题：**没有耳机时正常外放；检测到耳机时，只在确认当前实际音频路径安全后播放。** 如果耳机断开、路由改变或系统给出的证据不足，应用会先静音再停止，而不是为了“继续有声”悄悄切回扬声器。

> [!IMPORTANT]
> `0.4.3-beta` 已具备完整核心功能，但仍是公开源码的功能测试版，不是公开稳定发行版。真实耳机的零扬声器串音验证仍是发布门禁；源码可构建不等于所有设备都已安全通过。

## 为什么做 HushWake

午休、通勤或合住场景里，用户既希望被可靠叫醒，也不希望声音突然从手机扬声器放出来。Android 提供了“首选输出设备”等能力，但首选并不等于已经验证的实际唯一输出；蓝牙断连和系统重路由之间还存在竞态。

HushWake 因此选择一条更克制的路线：

- **失败时保持安静。** 耳机会话无法确认实际路由，就不解除应用层静音。
- **把偏好和事实分开。** 目标设备、当前候选设备与实际路由分别检查。
- **先静音，再停止。** 断连或路由不确定时，先把播放器增益归零，再停止和执行用户允许的振动/通知兜底。
- **隐私默认留在本机。** 没有账号、云同步或行为上传；耳机原始名称和地址不落盘。

## 现在可以做什么

| 能力 | 当前实现 |
| --- | --- |
| 闹钟 | 一次性/周重复、精确调度、开机和时间变化后重排、锁屏入口、高优先级通知、停止、一次稍后提醒、最长响铃 |
| 智能输出 | 无耳机时允许系统媒体外放；检测到唯一耳机时进入路由守卫，不安全则阻断 |
| 耳机验证 | 双层静音起播、本机人工低增益确认、实际路由复验、断连/焦点丢失/多候选时停止 |
| 助眠声 | 8 种带环境底纹的真实录音，支持暂停、通知控制、5–120 分钟定时、渐隐和不重置计时的即时换声 |
| 闹铃声音 | 6 种 AOSP 闹铃素材，编辑页即时试听，正式响铃持续循环 |
| 品牌体验 | B1-A 自适应桌面图标、Android 主题单色图标与品牌冷启动动效 |
| 数据 | 本地 SQLite / SharedPreferences；Android 备份与设备迁移关闭；无网络和账号层 |

应用音量完全跟随系统媒体音量，不会擅自调高系统音量。所有音频离线打包，来源和再分发许可可在[音频来源与许可](docs/audio-credits.md)中逐项审查。

## 项目状态

| 项目 | 状态 |
| --- | --- |
| Android 12 / API 31 及以上构建 | 已实现 |
| JVM 状态机、调度与策略测试 | 已覆盖 |
| Lint 与 Debug APK 构建 | 本地与 CI 门禁 |
| 模拟器冷启动、主流程与后台拉起 | 已有自动回归脚本 |
| 有线 / A2DP / USB / LE 耳机矩阵 | **待持续补充实体机证据** |
| 公开稳定版 / 应用商店发行 | **尚未开放** |

HushWake 不能在关机、无电、被系统强制停止等条件下保证唤醒，也不提供失眠治疗或其他医疗效果。完整边界见 [PRD](docs/hush-wake-prd.md) 与[实体机测试指南](docs/device-test-guide.md)。

## 快速开始

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

## 代码地图

```text
app/src/main/java/com/hushwake/app/
├── alarm/       闹钟调度、触发、响铃服务与停止策略
├── audio/       路由检查、设备指纹、安全引擎与播放器
├── data/        本地数据库与偏好
├── domain/      闹钟和设备验证领域规则
├── guard/       失败静音与动作顺序
├── noise/       助眠声目录、定时与前台服务
└── ui/          轻量原生 Android UI
```

进一步阅读：

- [产品需求文档与安全约束](docs/hush-wake-prd.md)
- [Android 实现与验证说明](docs/android-implementation.md)
- [实体机测试指南](docs/device-test-guide.md)
- [音频来源与第三方许可](docs/audio-credits.md)

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
