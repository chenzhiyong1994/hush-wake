# iOS Beta 下载与安装

HushWake iOS 为原生 SwiftUI 开源测试版，最低支持 iOS / iPadOS 17。不依赖 App Store、TestFlight 或云服务。

## 下载内容

- GitHub [当前 beta 下载页](https://github.com/chenzhiyong1994/hush-wake/releases/tag/v0.4.8-beta)：iOS 附件与 Android APK 分别标注平台和版本。
- [iOS 构建记录](https://github.com/chenzhiyong1994/hush-wake/actions/workflows/ios-ci.yml)：成功运行的 `HushWake-iOS-beta-downloads` artifact 包含未签名 IPA、模拟器应用、校验文件和本说明。Actions 下载需要登录 GitHub，有 90 天保留期；Release 附件长期保留且无需登录。
- `HushWake-iOS-0.1.0-beta-unsigned.ipa`：iPhone / iPad 的 arm64 应用，**未签名，不能直接点击安装**。需要使用自己的签名身份和匹配的 provisioning profile 签名。
- `HushWake-iOS-0.1.0-beta-simulator.zip`：macOS 上的 iOS 模拟器应用，不能安装到 iPhone。
- iOS 独立源码附件及 `BUILD.txt` 指向实际构建 commit。Release 原有 `v0.4.8-beta` 标签继续对应 Android 历史版本；页面自带的旧标签 “Source code” 归档不包含后来新增的 iOS 工程，请使用 iOS 专用源码附件或构建 commit。

下载后按随包 `SHA256SUMS.txt` 校验，例如 macOS 中运行 `shasum -a 256 -c SHA256SUMS.txt`。文件名中的 `unsigned` 是签名状态，不代表可以绕过 iOS 签名验证。

## 推荐：用 Xcode 构建到自己的设备

需要 macOS、Xcode 16.4（含 iOS 18.5 SDK；部署目标仍为 iOS 17）、XcodeGen 和 FFmpeg。GitHub CI 使用相同的 Xcode 版本。

```bash
git clone https://github.com/chenzhiyong1994/hush-wake.git
cd hush-wake
brew install xcodegen ffmpeg
python3 scripts/ios/prepare_resources.py
cd ios
xcodegen generate
open HushWake.xcodeproj
```

在 Xcode 中选择 `HushWake` target 的 **Signing & Capabilities**，选择自己的 Team，必要时改为自己的唯一 Bundle Identifier；连接 iPhone / iPad，按系统提示启用 Developer Mode，然后运行 `HushWake` scheme。Personal Team 是否可用、签名期限和设备限制以 Apple 账号当前权限为准。不要把证书、私钥或 provisioning profile 提交到仓库。

如果使用已下载的 IPA，通过支持个人签名的工具以自己的身份重新签名；应用标识、授权与 profile 必须匹配。仓库不会提供公共证书或通用已签名包。不需要申请 App Store 上架。

Apple 官方说明：[签名与设备运行](https://help.apple.com/xcode/mac/current/en.lproj/dev60b6fbbc7.html)、[向已注册设备分发](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices)。

## 模拟器安装

解压 simulator ZIP，在匹配架构的 Mac 上启动 iOS 17+ 模拟器后运行：

```bash
xcrun simctl install booted HushWake.app
xcrun simctl launch booted com.hushwake.app.ios
```

CI 模拟器包的架构随 GitHub macOS runner 而定；如果本机架构不匹配，用 Xcode 从源码选择本机模拟器构建。

## 首次使用

1. 先看闹钟页的后台说明，再按需开启无声通知。
2. 在应用前台运行一次“1 分钟测试”；按自己的需要调整系统媒体音量。
3. 无耳机时允许手机扬声器播放；仅系统明确识别的唯一有线耳机允许受守卫保护的播放。蓝牙、USB、AirPlay、车载、听筒与未知输出均保持静音。
4. 有声闹钟要求应用保持前台。锁屏或后台只会收到无声通知，通知可能受专注模式和系统设置影响；打开提醒后可手动请求播放。
5. 助眠声支持正常后台音频播放，暂停和切换声音保留本次结束时间；被系统终止后不会自动恢复。

本版不是 Android 后台精确唤醒能力的等价移植。实体机路由变化和零扬声器串音仍待验证，不能作为唯一叫醒手段。功能、验证入口和设备验收清单见 [iOS 实现说明](ios-implementation.md)。
