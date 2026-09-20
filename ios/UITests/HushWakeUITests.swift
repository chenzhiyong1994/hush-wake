import XCTest

final class HushWakeUITests: XCTestCase {
    @MainActor
    func testAlarmCreationPersistsAfterRelaunchAndSleepScreenOpens() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["add-alarm"].waitForExistence(timeout: 10))
        app.buttons["add-alarm"].tap()
        let label = app.textFields["alarm-label"]
        XCTAssertTrue(label.waitForExistence(timeout: 5))
        label.tap()
        let original = label.value as? String ?? ""
        label.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: original.count) + "UITest Nap")
        app.buttons["save-alarm"].tap()
        XCTAssertTrue(app.staticTexts["UITest Nap"].firstMatch.waitForExistence(timeout: 5))
        app.terminate()
        app.launch()
        XCTAssertTrue(app.staticTexts["UITest Nap"].firstMatch.waitForExistence(timeout: 10))
        attachScreenshot("Alarms")
        app.tabBars.buttons["助眠"].tap()
        XCTAssertTrue(app.navigationBars["慢慢安静"].waitForExistence(timeout: 5))
        let rain = app.buttons["sleep-sound-sleep_rain"]
        for _ in 0..<4 {
            if rain.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(rain.isHittable)
        rain.tap()
        XCTAssertEqual(rain.value as? String, "已选择")
        attachScreenshot("Sleep")
        app.tabBars.buttons["说明"].tap()
        XCTAssertTrue(app.navigationBars["关于悄醒"].waitForExistence(timeout: 5))
        attachScreenshot("About")
    }

    @MainActor
    func testOneMinuteAlarmReachesReminderAndCanSnooze() {
        let app = XCUIApplication()
        app.launch()
        let testButton = app.buttons["test-alarm"]
        XCTAssertTrue(app.buttons["add-alarm"].waitForExistence(timeout: 10))
        for _ in 0..<5 {
            if testButton.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(testButton.isHittable)
        testButton.tap()
        XCTAssertFalse(app.buttons["stop-alarm"].exists)
        XCTAssertTrue(app.buttons["stop-alarm"].waitForExistence(timeout: 75))
        attachScreenshot("Ringing")
        app.buttons["稍后 5 分钟"].tap()
        XCTAssertTrue(app.buttons["add-alarm"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["stop-alarm"].exists)
    }

    @MainActor private func attachScreenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
