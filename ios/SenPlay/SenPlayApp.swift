import SwiftUI
import UIKit

@main
struct SenPlayApp: App {
    @StateObject private var store = LibraryStore.shared
    @State private var pending: PlayRequest?
    @State private var callbackSource: URL?

    var body: some Scene {
        WindowGroup {
            HomeView(store: store, pending: $pending)
                .onOpenURL { url in handle(url) }
                .fullScreenCover(item: $pending) { request in
                    PlayerScreen(request: request, onPlayNext: { next in
                        pending = next
                    }) { seconds in
                        pending = nil
                        if let source = callbackSource,
                           let reply = SchemeRouter.callbackURL(for: source, lastPlayedURL: request.url, positionSeconds: seconds) {
                            UIApplication.shared.open(reply)
                            callbackSource = nil
                        }
                    }
                }
        }
    }

    private func handle(_ url: URL) {
        switch SchemeRouter.parse(url) {
        case .play(let request):
            callbackSource = url
            var resolved = request
            if request.saveToUrlList {
                store.add(url: UrlListItem(url: request.url, name: request.name, userAgent: request.userAgent))
            }
            if resolved.positionMs <= 0 {
                resolved.positionMs = store.savedPosition(url: request.url)
            }
            pending = resolved

        case .download(let target, let name, let userAgent):
            store.add(url: UrlListItem(url: target, name: name, userAgent: userAgent))

        case .importServer(let server):
            store.save(server)

        case .importIcon(let iconset):
            store.add(url: UrlListItem(url: iconset, name: "Icon Library", userAgent: nil))

        case .none:
            if url.scheme == "http" || url.scheme == "https" {
                pending = PlayRequest(url: url.absoluteString, name: (url.absoluteString as NSString).lastPathComponent)
            }
        }
    }
}
