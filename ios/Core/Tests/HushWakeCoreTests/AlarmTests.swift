import XCTest
@testable import HushWakeCore

final class AlarmTests: XCTestCase {
    var calendar: Calendar {
        var result = Calendar(identifier: .gregorian)
        result.timeZone = TimeZone(identifier: "Asia/Shanghai")!
        return result
    }
    func date(_ value: String) -> Date { ISO8601DateFormatter().date(from: value)! }

    func testWeeklyScheduleSkipsPastTimeAndWrapsWeek() {
        let alarm = Alarm(hour: 7, minute: 30, weekdays: [2, 3, 4, 5, 6])
        XCTAssertEqual(alarm.nextDate(after: date("2026-09-18T00:00:00Z"), calendar: calendar),
                       date("2026-09-20T23:30:00Z"))
    }
    func testOneTimeIsConsumedAndSnoozeIsAllowedOnlyOnce() {
        let now = date("2026-09-20T00:00:00Z")
        var alarm = Alarm(hour: 8, minute: 0, oneTimeDate: now)
        alarm.consume()
        XCTAssertFalse(alarm.enabled)
        XCTAssertTrue(alarm.snooze(from: now))
        XCTAssertTrue(alarm.enabled)
        XCTAssertEqual(alarm.nextDate(after: now, calendar: calendar), now.addingTimeInterval(300))
        XCTAssertFalse(alarm.snooze(from: now.addingTimeInterval(301)))
        alarm.consume()
        XCTAssertFalse(alarm.enabled)
    }
    func testSnoozedWeeklyAlarmRetainsWeekdaysAndReturnsToRegularSchedule() {
        let now = date("2026-09-21T00:00:00Z")
        var alarm = Alarm(hour: 8, minute: 0, weekdays: [2])
        XCTAssertTrue(alarm.snooze(from: now))
        XCTAssertEqual(alarm.weekdays, [2])
        alarm.consume()
        XCTAssertTrue(alarm.enabled)
        XCTAssertEqual(alarm.nextDate(after: now, calendar: calendar), date("2026-09-28T00:00:00Z"))
    }
    func testDSTGapMovesToNextValidLocalTime() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "America/Los_Angeles")!
        let alarm = Alarm(hour: 2, minute: 30, weekdays: [1])
        XCTAssertEqual(alarm.nextDate(after: date("2026-03-08T08:00:00Z"), calendar: cal),
                       date("2026-03-08T10:00:00Z"))
    }
    func testDisabledInvalidOrPastOneTimeCannotSchedule() {
        let now = Date()
        var alarm = Alarm(hour: 25, minute: 0)
        XCTAssertNil(alarm.nextDate(after: now, calendar: calendar))
        alarm = Alarm(hour: 8, minute: 0, oneTimeDate: now.addingTimeInterval(-1))
        XCTAssertNil(alarm.nextDate(after: now, calendar: calendar))
        alarm.enabled = false
        XCTAssertNil(alarm.nextDate(after: now.addingTimeInterval(-10), calendar: calendar))
    }
    func testSleepTimerDoesNotRestartOnSoundSwitchAndClampsFade() {
        let start = Date(timeIntervalSince1970: 0)
        let deadline = SleepDeadline(start: start, minutes: 5, fadeSeconds: 30)
        XCTAssertEqual(deadline.gain(at: start.addingTimeInterval(100)), 1)
        XCTAssertEqual(deadline.gain(at: start.addingTimeInterval(285)), 0.5, accuracy: 0.001)
        XCTAssertEqual(deadline.gain(at: start.addingTimeInterval(301)), 0)
        XCTAssertTrue(deadline.expired(at: start.addingTimeInterval(300)))
    }
}
