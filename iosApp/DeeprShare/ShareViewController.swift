import DeeprUI
import UIKit
import UniformTypeIdentifiers

class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        let item = extensionContext?.inputItems.first as? NSExtensionItem
        let providers = item?.attachments ?? []
        let title = item?.attributedContentText?.string
        let urlType = UTType.url.identifier
        let textType = UTType.plainText.identifier

        if let provider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(urlType) }) {
            provider.loadItem(forTypeIdentifier: urlType, options: nil) { data, _ in
                self.save((data as? URL)?.absoluteString, title: title)
            }
        } else if let provider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(textType) }) {
            provider.loadItem(forTypeIdentifier: textType, options: nil) { data, _ in
                self.save(data as? String, title: title)
            }
        } else {
            finish()
        }
    }

    private func save(_ text: String?, title: String?) {
        guard let text, !text.isEmpty else {
            DispatchQueue.main.async { self.finish() }
            return
        }
        // Same data dir as DeeprApp.swift: the App Group container (shared DB), else Application Support (unsigned builds).
        let dataDir = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.yogeshpaliyal.deepr")
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dataDir, withIntermediateDirectories: true)

        DispatchQueue.main.async {
            ShareExtensionKt.saveSharedLink(dataDir: dataDir.path, text: text, title: title) { status in
                // shortcut: English only (Compose resources aren't bundled into the extension); localize with a Strings catalog when needed.
                let messages = [
                    "saved": "Link saved",
                    "exists": "Link already exists",
                    "invalid": "Invalid link",
                    "failed": "Failed to save link",
                ]
                self.show(messages[status] ?? "")
            }
        }
    }

    private func show(_ message: String) {
        let label = UILabel()
        label.text = message
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) { self.finish() }
    }

    private func finish() {
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
}
