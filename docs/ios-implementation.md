# iOS 实现与验证

## 范围和架构

iOS 首版 `0.1.0-beta` 与 Android `0.4.8-beta` 分别版本化。最低 iOS / iPadOS 17，SwiftUI 原生界面，无第三方运行时依赖。`ios/project.yml` 是 Xcode 工程定义；生成的 `.xcodeproj`、M4A 音频和构建产物不入 Git。

- `ios/Core`：可用 SwiftPM 单独测试的日历调度、单次稍后提醒、睡眠截止时间和音频路由状态机。
- `ios/HushWake/Audio`：AVAudioSession / AVAudioPlayer 适配器，同一互斥锁串行化播放器增益、验证、播放、停止和路由事件；通知回调直接静音，不等待主线程。
- `ios/HushWake/Models`：本机 JSON 数据、闹钟生命周期和助眠会话。原子保存、文件保护、目录排除备份；读失败保留原文件并禁止覆盖。
- `ios/HushWake/Notifications`：UserNotifications 无声调度，最多 8 个闹钟，最坏 56 个周重复请求 + 8 个稍后请求；并发编辑串行重建，避免旧任务恢复已删除通知。
- `ios/HushWake/Views`：闹钟、助眠、说明、编辑和提醒页；适配 iPhone/iPad、动态字体及 VoiceOver 标签。

所有闹铃、试听与助眠复用同一音频守卫。音频来自现有 Android 素材，构建时转为 48 kHz / 160 kbps AAC，14 项署名和许可随应用离线打包；不联网下载素材。

## iOS 特有的产品边界

| 能力 | 首版行为 |
| --- | --- |
| 单次 / 每周闹钟 | 本地保存；前台定时器按计划触发；单次绑定实际日期，重复按本地时区计算 |
| 稍后提醒 | 每次实例最多一次，固定 5 分钟，保留周重复配置；新一轮正常重复可重新稍后 |
| 响铃时长 | 最多 2 分钟，支持停止；多闹钟同时到点只开启一个有声会话 |
| 后台、锁屏或应用终止 | 已登记的系统无声通知；无后台自动有声唤醒，不播放静音音轨保活 |
| 返回前台 / 重启应用 | 过期单次与稍后实例被消费，不补播；未来周重复继续计算 |
| 媒体播放 | 内置扬声器或唯一 `.headphones` 路由经本次静音起播复核后可播放 |
| 不明设备 | A2DP/HFP/LE/USB 仅证明传输类型，不能排除蓝牙音箱或其他非耳机输出，所以阻断 |
| 路由变化 | 回调先 volume=0、再 stop，所有不确定变化均停止；随后按通知授权发送无声阻断提示，前台轻触反馈按用户开关；50 ms 轮询补充检测，不声明可消除硬件竞态 |
| 助眠 | 8 声音、5–120 分钟、0/15/30 秒渐隐；正常音频可后台播放；暂停、换声不延长截止时间；换声必须匹配仍在播放的本次路由，旧会话失效后只能手动重新开始 |
| 数据 | Application Support JSON，不请求麦克风、蓝牙扫描、通讯录或位置；不存储路由名称/UID，不上传数据 |

Apple 的 [AVAudioSession 路由变更说明](https://developer.apple.com/documentation/avfaudio/responding-to-audio-route-changes)描述的是路由变化后通知；应用层回调与轮询不能证明物理零串音。`currentRoute.outputs` 是当前会话证据，不能仅根据端口名字猜测耳机，也不能把一次检查宣传为绝对安全。

根据 [UserNotifications 说明](https://developer.apple.com/library/archive/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/SchedulingandHandlingLocalNotifications.html)，后台通知由系统呈现，不等于后台任意执行应用播放器。AlarmKit 的系统闹铃声音也无法接入此应用的路由守卫，因此首版不以系统有声提醒替代私密输出策略。

## 构建与测试

完整安装步骤见 [ios-install.md](ios-install.md)。原生验证要求 Mac / Xcode；Windows 仅可编辑、准备资源、静态检查并触发 GitHub macOS CI。

```bash
swift test --package-path ios/Core
python3 scripts/ios/prepare_resources.py
(cd ios && xcodegen generate)
xcodebuild -project ios/HushWake.xcodeproj -scheme HushWake \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -derivedDataPath ios/build/Simulator CODE_SIGNING_ALLOWED=NO test
xcodebuild -project ios/HushWake.xcodeproj -scheme HushWake -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath ios/build/Device \
  CODE_SIGNING_ALLOWED=NO build
python3 scripts/ios/package.py \
  --device ios/build/Device/Build/Products/Release-iphoneos/HushWake.app \
  --simulator ios/build/Simulator/Build/Products/Debug-iphonesimulator/HushWake.app \
  --output ios/build/downloads
```

模拟器名称应替换为 `xcrun simctl list devices available` 中的本机设备。CI 自动选择现有 iPhone，并保留 XCTest 结果、UI 截图、构建日志及产物 SHA-256。

维护者在完成公开发布审计后，可手动运行 `iOS CI and downloads`，开启 `publish` 并将完整的已审计提交 SHA 填入 `expected_commit`。同一次运行的全部测试和打包成功后，发布任务校验源提交及全部产物摘要，再向既有 beta Release 追加 iOS 附件并回读验证；不创建或移动 Git 标签，不覆盖既有附件。相同版本已存在且内容不同会拒绝覆盖，应先制定新的版本分发目标。

自动验证覆盖：静音先于停止、空/未知/多路由阻断、耳机断开不降级、路由身份变化、周末跨周、DST 春季跳时、单次消费、稍后次数、周重复保留、定时与渐隐、无声通知与请求预算、数据原子往返/备份排除/损坏保留、14 个声音的解码，以及创建闹钟后重启仍存在和三个主页面可达。

## 实体机验收门禁（全部 pending）

用专用测试设备执行，不删除用户已有数据。记录系统/机型、输出类别和结论，避免记录设备原始名称、UID 或私人闹钟标签。

1. 无耳机起播和响铃、媒体音量为零、静音开关；确认未擅自提升系统音量。
2. 有线耳机静音起播、确认唯一输出后解除静音；连续循环的接缝与听感。
3. 在静音验证期间和有声期间拔出耳机，接入耳机、快速插拔、系统切换输出；外部录音验证扬声器是否存在泄漏。
4. A2DP 耳机、蓝牙音箱、HFP、USB 声卡、AirPlay、CarPlay、共享音频：无法识别为唯一 `.headphones` 时一律阻断。
5. 来电、Siri、其他音频抢占、媒体服务重置：先静音停止且不自动恢复。
6. 前台 1 分钟测试、单次与周重复、停止、一次稍后、最长 2 分钟、多闹钟同时到点。
7. 锁屏/后台/结束进程、通知拒绝与撤销、专注模式、重启、改时区/时钟：无意外补播或通知声音；界面边界说明准确。
8. 助眠后台播放、控制中心停止、暂停/换声保留截止时间、渐隐、恢复前台，以及真机个人签名安装。

通过 CI 不解除 beta 门禁，不等同于完成实体机的输出安全或后台可靠性验收。
