import SwiftUI
import Combine
import MediaPlayer
import HushWakeCore

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var saved = SavedState()
    @Published var error: String?
    @Published var audioStatus = "准备好后，声音去往正确的地方"
    @Published var isPlaying = false
    @Published var notificationAllowed = false
    @Published var ringing: Alarm?
    @Published var selectedSleep = Sound.sleep[0].id
    @Published var sleepEnd: Date?
    @Published var now = Date()
    private let storage: LocalStore
    let notifications = NotificationScheduler()
    private let audio = GuardedAudio()
    private var timer: AnyCancellable?
    private var scheduled: [UUID: Date] = [:]
    private var foreground = false
    private var loaded = false
    private var ringEnd: Date?
    private var previewing = false
    private var notificationRevision = 0
    private var audioRevision = 0

    init(storage: LocalStore = LocalStore()) {
        self.storage = storage
        do { saved = try storage.load(); loaded = true }
        catch { self.error = "本地数据无法读取，已保留原文件并停止保存。请检查设备空间或重新打开应用。" }
        reconcileExpired()
        rebuild()
        audio.onChange = { [weak self] revision, message, playing in
            Task { @MainActor [weak self] in
                guard let self, revision > self.audioRevision else { return }
                self.audioRevision = revision
                self.audioStatus = message
                self.isPlaying = playing
                if !playing { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil }
            }
        }
        notifications.onOpen = { [weak self] id in self?.openReminder(id) }
        timer = Timer.publish(every: 0.5, on: .main, in: .common).autoconnect().sink { [weak self] _ in
            self?.tick()
        }
        MPRemoteCommandCenter.shared().pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.stopAudio() }; return .success
        }
        MPRemoteCommandCenter.shared().stopCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.stopAudio() }; return .success
        }
        MPRemoteCommandCenter.shared().playCommand.isEnabled = false
    }

    var nextAlarm: (Alarm, Date)? {
        saved.alarms.compactMap { alarm -> (Alarm, Date)? in
            guard let date = scheduled[alarm.id] else { return nil }
            return (alarm, date)
        }.min { $0.1 < $1.1 }
    }

    @discardableResult private func persist(_ state: SavedState) -> Bool {
        guard loaded else { error = "本地数据尚未成功读取，无法覆盖保存。"; return false }
        do { try storage.save(state); saved = state; return true }
        catch { self.error = "保存失败，修改未生效。请检查设备可用空间后重试。"; return false }
    }

    @discardableResult func save(_ alarm: Alarm) -> Bool {
        var state = saved
        var item = alarm
        item.label = String(item.label.trimmingCharacters(in: .whitespacesAndNewlines).prefix(60))
        if item.label.isEmpty { item.label = "悄然醒来" }
        item.snoozeUsed = false; item.snoozedUntil = nil
        if item.weekdays.isEmpty {
            item.oneTimeDate = nil
            var enabledCopy = item; enabledCopy.enabled = true
            item.oneTimeDate = enabledCopy.nextDate(after: Date())
        } else { item.oneTimeDate = nil }
        if let index = state.alarms.firstIndex(where: { $0.id == item.id }) { state.alarms[index] = item }
        else {
            guard state.alarms.count < 8 else { error = "最多保存 8 个闹钟。"; return false }
            state.alarms.append(item)
        }
        guard persist(state) else { return false }
        rebuild(); return true
    }

    func toggle(_ alarm: Alarm) {
        var item = alarm
        item.enabled.toggle()
        if ringing?.id == item.id { dismissAlarm() }
        _ = save(item)
    }

    func delete(_ alarm: Alarm) {
        var state = saved
        state.alarms.removeAll { $0.id == alarm.id }
        guard persist(state) else { return }
        if ringing?.id == alarm.id { dismissAlarm() }
        rebuild()
    }

    func preferences(minutes: Int? = nil, fade: Int? = nil, haptics: Bool? = nil) {
        var state = saved
        if let minutes { state.sleepMinutes = max(5, min(120, minutes)) }
        if let fade { state.fadeSeconds = [0, 15, 30].contains(fade) ? fade : 15 }
        if let haptics { state.haptics = haptics }
        _ = persist(state)
    }

    func testAlarm() {
        guard saved.alarms.count < 8 else { error = "最多保存 8 个闹钟，请先删除一个。"; return }
        let date = Date().addingTimeInterval(60)
        let components = Calendar.current.dateComponents([.hour, .minute], from: date)
        var state = saved
        state.alarms.append(Alarm(label: "1 分钟测试", hour: components.hour!, minute: components.minute!, oneTimeDate: date))
        if persist(state) { rebuild() }
    }

    func scene(_ phase: ScenePhase) {
        if phase == .active {
            // Consume background occurrences silently before enabling foreground playback.
            tick()
            foreground = true
            reconcileExpired()
            rebuild()
            Task { notificationAllowed = await notifications.authorized() }
        } else if phase == .inactive {
            foreground = false
        } else if phase == .background {
            foreground = false
            if ringing != nil { dismissAlarm() }
            if previewing { stopAudio() }
        }
    }

    func requestNotifications() {
        Task {
            do { notificationAllowed = try await notifications.requestPermission(); rebuild() }
            catch { self.error = "通知授权未完成，请在系统设置中检查。" }
        }
    }

    private func reconcileExpired() {
        guard loaded else { return }
        var state = saved
        for index in state.alarms.indices {
            if let until = state.alarms[index].snoozedUntil, until <= Date() { state.alarms[index].consume() }
            else if state.alarms[index].weekdays.isEmpty, let date = state.alarms[index].oneTimeDate,
                    date <= Date(), state.alarms[index].snoozedUntil == nil { state.alarms[index].enabled = false }
        }
        if state.alarms != saved.alarms { _ = persist(state) }
    }

    private func rebuild() {
        scheduled = Dictionary(uniqueKeysWithValues: saved.alarms.compactMap { alarm in
            alarm.nextDate(after: Date()).map { (alarm.id, $0) }
        })
        notificationRevision += 1
        let revision = notificationRevision
        let alarms = saved.alarms
        Task {
            do { try await notifications.replace(alarms: alarms) }
            catch {
                if revision == notificationRevision { self.error = "系统通知登记失败。前台闹钟仍可运行，请打开设置检查通知权限。" }
            }
        }
    }

    private func tick() {
        now = Date()
        if let ringEnd, now >= ringEnd { dismissAlarm() }
        if let sleepEnd, now >= sleepEnd { stopAudio() }
        let due = saved.alarms.filter { scheduled[$0.id].map { $0 <= now } ?? false }
            .sorted { (scheduled[$0.id] ?? .distantFuture) < (scheduled[$1.id] ?? .distantFuture) }
        guard !due.isEmpty else { return }
        var state = saved
        var candidate: Alarm?
        for alarm in due {
            var occurrence = alarm
            if alarm.snoozedUntil == nil { occurrence.snoozeUsed = false }
            let date = scheduled.removeValue(forKey: alarm.id)!
            if let index = state.alarms.firstIndex(where: { $0.id == alarm.id }) {
                state.alarms[index].snoozeUsed = occurrence.snoozeUsed
                state.alarms[index].consume()
            }
            if candidate == nil, now.timeIntervalSince(date) < 5, foreground { candidate = occurrence }
        }
        // If storage fails, do not ring an occurrence that could be replayed on restart.
        guard persist(state) else { return }
        rebuild()
        if let candidate, ringing == nil { ring(candidate) }
    }

    private func ring(_ alarm: Alarm) {
        stopAudio()
        ringing = alarm
        ringEnd = Date().addingTimeInterval(120)
        _ = audio.play(sound: alarm.sound, until: ringEnd!)
        if saved.haptics { UINotificationFeedbackGenerator().notificationOccurred(.warning) }
    }

    private func openReminder(_ id: UUID) {
        guard let alarm = saved.alarms.first(where: { $0.id == id }) else { return }
        stopAudio()
        ringing = alarm
        ringEnd = Date().addingTimeInterval(120)
        audioStatus = "这是无声通知提醒。需要声音时，请手动开始播放。"
    }

    func playReminder() {
        guard let ringing else { return }
        ringEnd = Date().addingTimeInterval(120)
        _ = audio.play(sound: ringing.sound, until: ringEnd!)
    }

    func dismissAlarm() { stopAudio(); ringing = nil; ringEnd = nil }

    func snooze() {
        guard let ringing, let index = saved.alarms.firstIndex(where: { $0.id == ringing.id }) else { return }
        var state = saved
        state.alarms[index].snoozeUsed = ringing.snoozeUsed
        guard state.alarms[index].snooze(from: Date()), persist(state) else { return }
        dismissAlarm(); rebuild()
    }

    func playSleep(_ sound: String? = nil) {
        if let sound { selectedSleep = sound }
        previewing = false
        let end = sleepEnd.flatMap { $0 > Date() ? $0 : nil }
            ?? SleepDeadline(start: Date(), minutes: saved.sleepMinutes, fadeSeconds: Double(saved.fadeSeconds)).end
        sleepEnd = end
        if audio.play(sound: selectedSleep, until: end, fadeSeconds: Double(saved.fadeSeconds)) {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = [
                MPMediaItemPropertyTitle: Sound.sleep.first(where: { $0.id == selectedSleep })?.name ?? "悄醒",
                MPMediaItemPropertyArtist: "HushWake", MPNowPlayingInfoPropertyPlaybackRate: 1.0
            ]
        }
    }

    func pauseSleep() { audio.stop() } // Deadline deliberately continues while paused.
    func stopAudio() {
        audio.stop(); sleepEnd = nil; previewing = false
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
    func preview(_ sound: String) {
        stopAudio(); previewing = true
        _ = audio.play(sound: sound, until: Date().addingTimeInterval(10))
    }
    func stopPreview() { if previewing { stopAudio() } }
}
