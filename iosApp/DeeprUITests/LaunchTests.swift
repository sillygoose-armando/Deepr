import XCTest

final class LaunchTests: XCTestCase {
    func testLaunchShowsHome() {
        let app = XCUIApplication()
        app.launch()
        // Compose Multiplatform exposes semantics to the iOS accessibility tree; "Home" is the bottom-nav label (Res.string.home).
        XCTAssertTrue(app.staticTexts["Home"].waitForExistence(timeout: 60) || app.buttons["Home"].waitForExistence(timeout: 5))
    }
}