# HushWake Android 实现与验证

本文记录 `0.2.1-beta` 的工程入口；产品行为以 `hush-wake-prd.md` 为准，实体机安全验收以 `device-test-guide.md` 为准。

## 运行结构

- `HomeActivity` 承载首次引导与闹钟、白噪音、可靠性、记录、设置五个页面；`EditAlarmActivity` 编辑单次和周重复闹钟。
- SQLite 保存闹钟、当前耳机验证摘要和最近 30 次/30 天事件；SharedPreferences 只保存设置与粗粒度会话状态，Android 备份和设备迁移均关闭。
- `AlarmScheduler` 使用稳定 `PendingIntent` 和 `setAlarmClock()`；开机、时间/时区变化、应用升级、精确权限重新授予后重排。
- `AlarmRingingService` 与 `WhiteNoiseService` 是 `mediaPlayback` 前台服务。两者共用 `PrivatePlaybackEngine`，通知频道本身无声音且不绕过勿扰。
- `PrivatePlaybackEngine` 根据会话开始时的候选输出选择智能外放或耳机守卫。无耳机时允许系统媒体外放；检测到唯一耳机时，只有具体耳机哈希、有效人工测试和本次实际路由全部通过后才解除双层静音。外放期收到路由信号会先静音并重新枚举；耳机接入后转入耳机验证。耳机目标移除、路由不匹配、多候选、超时或焦点丢失会直接结束，不在同一会话中降级回扬声器。

## 数据和升级

- 数据库版本 2 为一次性闹钟保存本地目标日期，避免进程重启后将过期实例悄悄滚到第二天；从版本 1 升级时旧一次性实例保守停用。
- 耳机原始地址只在内存中参与 `SHA-256(安装盐 + 设备类型 + 地址)`，不保存名称或地址。验证在 90 天、Android 大版本、音频引擎版本或耳机变化后失效。
- “清除全部本地数据”取消系统调度，清空数据库、设置、诊断和会话状态，删除安装盐，并启用 `secure_delete`、截断 WAL 和 `VACUUM` 做本地压实。

## 自动验证

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\scripts\verify-app-launch.ps1
.\scripts\smoke-emulator.ps1
```

JVM 测试覆盖智能输出选择、外放转耳机、耳机断连禁止外放降级、守卫动作顺序、路由信号静音重验、静音延迟计量、耳机验证过期、闹钟字段、单次过期不顺延、周重复和 DST 跳时。既有模拟器基线覆盖 API 31/34/36 冷启动与 UI；智能输出本轮需重新跑设备启动与 UI 回归。

模拟器不能证明实体耳机播放期间没有扬声器泄漏，也不能覆盖厂商后台策略。该结论必须保留为 pending，直到按实体机测试指南完成。
