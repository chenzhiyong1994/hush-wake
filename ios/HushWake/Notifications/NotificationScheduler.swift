import Foundation
import UserNotifications
import HushWakeCore

/// Alerts intentionally have no sound; iOS notification audio cannot use our route guard.
@MainActor
final class NotificationScheduler: NSObject, UNUserNotificationCenterDelegate {
    private let center = UNUserNotificationCenter.current()
    var onOpen: ((UUID) -> Void)?
    private var revision = 0
    private var updateTask: Task<Void, Error>?

    override init() {
        super.init()
        center.delegate = self
    }

    func requestPermission() async throws -> Bool {
        try await center.requestAuthorization(options: [.alert, .badge])
    }

    func authorized() async -> Bool {
        let settings = await center.notificationSettings()
        return settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
    }

    static func routeWarning() -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = "悄醒 · 输出已阻断"
        content.body = "音频输出发生变化或无法确认，应用已静音并停止。请确认输出后手动重新播放。"
        content.sound = nil
        return UNNotificationRequest(identifier: "hushwake-output-blocked", content: content, trigger: nil)
    }

    func notifyRouteBlocked() async {
        guard await authorized() else { return }
        // A denied/failed fallback never changes the already-muted player state.
        try? await center.add(Self.routeWarning())
    }

    func replace(alarms: [Alarm]) async throws {
        revision += 1
        let revisionSnapshot = revision
        let previous = updateTask
        let task = Task { @MainActor [weak self] in
            _ = try? await previous?.value
            guard let self, revisionSnapshot == self.revision else { return }
            let permitted = await self.authorized()
            guard revisionSnapshot == self.revision else { return }
            self.center.removeAllPendingNotificationRequests()
            guard permitted else { return } // Permission warning stays inline; saving alarms remains usable.
            for alarm in alarms where alarm.enabled {
                for request in Self.requests(for: alarm, now: Date()) {
                    guard revisionSnapshot == self.revision else { return }
                    try await self.center.add(request)
                }
            }
        }
        updateTask = task
        try await task.value
    }

    static func requests(for alarm: Alarm, now: Date) -> [UNNotificationRequest] {
        guard alarm.enabled else { return [] }
        let content = UNMutableNotificationContent()
        content.title = "悄醒 · 到提醒时间了"
        content.body = "后台提醒为无声通知。打开应用查看，或手动开始受路由保护的播放。"
        content.sound = nil
        content.userInfo = ["alarmID": alarm.id.uuidString]
        content.threadIdentifier = "hushwake-alarms"
        var requests: [UNNotificationRequest] = []
        if alarm.weekdays.isEmpty || alarm.snoozedUntil != nil {
            if let date = alarm.nextDate(after: now) {
                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, date.timeIntervalSince(now)), repeats: false)
                requests.append(UNNotificationRequest(identifier: alarm.id.uuidString + ".once", content: content, trigger: trigger))
            }
        }
        requests += alarm.weekdays.sorted().map { day in
            var components = DateComponents()
            components.weekday = day; components.hour = alarm.hour; components.minute = alarm.minute
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            return UNNotificationRequest(identifier: alarm.id.uuidString + "." + String(day), content: content, trigger: trigger)
        }
        return requests
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list]) // Never .sound, even in the foreground.
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        let value = response.notification.request.content.userInfo["alarmID"] as? String
        Task { @MainActor [weak self] in
            if let value, let id = UUID(uuidString: value) { self?.onOpen?(id) }
            completionHandler()
        }
    }
}
