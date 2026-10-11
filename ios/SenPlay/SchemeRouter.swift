import Foundation

enum Route {
    case play(PlayRequest)
    case download(url: String, name: String, userAgent: String?)
    case importServer(ServerConfig)
    case importIcon(String)
    case none
}

/// Parses the URL-scheme contract published with the reference app so existing
/// external callers keep working against this implementation.
enum SchemeRouter {

    private static let schemes: Set<String> = ["senplay", "senplayer"]

    static func parse(_ url: URL) -> Route {
        guard let scheme = url.scheme?.lowercased(), schemes.contains(scheme) else { return .none }
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return .none }

        let host = (components.host ?? "").lowercased()
        let path = components.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")).lowercased()
        let items = components.queryItems ?? []

        func value(_ name: String) -> String? {
            items.first { $0.name == name }?.value
        }

        switch (host, path) {
        case ("x-callback-url", "play"):
            guard let target = value("url"), !target.isEmpty else { return .none }
            let seconds = Double(value("position") ?? "") ?? 0
            let name = value("name").flatMap { $0.isEmpty ? nil : $0 } ?? target.split(separator: "/").last.map(String.init) ?? "Video"
            return .play(
                PlayRequest(
                    url: target,
                    name: name,
                    subtitleURL: value("sub"),
                    userAgent: value("User-Agent"),
                    positionMs: seconds * 1000,
                    saveToUrlList: items.contains { $0.name == "saveURL" }
                )
            )

        case ("x-callback-url", "download"):
            guard let target = value("url") else { return .none }
            return .download(url: target, name: value("name") ?? (target as NSString).lastPathComponent, userAgent: value("User-Agent"))

        default:
            if host == "importserver" || path == "importserver" {
                guard let address = value("address") else { return .none }
                let type = (value("type") ?? "webdav").lowercased()
                return .importServer(
                    ServerConfig(
                        id: UUID().uuidString,
                        type: type,
                        name: value("name") ?? type,
                        address: address,
                        username: value("username") ?? "",
                        password: value("password") ?? "",
                        note: value("note") ?? ""
                    )
                )
            }
            if host == "importicon" || path == "importicon" {
                guard let iconset = value("iconset") else { return .none }
                return .importIcon(iconset)
            }
            return .none
        }
    }

    /// x-callback-url reply on player dismissal.
    static func callbackURL(for source: URL?, lastPlayedURL: String, positionSeconds: Double) -> URL? {
        guard let source, let components = URLComponents(url: source, resolvingAgainstBaseURL: false),
              let success = components.queryItems?.first(where: { $0.name == "x-success" })?.value,
              var target = URLComponents(string: success) else { return nil }
        var items = target.queryItems ?? []
        items.append(URLQueryItem(name: "lastPlayedUrl", value: lastPlayedURL))
        items.append(URLQueryItem(name: "position", value: String(Int(positionSeconds))))
        target.queryItems = items
        return target.url
    }
}
