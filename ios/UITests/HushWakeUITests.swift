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
        XCTAssertTrue(app.buttons["alarm-UITest Nap"].waitForExistence(timeout: 5))
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["alarm-UITest Nap"].waitForExistence(timeout: 10))
        attachScreenshot("Alarms")
        app.tabBars.buttons["助眠"].tap()
        XCTAssertTrue(app.staticTexts["绵密夜雨"].waitForExistence(timeout: 5))
        attachScreenshot("Sleep")
        app.tabBars.buttons["说明"].tap()
        XCTAssertTrue(app.navigationBars["关于悄醒"].waitForExistence(timeout: 5))
        attachScreenshot("About")
    }

    @MainActor private func attachScreenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
