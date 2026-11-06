import SwiftUI
import DeeprUI

@main
struct DeeprApp: App {
    init() {
        // The Share Extension (M4-03) shares the same DB, so prefer the App Group
        // container; fall back to Application Support for unsigned/simulator builds.
        let dataDir: URL
        if let container =
            FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: "group.com.yogeshpaliyal.deepr"
            ) {
            dataDir = container
        } else {
            dataDir = FileManager.default.urls(
                for: .applicationSupportDirectory, in: .userDomainMask
            )[0]
        }
        try? FileManager.default.createDirectory(
            at: dataDir, withIntermediateDirectories: true
        )
        MainViewControllerKt.doInitKoin(dataDir: dataDir.path, withP2p: true)
    }

    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
    }
}
