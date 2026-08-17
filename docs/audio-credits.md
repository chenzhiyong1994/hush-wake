# 音频来源与许可

HushWake 的闹铃与助眠声只使用可离线再分发且许可清楚的素材。应用不会联网拉取音频，也不再实时生成合成闹铃。

## 助眠声

| 应用内名称 | 文件 | 来源 | 作者 | 许可 |
| --- | --- | --- | --- | --- |
| 绵密夜雨 | `sleep_rain.ogg` | [AMB Rain Loop 1](https://opengameart.org/content/amb-rain-loop-1) | Kresiek The Furry | CC0 1.0 |
| 林间溪流 | `gentle_stream.ogg` | [Stream（经 AmbientSounds 裁为无缝循环）](https://github.com/Muges/ambientsounds) | mystiscool | CC BY（来源仓库未标版本），保留作者、标题、来源与许可 |
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

`gentle_stream.ogg` 的再分发保留上述作者、标题、来源和许可说明。AOSP 闹铃素材的目录许可声明见同一提交的 [Android.bp](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/data/sounds/Android.bp)，许可正文随仓库保存在 [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)；CC0 音频无需署名，但仍保留来源以便审查。

闹铃素材只做响度归一化与 48 kHz OGG 转码，不改变作品许可；持续播放由应用播放器负责。新增或替换音频时必须同时记录固定版本来源、作者、许可和应用内文件名；许可不明、仅供个人使用或禁止商业使用的资源不得进入 APK。
