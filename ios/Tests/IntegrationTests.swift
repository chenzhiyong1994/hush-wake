import XCTest
import UserNotifications
import AVFoundation
import HushWakeCore
@testable import HushWake

@MainActor
final class IntegrationTests: XCTestCase {
    func testEveryNotificationIsSilentAndWeeklySnoozeRetainsRegularDays() {
        let now = Date()
        var alarm = Alarm(weekdays: Set(1...7))
        XCTAssertTrue(alarm.snooze(from: now))
        let requests = NotificationScheduler.requests(for: alarm, now: now)
        XCTAssertEqual(requests.count, 8)
        XCTAssertEqual(requests.filter { $0.trigger?.repeats == true }.count, 7)
        XCTAssertTrue(requests.allSatisfy { $0.content.sound == nil })
        XCTAssertFalse(requests.contains { $0.content.body.contains(alarm.label) })
        XCTAssertEqual(Set(requests.map(\.identifier)).count, 8)
        XCTAssertNil(NotificationScheduler.routeWarning().content.sound)
        XCTAssertTrue(NotificationScheduler.routeWarning().content.userInfo.isEmpty)
    }
    func testEightDailyAlarmsWithSnoozesFitTheNotificationBudget() {
        let now = Date()
        let requests = (0..<8).flatMap { _ -> [UNNotificationRequest] in
            var alarm = Alarm(weekdays: Set(1...7))
            alarm.snooze(from: now)
            return NotificationScheduler.requests(for: alarm, now: now)
        }
        XCTAssertEqual(requests.count, 64)
        XCTAssertEqual(Set(requests.map(\.identifier)).count, 64)
    }
    func testExpiredSnoozeDoesNotRemoveFutureWeeklyNotifications() {
        let now = Date()
        var alarm = Alarm(weekdays: [2, 6])
        alarm.snooze(from: now.addingTimeInterval(-600))
        let requests = NotificationScheduler.requests(for: alarm, now: now)
        XCTAssertEqual(requests.count, 2)
        XCTAssertTrue(requests.allSatisfy { $0.trigger?.repeats == true && $0.content.sound == nil })
    }
    func testOutOfRangeStoredPreferencesAreRejected() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LocalStore(directory: directory)
        var state = SavedState()
        state.sleepMinutes = -5
        try store.save(state)
        XCTAssertThrowsError(try store.load())
    }
    func testLocalStateRoundTripsAndCorruptionIsNotOverwritten() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LocalStore(directory: directory)
        var state = SavedState()
        state.alarms = [Alarm(label: "Test", weekdays: [2, 6])]
        try store.save(state)
        XCTAssertEqual(try store.load().alarms, state.alarms)
        XCTAssertTrue(try directory.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup == true)
        let file = directory.appendingPathComponent("state.json")
        let corrupt = Data("invalid json".utf8)
        try corrupt.write(to: file)
        XCTAssertThrowsError(try store.load())
        XCTAssertEqual(try Data(contentsOf: file), corrupt)
    }
    func testEveryBundledSoundCanBeFoundAndDecoded() throws {
        for sound in Sound.alarms + Sound.sleep {
            let url = try XCTUnwrap(Bundle.main.url(forResource: sound.id, withExtension: "m4a"))
            let player = try AVAudioPlayer(contentsOf: url)
            XCTAssertGreaterThan(player.duration, 1, sound.id)
        }
    }
}
