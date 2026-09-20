import SwiftUI
import HushWakeCore

struct ContentView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        TabView {
            AlarmListView().tabItem { Label("闹钟", systemImage: "alarm") }
            SleepView().tabItem { Label("助眠", systemImage: "moon.stars") }
            SettingsView().tabItem { Label("说明", systemImage: "info.circle") }
        }
        .sheet(item: $model.ringing, onDismiss: { model.dismissAlarm() }) { alarm in
            RingingView(alarm: alarm).interactiveDismissDisabled()
        }
        .alert("需要留意", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) {
            Button("知道了", role: .cancel) { model.error = nil }
        } message: { Text(model.error ?? "") }
    }
}

struct OutputStatus: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 5) {
                Text(model.audioStatus).font(.subheadline.weight(.medium))
                Text("路由不确定时保持静音，音量跟随系统媒体设置。")
                    .font(.caption).foregroundStyle(.secondary)
            }
        } icon: { Image(systemName: model.isPlaying ? "waveform" : "shield.lefthalf.filled").foregroundStyle(.mint) }
        .padding(.vertical, 6)
    }
}

struct AlarmListView: View {
    @EnvironmentObject private var model: AppModel
    @State private var editing: Alarm?
    var body: some View {
        NavigationStack {
            List {
                Section { OutputStatus() }
                Section {
                    Text("有声闹钟需保持应用在前台。后台或锁屏时只发送无声通知，不能保证叫醒。")
                        .font(.subheadline).foregroundStyle(.secondary)
                    if !model.notificationAllowed {
                        Button("开启无声提醒通知") { model.requestNotifications() }
                        Button("打开系统设置") { openSettings() }.font(.caption)
                    }
                }
                if let (alarm, date) = model.nextAlarm {
                    Section("下一次提醒") {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(date, style: .time).font(.system(size: 54, weight: .light, design: .rounded)).monospacedDigit()
                            Text(alarm.label).font(.headline)
                            Text(date, format: .dateTime.month().day().weekday()).foregroundStyle(.secondary)
                        }.padding(.vertical, 10)
                    }
                }
                Section("我的闹钟 · \(model.saved.alarms.count)/8") {
                    if model.saved.alarms.isEmpty {
                        ContentUnavailableView("留一点时间给自己", systemImage: "sunrise",
                                               description: Text("添加第一个闹钟，或先试一次 1 分钟提醒。"))
                    }
                    ForEach(model.saved.alarms) { alarm in
                        HStack {
                            Button { editing = alarm } label: {
                                VStack(alignment: .leading, spacing: 5) {
                                    Text(String(format: "%02d:%02d", alarm.hour, alarm.minute))
                                        .font(.system(.largeTitle, design: .rounded)).monospacedDigit()
                                    Text(alarm.label).font(.subheadline)
                                    Text(repeatDescription(alarm)).font(.caption).foregroundStyle(.secondary)
                                    if let date = alarm.snoozedUntil { Text("稍后提醒：\(date.formatted(date: .omitted, time: .shortened))").font(.caption).foregroundStyle(.orange) }
                                }.foregroundStyle(.primary)
                            }.buttonStyle(.plain).accessibilityIdentifier("alarm-\(alarm.label)")
                            Spacer()
                            Toggle("启用 \(alarm.label)", isOn: Binding(get: { alarm.enabled }, set: { _ in model.toggle(alarm) }))
                                .labelsHidden()
                        }.padding(.vertical, 5)
                        .swipeActions { Button("删除", role: .destructive) { model.delete(alarm) } }
                    }
                }
                Section {
                    Button { editing = Alarm() } label: { Label("添加闹钟", systemImage: "plus.circle.fill") }
                        .accessibilityIdentifier("add-alarm")
                    Button("1 分钟测试") { model.testAlarm() }.accessibilityIdentifier("test-alarm")
                }
            }
            .navigationTitle("悄醒")
            .sheet(item: $editing) { alarm in AlarmEditor(alarm: alarm) }
        }
    }
}

struct AlarmEditor: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State var alarm: Alarm
    @State private var time = Date()
    var body: some View {
        NavigationStack {
            Form {
                DatePicker("时间", selection: $time, displayedComponents: .hourAndMinute).datePickerStyle(.wheel)
                TextField("闹钟名称", text: $alarm.label).accessibilityIdentifier("alarm-label")
                Section("每周重复 · 不选为单次") {
                    ForEach(1...7, id: \.self) { day in
                        Toggle(weekday(day), isOn: Binding(get: { alarm.weekdays.contains(day) }, set: {
                            if $0 { alarm.weekdays.insert(day) } else { alarm.weekdays.remove(day) }
                        }))
                    }
                }
                Section("闹铃") {
                    Picker("声音", selection: $alarm.sound) {
                        ForEach(Sound.alarms) { Text($0.name).tag($0.id) }
                    }
                    Button("试听 10 秒") { model.preview(alarm.sound) }
                    Button("停止试听") { model.stopAudio() }
                    OutputStatus()
                }
                Text("后台只提供无声通知。请先在前台测试你的手机和输出设备。")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            .navigationTitle("编辑闹钟").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        let parts = Calendar.current.dateComponents([.hour, .minute], from: time)
                        alarm.hour = parts.hour!; alarm.minute = parts.minute!
                        if model.save(alarm) { dismiss() }
                    }.accessibilityIdentifier("save-alarm")
                }
            }
            .onAppear { time = Calendar.current.date(bySettingHour: alarm.hour, minute: alarm.minute, second: 0, of: Date()) ?? Date() }
            .onDisappear { model.stopAudio() }
        }
    }
}

struct SleepView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        NavigationStack {
            List {
                Section { OutputStatus() }
                Section {
                    VStack(alignment: .leading, spacing: 12) {
                        Image(systemName: "moon.stars").font(.system(size: 36)).foregroundStyle(.mint)
                        Text("把一小片自然，\n带进今晚。").font(.largeTitle.weight(.light))
                        Text("8 种离线环境录音 · 支持后台播放").font(.subheadline).foregroundStyle(.secondary)
                    }.padding(.vertical, 14)
                }
                Section("选择声音") {
                    ForEach(Sound.sleep) { sound in
                        Button {
                            model.selectedSleep = sound.id
                            if model.isPlaying { model.playSleep(sound.id) }
                        } label: {
                            HStack {
                                Label(sound.name, systemImage: sound.symbol)
                                Spacer()
                                if model.selectedSleep == sound.id { Image(systemName: "checkmark.circle.fill") }
                            }.padding(.vertical, 5)
                        }.foregroundStyle(model.selectedSleep == sound.id ? .mint : .primary)
                    }
                }
                Section("睡眠定时") {
                    Text("\(model.saved.sleepMinutes) 分钟")
                    Slider(value: Binding(get: { Double(model.saved.sleepMinutes) }, set: { model.preferences(minutes: Int($0)) }),
                           in: 5...120, step: 5).accessibilityLabel("定时分钟")
                    Picker("结束渐隐", selection: Binding(get: { model.saved.fadeSeconds }, set: { model.preferences(fade: $0) })) {
                        Text("立即结束").tag(0); Text("15 秒").tag(15); Text("30 秒").tag(30)
                    }
                    if let end = model.sleepEnd {
                        Text("本次结束：\(end.formatted(date: .omitted, time: .shortened))")
                        Text("暂停和换声保留本次结束时间；定时设置在下次开始时生效。")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Button(model.isPlaying ? "暂停助眠声" : "播放助眠声") {
                        if model.isPlaying { model.pauseSleep() } else { model.playSleep() }
                    }.accessibilityIdentifier("sleep-play")
                    if model.sleepEnd != nil { Button("结束本次助眠", role: .destructive) { model.stopAudio() } }
                }
            }.navigationTitle("慢慢安静")
        }
    }
}

struct RingingView: View {
    @EnvironmentObject private var model: AppModel
    let alarm: Alarm
    var body: some View {
        VStack(spacing: 28) {
            Spacer()
            Image(systemName: "sunrise.fill").font(.system(size: 60)).foregroundStyle(.orange)
            Text(model.now, style: .time).font(.system(size: 62, weight: .light, design: .rounded))
            Text(alarm.label).font(.title2)
            OutputStatus()
            if !model.isPlaying { Button("手动开始播放") { model.playReminder() }.buttonStyle(.bordered) }
            Spacer()
            Button("停止提醒") { model.dismissAlarm() }.buttonStyle(.borderedProminent).controlSize(.large)
                .accessibilityIdentifier("stop-alarm")
            if !alarm.snoozeUsed { Button("稍后 5 分钟") { model.snooze() }.controlSize(.large) }
            Text("本次提醒最多持续 2 分钟").font(.caption).foregroundStyle(.secondary)
        }.padding(28)
    }
}

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        NavigationStack {
            Form {
                Section("HushWake · iOS Beta") {
                    Text("轻声入睡，悄然醒来。").font(.title2)
                    Text("开源测试版 · iOS 17 及以上")
                }
                Section("提醒偏好") {
                    Toggle("前台提醒时轻触反馈", isOn: Binding(get: { model.saved.haptics }, set: { model.preferences(haptics: $0) }))
                    Text("默认关闭；开启后仅在前台触发一次，不保证持续振动。后台通知不请求声音。")
                    Button("允许无声通知") { model.requestNotifications() }
                    Button("打开系统设置") { openSettings() }
                }
                Section("播放与提醒边界") {
                    Text("无耳机时允许手机扬声器播放；系统明确识别的唯一有线耳机经本次路由检查后可播放。蓝牙、USB、AirPlay、车载、听筒及未知输出保持静音。")
                    Text("断连、路由变化或系统中断时先静音再停止。不会自动恢复，请确认输出后手动重新播放。实体机零串音验证仍待完成。")
                    Text("有声闹钟只在应用前台运行；进入后台或锁屏时以系统无声通知提醒。专注模式、通知设置或系统调度可能延迟或隐藏提醒。请勿将本测试版作为唯一叫醒方式。")
                }
                Section("数据与许可") {
                    Text("闹钟与偏好仅保存在本机，数据目录排除系统备份。无账号、广告、分析或网络上传，不保存耳机名称及硬件标识。卸载将删除本地数据。")
                    Text("不提供医疗效果，也不能在关机、无电或强制结束应用时保证唤醒。")
                    NavigationLink("音频来源与开源许可") { CreditsView() }
                    Link("GitHub 源代码与下载", destination: URL(string: "https://github.com/chenzhiyong1994/hush-wake")!)
                }
            }.navigationTitle("关于悄醒")
        }
    }
}

struct CreditsView: View {
    var body: some View {
        ScrollView {
            Text(credits).font(.footnote).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading).padding()
        }.navigationTitle("来源与许可")
    }
    private var credits: String {
        guard let url = Bundle.main.url(forResource: "AudioCredits", withExtension: "txt"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return "许可文件不可用，请查看 GitHub 的 docs/audio-credits.md。" }
        return text
    }
}

private func openSettings() {
    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
}
private func weekday(_ day: Int) -> String { ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][day - 1] }
private func repeatDescription(_ alarm: Alarm) -> String {
    alarm.weekdays.isEmpty ? "单次" : alarm.weekdays.sorted().map(weekday).joined(separator: " · ")
}
