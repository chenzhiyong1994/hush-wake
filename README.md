# HushWake（悄醒）

> 只在耳边响起。

HushWake 是一款面向 Android 的个人工具：在耳机输出通过验证时提供私密闹钟，并提供轻量白噪音辅助入睡。

## 当前阶段

项目已交付 Android 技术验证版 `0.1.1-lab`。它用于在实体设备上验证静音起播、实际路由读取、耳机断连和失败静音链路，不是完整闹钟产品，也不代表已经通过发布门禁。

当前版本包含：

- API 31–35 兼容验证与 API 36+ 全路由强验证的明确分级。
- 双层静音起播：PCM 零采样与 `AudioTrack` 增益 0。
- 路由通过后 10 秒低增益测试音；路由不确定、超时或断连时先静音再停止。
- 仅本地保存的粗粒度诊断摘要，不含设备名称、地址、稳定标识或精确闹钟时间。
- 面向手机的单页“路由实验室”界面。

## 核心原则

- 隐私安全优先于有声唤醒可靠性。
- 无法确认输出安全时保持静音，不自动切换到手机扬声器。
- 当前免费供个人使用和分享，不包含付费、广告、账号或云同步。

## 产品文档

- [HushWake Android App PRD](docs/hush-wake-prd.md)
- [0.1.1-lab 实体机测试指南](docs/device-test-guide.md)

## 构建与验证

需要 JDK 17 与 Android SDK 36：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

生成的测试包位于 `app/build/outputs/apk/debug/app-debug.apk`。ADB 连接测试设备后可运行 `.\scripts\verify-app-launch.ps1` 检查冷启动、前台 Activity 和崩溃日志。构建和单元测试通过只能证明代码与状态机满足本地契约；扬声器零误播仍必须按实体机测试指南验证。
