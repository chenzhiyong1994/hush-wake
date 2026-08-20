# HushWake Android 实现与验证

本文记录 `0.3.7-beta` 的工程入口；产品行为以 `hush-wake-prd.md` 为准，实体机安全验收以 `device-test-guide.md` 为准。

## 运行结构

- `HomeActivity` 只承载首次引导、“闹钟”和“助眠声”两个主页面；系统权限缺失、后台受限和当前输出状态以内联卡片呈现。主页重新检查精确闹钟、通知、全屏响铃、系统后台限制、App Standby Bucket 和电池优化豁免，按优先级显示处理入口，但保存或重新启用闹钟时不自动弹窗或打开设置。厂商自启动没有统一可读 API，因此在闹钟列表顶部、已开启闹钟卡片和 `EditAlarmActivity` 中持续显示暖色风险提醒；只有用户点击“去设置”后才按厂商尝试打开启动管理页，返回后要求人工确认并按当前厂商保存。`EditAlarmActivity` 通过小时/分钟双滚轮和三个相对时间快捷项编辑时间，点选铃声通过短生命周期 `PrivatePlaybackEngine` 会话即时试听；保存新建或编辑结果会自动启用，启停开关只保留在列表页。
- SQLite 只保存闹钟和当前耳机验证摘要；SharedPreferences 保存必要偏好与粗粒度当前会话状态。没有用户行为历史表，Android 备份和设备迁移均关闭。
- `AlarmScheduler` 使用稳定 `PendingIntent` 和 `setAlarmClock()`；开机、时间/时区变化、应用升级、精确权限重新授予后重排。API 33+ 声明闹钟应用允许的 `USE_EXACT_ALARM`，API 31–32 以 `SCHEDULE_EXACT_ALARM` 兼容；发布到 Google Play 前必须完成精确闹钟用途声明。
- `AlarmRingingService` 与 `WhiteNoiseService` 是 `mediaPlayback` 前台服务。两者共用 `PrivatePlaybackEngine`，通知频道本身无声音且不绕过勿扰。
- 闹钟采用统一策略：系统媒体音量、路由通过后 25% 起始应用增益、15 秒渐强至 100%、阻断时振动、5 分钟稍后提醒、2 分钟最长响铃。点击稍后提醒会更新同一闹钟的时间和开启状态，保留重复星期，并持久化精确到秒的本次目标，确保应用重开或系统重排后仍是点击时刻后 5 分钟。旧版本保存的逐闹钟增益和处置字段不再参与播放决策。
- `AlarmSessionStore` 保存当前活动闹钟 ID。主页关闭同一闹钟，或点击一次性闹钟卡片上的“立即停止”，会向服务发送带 ID 的停止命令；服务拒绝停止不匹配的闹钟。主页订阅 `AlarmRingingService.ACTION_STATE`，服务确认 `STOPPED` 后按持久化会话重新渲染，避免停止按钮残留。
- 闹铃与助眠声均通过 `MediaPlayer` 播放 APK 内的许可清晰素材，来源见 `audio-credits.md`；闹铃使用 AOSP 专业素材并持续循环，不再由应用实时合成。`ACTION_SWITCH_SOUND` 在同一服务会话内重建播放器，保留原结束时间、渐隐配置和暂停状态。
- `SleepSoundCatalog` 与 `AlarmSoundCatalog` 是声音库的单一入口；页面由目录生成横向选择卡片。助眠声播放时只展示实时剩余时间，预设时长只在未播放状态展示。
- `PrivatePlaybackEngine` 根据当前候选输出选择智能外放或耳机守卫。无耳机时允许系统媒体外放；检测到唯一耳机时，只有具体耳机哈希、有效人工测试和本次实际路由全部通过后才解除双层静音。外放期接入耳机会先静音并转入耳机验证；耳机目标移除、路由不匹配、多候选、超时或焦点丢失会结束会话，不降级回扬声器。

## 数据和升级

- 数据库版本 2 为一次性闹钟保存本地目标日期；版本 3 删除已废弃的 `playback_events` 表和索引。升级时保留闹钟与耳机验证数据。
- 耳机原始地址只在内存中参与 `SHA-256(安装盐 + 设备类型 + 地址)`，不保存名称或地址。验证在 90 天、Android 大版本、音频引擎版本或耳机变化后失效。
- 应用不提供独立设置/数据页；卸载应用仍会由系统删除应用沙箱内数据。

## 自动验证

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\scripts\verify-alarm-audio.ps1
.\scripts\verify-app-launch.ps1
.\scripts\smoke-emulator.ps1
.\scripts\verify-background-alarm.ps1
```

JVM 测试覆盖智能输出选择、外放转耳机、耳机断连禁止外放降级、守卫动作顺序、路由信号静音重验、耳机验证过期、闹钟调度字段、稍后提醒状态转换、快捷时间跨午夜、主页停用与活动响铃 ID 的匹配规则、响铃状态刷新策略、六项 Android 标准检查与厂商自启动人工确认优先级、后台就绪判定、助眠声无重置切换和铃声试听选择。模拟器回归覆盖冷启动、两页导航、闹钟编辑、保存与重新启用后只显示内联后台风险入口而不强制弹窗、用户主动进入电池优化确认、全新安装精确调度、后台系统唤醒、稍后提醒持久化与应用重开重排，以及响铃期间从桌面启动应用不崩溃。

模拟器不能证明实体耳机播放期间没有扬声器泄漏，也不能覆盖厂商后台策略。该结论必须保留为 pending，直到按实体机测试指南完成。
