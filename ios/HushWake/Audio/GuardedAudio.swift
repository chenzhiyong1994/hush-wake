import AVFoundation
import HushWakeCore

private final class PlayerSink: AudioSink {
    var player: AVAudioPlayer?
    func mute() { player?.volume = 0 }
    func stop() { player?.stop(); player = nil }
    func unmute() { player?.volume = 1 }
}

/// AVAudioSession notifications run on the posting thread. The same lock protects
/// validation, gain and stop; muting must never wait for the main/UI queue.
final class GuardedAudio: @unchecked Sendable {
    private let lock = NSRecursiveLock()
    private let sink = PlayerSink()
    private lazy var routeGuard = RouteGuard(sink: sink)
    private let session = AVAudioSession.sharedInstance()
    private var observers: [NSObjectProtocol] = []
    private var timer: DispatchSourceTimer?
    private var deadline: Date?
    private var fade: TimeInterval = 0
    private var generation = 0
    var onChange: (@Sendable (String, Bool) -> Void)?

    init() {
        for name in [AVAudioSession.routeChangeNotification, AVAudioSession.interruptionNotification,
                     AVAudioSession.mediaServicesWereLostNotification,
                     AVAudioSession.mediaServicesWereResetNotification] {
            observers.append(NotificationCenter.default.addObserver(forName: name, object: nil, queue: nil) {
                [weak self] _ in self?.invalidate()
            })
        }
    }

    deinit {
        timer?.cancel()
        observers.forEach(NotificationCenter.default.removeObserver)
        sink.mute()
        sink.stop()
    }

    private func outputs() -> [Output] {
        session.currentRoute.outputs.map { port in
            let kind: Output.Kind
            switch port.portType {
            case .builtInSpeaker: kind = .speaker
            case .headphones: kind = .headphones
            // A2DP/HFP/USB describe a transport, not an exclusively private headset.
            // Names and model heuristics cannot supply the missing evidence.
            default: kind = .unknown
            }
            return Output(kind: kind, id: port.uid)
        }
    }

    @discardableResult
    func play(sound: String, until end: Date, fadeSeconds: TimeInterval = 0) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        finishLocked()
        let before = outputs()
        do {
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
            guard before == outputs(), routeGuard.begin(outputs: before) else {
                blockLocked("输出未确认：仅支持手机扬声器或系统明确识别的有线耳机。")
                return false
            }
            guard let url = Bundle.main.url(forResource: sound, withExtension: "m4a"), end > Date() else {
                blockLocked("声音文件不可用或播放时间已结束。")
                return false
            }
            let player = try AVAudioPlayer(contentsOf: url)
            player.volume = 0
            player.numberOfLoops = -1
            sink.player = player
            guard player.prepareToPlay(), player.play(), routeGuard.verify(outputs: outputs()) else {
                blockLocked("未能验证本次输出，已保持静音。")
                return false
            }
            deadline = end
            fade = fadeSeconds
            let revisionSnapshot = generation
            let timer = DispatchSource.makeTimerSource(queue: DispatchQueue(label: "com.hushwake.audio-watch"))
            timer.schedule(deadline: .now(), repeating: .milliseconds(50))
            timer.setEventHandler { [weak self] in self?.tick(revisionSnapshot: revisionSnapshot) }
            self.timer = timer
            timer.resume()
            onChange?(before.first?.kind == .headphones ? "有线耳机播放中 · 路由守卫开启" : "手机扬声器播放中", true)
            return true
        } catch {
            blockLocked("无法启动音频会话，已保持静音。")
            return false
        }
    }

    func stop() {
        lock.lock()
        finishLocked()
        onChange?("播放已停止", false)
        lock.unlock()
        // Deactivation may itself post notifications. No active player remains.
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func finishLocked() {
        routeGuard.end()
        generation += 1
        timer?.cancel()
        timer = nil
        deadline = nil
    }

    private func blockLocked(_ message: String) {
        routeGuard.invalidate()
        generation += 1
        timer?.cancel()
        timer = nil
        deadline = nil
        onChange?(message, false)
    }

    private func invalidate() {
        lock.lock()
        defer { lock.unlock() }
        guard routeGuard.state == .playing || routeGuard.state == .verifying else { return }
        blockLocked("音频路由变化或播放被打断，已先静音再停止。请确认输出后手动重试。")
    }

    private func tick(revisionSnapshot: Int) {
        lock.lock()
        defer { lock.unlock() }
        guard revisionSnapshot == generation, routeGuard.state == .playing else { return }
        guard routeGuard.matches(outputs: outputs()) else {
            blockLocked("输出路径不再匹配，已保持静音。")
            return
        }
        guard let deadline, Date() < deadline else {
            finishLocked()
            onChange?("定时结束", false)
            return
        }
        guard sink.player?.isPlaying == true else {
            blockLocked("系统已停止播放，请手动重新开始。")
            return
        }
        if fade > 0 { sink.player?.volume = Float(min(1, max(0, deadline.timeIntervalSinceNow / fade))) }
    }
}
