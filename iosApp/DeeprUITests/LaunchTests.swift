import XCTest

final class LaunchTests: XCTestCase {
    func testLaunchShowsHome() {
        let app = XCUIApplication()
        app.launch()
        // "Home" is the bottom-nav label (Res.string.home); Compose may expose it as any element type.
        let home = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Home")).firstMatch
        if !home.waitForExistence(timeout: 60) {
            print(app.debugDescription)
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.lifetime = .keepAlways
            add(shot)
            XCTFail("\"Home\" not found in the accessibility tree (dump above, screenshot in the .xcresult artifact)")
        }
    }
}
