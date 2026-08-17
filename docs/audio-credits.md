# 音频来源与许可

HushWake 的闹铃与助眠声只使用可离线再分发且许可清楚的素材。应用不会联网拉取音频，也不再实时生成合成闹铃。

## 助眠声

| 应用内名称 | 文件 | 来源 | 作者 | 许可 |
| --- | --- | --- | --- | --- |
| 绵密夜雨 | `sleep_rain.ogg` | [AMB Rain Loop 1](https://opengameart.org/content/amb-rain-loop-1) | Kresiek The Furry | CC0 1.0 |
| 林间溪流 | `gentle_stream.ogg` | [stream2.wav](https://freesound.org/people/mystiscool/sounds/7138/)；基于 [AmbientSounds 固定提交](https://github.com/Muges/ambientsounds/blob/7ef9aefeeed93c37bca3cdb246a982dd1afce2f0/stream.ogg) 的无缝循环版本再截取、转码 | mystiscool | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| 轻柔壁炉 | `soft_fireplace.ogg` | [Fireplace.wav（经 AmbientSounds 裁为无缝循环）](https://freesound.org/people/inchadney/sounds/132534/) | inchadney | CC0 1.0 |
| 清晨林鸟 | `morning_forest.ogg` | [AMB Morning Sounds (Perfect Loop)](https://opengameart.org/content/amb-morning-sounds-perfect-loop) | Kresiek The Furry | CC0 1.0 |
| 静夜虫鸣 | `night_crickets.ogg` | [Crickets Ambient Noise - loopable](https://opengameart.org/content/crickets-ambient-noise-loopable) | Wolfgang_（可选署名 Ted Kerr） | CC0 1.0 |
| 旷野微风 | `gentle_wind.ogg` | [Wind（经 AmbientSounds 裁为无缝循环）](https://github.com/Muges/ambientsounds) | felix.blume | CC0 1.0 |

## 闹铃

| 应用内名称 | 文件 | 来源 | 作者 | 许可 |
| --- | --- | --- | --- | --- |
| 晨光和弦 | `alarm_soft_bell.ogg` | [Argon.ogg](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/data/sounds/alarms/ogg/Argon.ogg) | Android Open Source Project | Apache License 2.0 |
| 清醒节拍 | `alarm_clear_bell.ogg` | [Carbon.ogg](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/data/sounds/alarms/ogg/Carbon.ogg) | Android Open Source Project | Apache License 2.0 |
| 柔光旋律 | `alarm_wind_chimes.ogg` | [Copernicium.ogg](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/data/sounds/alarms/ogg/Copernicium.ogg) | Android Open Source Project | Apache License 2.0 |
| 深稳脉冲 | `alarm_deep_bell.ogg` | [Curium.ogg](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/data/sounds/alarms/ogg/Curium.ogg) | Android Open Source Project | Apache License 2.0 |
| 霓虹晨铃 | `alarm_garden_chimes.ogg` | [Neon.ogg](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/data/sounds/alarms/ogg/Neon.ogg) | Android Open Source Project | Apache License 2.0 |
| 清脆回响 | `alarm_morning_birds.ogg` | [Platinum.ogg](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/data/sounds/alarms/ogg/Platinum.ogg) | Android Open Source Project | Apache License 2.0 |

`gentle_stream.ogg` 来自 mystiscool 的 `stream2.wav`：AmbientSounds 先将其制作成无缝循环，本项目再截取为约 120 秒并转为 48 kHz OGG；再分发时保留作者、标题、原始素材页、修改说明和 CC BY 4.0 链接。AOSP 闹铃素材的目录许可声明见同一提交的 [Android.bp](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/data/sounds/Android.bp)，许可正文随仓库保存在 [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)；CC0 音频无需署名，但仍保留来源以便审查。

闹铃素材只做响度归一化与 48 kHz OGG 转码，不改变作品许可；持续播放由应用播放器负责。新增或替换音频时必须同时记录固定版本来源、作者、许可和应用内文件名；许可不明、仅供个人使用或禁止商业使用的资源不得进入 APK。

仓库根目录的 Apache License 2.0 适用于 HushWake 自有源代码，不会覆盖或重新授权 `app/src/main/res/raw/` 中的第三方音频素材。
