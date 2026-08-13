# HushWake Android 实现与验证

本文记录 `0.3.0-beta` 的工程入口；产品行为以 `hush-wake-prd.md` 为准，实体机安全验收以 `device-test-guide.md` 为准。

## 运行结构

- `HomeActivity` 只承载首次引导、“闹钟”和“助眠声”两个主页面；系统权限缺失和当前输出状态以内联卡片呈现。`EditAlarmActivity` 只编辑时间、标签、重复、启用状态和铃声。
- SQLite 只保存闹钟和当前耳机验证摘要；SharedPreferences 保存必要偏好与粗粒度当前会话状态。没有用户行为历史表，Android 备份和设备迁移均关闭。
- `AlarmScheduler` 使用稳定 `PendingIntent` 和 `setAlarmClock()`；开机、时间/时区变化、应用升级、精确权限重新授予后重排。
- `AlarmRingingService` 与 `WhiteNoiseService` 是 `mediaPlayback` 前台服务。两者共用 `PrivatePlaybackEngine`，通知频道本身无声音且不绕过勿扰。
- 闹钟采用统一策略：系统媒体音量、100% 应用播放系数、15 秒渐强、阻断时振动、5 分钟稍后提醒、2 分钟最长响铃。旧版本保存的逐闹钟增益和处置字段不再参与播放决策。
- `AlarmSessionStore` 保存当前活动闹钟 ID。主页关闭同一闹钟，或点击一次性闹钟卡片上的“立即停止”，会向服务发送带 ID 的停止命令；服务拒绝停止不匹配的闹钟。
- 助眠声通过 `MediaPlayer` 循环播放 APK 内的真实录音，素材及许可见 `audio-credits.md`；不再生成合成白噪音。
- `PrivatePlaybackEngine` 根据当前候选输出选择智能外放或耳机守卫。无耳机时允许系统媒体外放；检测到唯一耳机时，只有具体耳机哈希、有效人工测试和本次实际路由全部通过后才解除双层静音。外放期接入耳机会先静音并转入耳机验证；耳机目标移除、路由不匹配、多候选、超时或焦点丢失会结束会话，不降级回扬声器。

## 数据和升级

- 数据库版本 2 为一次性闹钟保存本地目标日期；版本 3 删除已废弃的 `playback_events` 表和索引。升级时保留闹钟与耳机验证数据。
- 耳机原始地址只在内存中参与 `SHA-256(安装盐 + 设备类型 + 地址)`，不保存名称或地址。验证在 90 天、Android 大版本、音频引擎版本或耳机变化后失效。
- 应用不提供独立设置/数据页；卸载应用仍会由系统删除应用沙箱内数据。

## 自动验证

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\scripts\verify-app-launch.ps1
.\scripts\smoke-emulator.ps1
```

JVM 测试覆盖智能输出选择、外放转耳机、耳机断连禁止外放降级、守卫动作顺序、路由信号静音重验、耳机验证过期、闹钟调度字段，以及主页停用与活动响铃 ID 的匹配规则。模拟器回归覆盖冷启动、两页导航、闹钟编辑和无耳机助眠声播放。

模拟器不能证明实体耳机播放期间没有扬声器泄漏，也不能覆盖厂商后台策略。该结论必须保留为 pending，直到按实体机测试指南完成。
