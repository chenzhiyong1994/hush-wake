import Foundation

struct Sound: Identifiable {
    let id: String
    let name: String
    let symbol: String
    static let alarms = [
        Sound(id: "alarm_soft_bell", name: "晨光和弦", symbol: "sunrise"),
        Sound(id: "alarm_clear_bell", name: "清醒节拍", symbol: "bell"),
        Sound(id: "alarm_wind_chimes", name: "柔光旋律", symbol: "wind"),
        Sound(id: "alarm_deep_bell", name: "深稳脉冲", symbol: "waveform"),
        Sound(id: "alarm_garden_chimes", name: "霓虹晨铃", symbol: "sparkles"),
        Sound(id: "alarm_morning_birds", name: "清脆回响", symbol: "bird")
    ]
    static let sleep = [
        Sound(id: "sleep_rain", name: "绵密夜雨", symbol: "cloud.rain"),
        Sound(id: "gentle_stream", name: "林间溪流", symbol: "water.waves"),
        Sound(id: "soft_fireplace", name: "轻柔壁炉", symbol: "flame"),
        Sound(id: "morning_forest", name: "清晨林鸟", symbol: "bird"),
        Sound(id: "night_crickets", name: "静夜虫鸣", symbol: "moon.stars"),
        Sound(id: "gentle_wind", name: "旷野微风", symbol: "wind"),
        Sound(id: "ocean_waves", name: "深夜海浪", symbol: "water.waves"),
        Sound(id: "distant_thunder", name: "远方雷雨", symbol: "cloud.bolt.rain")
    ]
}
