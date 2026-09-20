import Foundation

public struct Alarm: Codable, Identifiable, Equatable, Sendable {
    public var id: UUID
    public var label: String
    public var hour: Int
    public var minute: Int
    /// Calendar weekday: Sunday = 1, Saturday = 7. Empty means one-time.
    public var weekdays: Set<Int>
    public var enabled: Bool
    public var sound: String
    public var oneTimeDate: Date?
    public var snoozedUntil: Date?
    public var snoozeUsed: Bool

    public init(id: UUID = UUID(), label: String = "悄然醒来", hour: Int = 7, minute: Int = 30,
                weekdays: Set<Int> = [], enabled: Bool = true, sound: String = "alarm_soft_bell",
                oneTimeDate: Date? = nil) {
        self.id = id; self.label = label; self.hour = hour; self.minute = minute
        self.weekdays = weekdays; self.enabled = enabled; self.sound = sound
        self.oneTimeDate = oneTimeDate; snoozedUntil = nil; snoozeUsed = false
    }

    public func nextDate(after date: Date, calendar: Calendar = .current) -> Date? {
        guard enabled, (0...23).contains(hour), (0...59).contains(minute),
              weekdays.allSatisfy({ (1...7).contains($0) }) else { return nil }
        if let snoozedUntil { return snoozedUntil > date ? snoozedUntil : nil }
        if weekdays.isEmpty, let oneTimeDate { return oneTimeDate > date ? oneTimeDate : nil }
        let days: [Int?] = weekdays.isEmpty ? [nil] : weekdays.sorted().map(Optional.some)
        return days.compactMap { weekday in
            var components = DateComponents()
            components.hour = hour; components.minute = minute; components.second = 0
            components.weekday = weekday
            return calendar.nextDate(after: date, matching: components,
                                     matchingPolicy: .nextTime, repeatedTimePolicy: .first)
        }.min()
    }

    public mutating func consume() {
        snoozedUntil = nil
        if weekdays.isEmpty { enabled = false }
    }

    @discardableResult public mutating func snooze(from date: Date) -> Bool {
        guard !snoozeUsed else { return false }
        snoozeUsed = true
        snoozedUntil = date.addingTimeInterval(300)
        enabled = true
        return true
    }
}

public struct SleepDeadline: Sendable {
    public let end: Date
    public let fadeSeconds: TimeInterval
    public init(start: Date, minutes: Int, fadeSeconds: TimeInterval) {
        end = start.addingTimeInterval(TimeInterval(max(5, min(120, minutes))) * 60)
        self.fadeSeconds = max(0, min(30, fadeSeconds))
    }
    public func expired(at date: Date) -> Bool { date >= end }
    public func gain(at date: Date) -> Float {
        let remaining = end.timeIntervalSince(date)
        if remaining <= 0 { return 0 }
        return fadeSeconds > 0 ? Float(min(1, remaining / fadeSeconds)) : 1
    }
}
