# 参与 HushWake

感谢你愿意让“声音去往正确的地方”这件事更可靠。HushWake 欢迎缺陷报告、设备兼容性证据、测试、文档与聚焦的代码改进；中文或英文均可。

## 开始之前

1. 阅读 [`AGENTS.md`](AGENTS.md) 中不可破坏的音频路由和隐私约束。
2. 产品行为以 [`docs/hush-wake-prd.md`](docs/hush-wake-prd.md) 为准，实体机安全结论以 [`docs/device-test-guide.md`](docs/device-test-guide.md) 为准。
3. 对较大改动先开 Issue，说明用户场景、Android 版本、预期行为和验证方法。

## 本地开发

需要 JDK 17 与 Android SDK 36。在仓库根目录运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug :app:assembleDebug
```

改动闹铃音频时还需安装 `ffmpeg` / `ffprobe` 并运行：

```powershell
.\scripts\verify-alarm-audio.ps1
```

模拟器或设备回归入口见 README。模拟器结果不能替代真实耳机的零扬声器串音验证。

## 安全边界

- 耳机会话无法确认实际输出路径时必须保持静音。
- 路由断开或变得不确定时，先将应用播放器增益归零，再停止播放。
- 不得用扬声器降级、减少路由检查、隐藏阻断结果或削弱测试来“提升兼容性”。
- 日志、截图、测试数据和 Issue 中不得包含蓝牙名称、MAC 地址、精确作息、稳定设备标识或真实凭据。

若你的改动触及上述边界，请同时提交聚焦测试，并在 PR 中列出真实设备验证状态。没有设备证据时明确写 `pending`，不要推断为已通过。

## 提交 Pull Request

- 保持改动聚焦，避免混入无关格式化或重构。
- 描述问题、实现、风险与验证证据；UI 改动附前后截图。
- 新增或替换音频必须提供固定来源、作者、明确的离线再分发许可和变更说明，并同步更新 [`docs/audio-credits.md`](docs/audio-credits.md)。
- 新增权限、数据字段或用户工作流时同步更新对应文档。

提交信息推荐使用 `feat:`、`fix:`、`docs:`、`test:`、`refactor:` 等简短前缀，但不作机械要求。
