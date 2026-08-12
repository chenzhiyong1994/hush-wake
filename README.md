# HushWake（悄醒）

> 只在耳边响起。

HushWake 是一款面向 Android 的个人工具：在耳机输出通过验证时提供私密闹钟，并提供轻量白噪音辅助入睡。

## 当前阶段

`0.2.0-beta` 是首个完整可用测试版，面向 Android 12 / API 31 及以上。它已经覆盖正式私密闹钟、耳机白噪音和本地设置/记录，但实体耳机的零泄漏验证仍是发布门禁，不应把 beta 描述为公开稳定版。

当前版本包含：

- 一次性/周重复闹钟的创建、编辑、启停、删除、精确调度、重启与时间变化后重排。
- 到点前台服务、锁屏全屏入口或高优先级通知降级、停止、一次稍后提醒、最长响铃和振动兜底。
- API 31–35 兼容验证与 API 36+ 全路由强验证；正式声音只对当前已测试耳机开放。
- 双层静音起播、实际路由通过后渐强、断连/焦点/路由变化时先归零增益再停止。
- 四种离线环境音、暂停、通知控制、定时和结束渐隐；白噪音同样禁止扬声器降级。
- 首次引导、分项可靠性中心、最近 30 次或 30 天本地记录、默认值与本地数据清除。
- 无账号、无云同步、无诊断上传；耳机身份仅保存带本机随机盐的不可逆哈希。

## 核心原则

- 隐私安全优先于有声唤醒可靠性。
- 无法确认输出安全时保持静音，不自动切换到手机扬声器。
- 当前免费供个人使用和分享，不包含付费、广告、账号或云同步。

## 产品文档

- [HushWake Android App PRD](docs/hush-wake-prd.md)
- [0.2.0-beta 实体机测试指南](docs/device-test-guide.md)
- [Android 实现与验证说明](docs/android-implementation.md)

## 构建与验证

需要 JDK 17 与 Android SDK 36：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

生成的测试包位于 `app/build/outputs/apk/debug/app-debug.apk`。ADB 连接设备后可运行：

```powershell
.\scripts\verify-app-launch.ps1
.\scripts\smoke-emulator.ps1
```

前者检查冷启动，后者自动遍历首次引导、五个主页面和新建闹钟页。当前已在 API 31、34、36 模拟器覆盖该流程；API 34 还验证了 Android 系统真实精确触发和无耳机阻断。构建、单元测试和模拟器回归只能证明逻辑与生命周期满足本地契约；真实耳机的扬声器零误播仍必须按实体机测试指南验证。
