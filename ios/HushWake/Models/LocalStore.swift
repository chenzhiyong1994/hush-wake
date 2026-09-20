import Foundation
import HushWakeCore

struct SavedState: Codable {
    var alarms: [Alarm] = []
    var haptics = false
    var sleepMinutes = 30
    var fadeSeconds = 15
}

struct LocalStore {
    let directory: URL
    init(directory: URL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("HushWake", isDirectory: true)) {
        self.directory = directory
    }
    private var file: URL { directory.appendingPathComponent("state.json") }
    func load() throws -> SavedState {
        guard FileManager.default.fileExists(atPath: file.path) else { return SavedState() }
        let state = try JSONDecoder().decode(SavedState.self, from: Data(contentsOf: file))
        guard state.alarms.count <= 8,
              (5...120).contains(state.sleepMinutes), [0, 15, 30].contains(state.fadeSeconds),
              Set(state.alarms.map(\.id)).count == state.alarms.count,
              state.alarms.allSatisfy({ alarm in (0...23).contains(alarm.hour) && (0...59).contains(alarm.minute)
                  && alarm.label.count <= 60
                  && alarm.weekdays.allSatisfy { (1...7).contains($0) }
                  && Sound.alarms.contains(where: { $0.id == alarm.sound }) }) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        return state
    }
    func save(_ state: SavedState) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var location = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try location.setResourceValues(values)
        let data = try JSONEncoder().encode(state)
        try data.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
