import Foundation

/// Plex talks XML over HTTP with a token header. The token goes in the password
/// field when the server is configured, which keeps one credential UI for every
/// server type.
struct PlexLibrary: Identifiable, Hashable {
    var id: String
    var title: String
    var type: String
}

struct PlexItem: Identifiable, Hashable {
    var id: String
    var title: String
    var year: Int?
    var durationMs: Double
    var thumbPath: String?
    var partPath: String?
    var partSize: Int
    /// Playback position reported by the server, for the resume strip.
    var viewOffsetMs: Double
    var isPlayed: Bool
}

final class PlexClient {
    private let server: ServerConfig
    private(set) var token: String

    init(server: ServerConfig) {
        self.server = server
        self.token = server.password.isEmpty ? server.username : server.password
    }

    private var base: String {
        var address = server.address
        if !address.lowercased().hasPrefix("http") { address = "http://" + address }
        return address.hasSuffix("/") ? String(address.dropLast()) : address
    }

    private func request(_ path: String) -> URLRequest? {
        guard var components = URLComponents(string: base + path) else { return nil }
        var query = components.queryItems ?? []
        query.append(URLQueryItem(name: "X-Plex-Token", value: token))
        components.queryItems = query
        guard let url = components.url else { return nil }
        var request = URLRequest(url: url)
        request.setValue("application/xml", forHTTPHeaderField: "Accept")
        request.setValue("SenPlay/1.2 (iOS)", forHTTPHeaderField: "X-Plex-Product")
        request.setValue("senplay-ios", forHTTPHeaderField: "X-Plex-Client-Identifier")
        return request
    }

    private func fetch(_ path: String) async throws -> PlexXMLNode {
        guard let request = request(path) else { throw ServerError.badAddress }
        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw ServerError.http(http.statusCode)
        }
        let parser = XMLParser(data: data)
        let delegate = PlexXMLDelegate()
        parser.delegate = delegate
        guard parser.parse(), let root = delegate.root else { throw ServerError.empty }
        return root
    }

    func libraries() async throws -> [PlexLibrary] {
        let root = try await fetch("/library/sections")
        return root.children(named: "Directory").compactMap { node in
            guard let key = node.attributes["key"], let title = node.attributes["title"] else { return nil }
            return PlexLibrary(id: key, title: title, type: node.attributes["type"] ?? "")
        }
    }

    func items(section: String, search: String? = nil) async throws -> [PlexItem] {
        var path = "/library/sections/\(section)/all"
        if let search, !search.isEmpty {
            path = "/library/sections/\(section)/search?query=\(search.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
        }
        let root = try await fetch(path)
        return root.children(named: "Video").map(\.asPlexItem)
            + root.children(named: "Directory").map(\.asPlexItem)
    }

    /// Continue watching, straight from the server's own view.
    func onDeck() async throws -> [PlexItem] {
        let root = try await fetch("/library/onDeck")
        return root.children(named: "Video").map(\.asPlexItem)
    }

    func recentlyAdded() async throws -> [PlexItem] {
        let root = try await fetch("/library/recentlyAdded")
        return root.children(named: "Video").map(\.asPlexItem)
    }

    /// Direct file URL, bypassing the transcoder so the kernel gets the original.
    func streamURL(for item: PlexItem) -> String? {
        guard let part = item.partPath else { return nil }
        var separator = "?"
        var url = base + part
        if url.contains("?") { separator = "&" }
        url += "\(separator)X-Plex-Token=\(token)"
        return url
    }

    func artworkURL(for item: PlexItem) -> String? {
        guard let thumb = item.thumbPath else { return nil }
        return base + thumb + "?X-Plex-Token=\(token)"
    }

    func mediaHeaders() -> [String: String] {
        ["X-Plex-Token": token]
    }
}

// MARK: - Minimal XML tree

/// Plex responses are shallow; a generic node tree is all that is needed, and it
/// keeps the parsing in one place instead of a delegate per endpoint.
final class PlexXMLNode {
    var name: String
    var attributes: [String: String]
    var children: [PlexXMLNode] = []

    init(name: String, attributes: [String: String]) {
        self.name = name
        self.attributes = attributes
    }

    func children(named name: String) -> [PlexXMLNode] {
        children.filter { $0.name == name }
    }

    var asPlexItem: PlexItem {
        let media = children(named: "Media").first
        let part = media?.children(named: "Part").first
        return PlexItem(
            id: attributes["ratingKey"] ?? attributes["key"] ?? UUID().uuidString,
            title: attributes["title"] ?? "未命名",
            year: attributes["year"].flatMap(Int.init),
            durationMs: Double(attributes["duration"] ?? "") ?? 0,
            thumbPath: attributes["thumb"] ?? media?.attributes["thumb"],
            partPath: part?.attributes["key"],
            partSize: Int(part?.attributes["size"] ?? "") ?? 0,
            viewOffsetMs: Double(attributes["viewOffset"] ?? "") ?? 0,
            isPlayed: (Int(attributes["viewCount"] ?? "") ?? 0) > 0
        )
    }
}

final class PlexXMLDelegate: NSObject, XMLParserDelegate {
    var root: PlexXMLNode?
    private var stack: [PlexXMLNode] = []

    func parser(_ parser: XMLParser, didStartElement elementName: String,
                namespaceURI: String?, qualifiedName: String?,
                attributes attributeDict: [String: String]) {
        let node = PlexXMLNode(name: elementName, attributes: attributeDict)
        if let parent = stack.last {
            parent.children.append(node)
        } else if root == nil {
            root = node
        }
        stack.append(node)
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String,
                namespaceURI: String?, qualifiedName: String?) {
        if !stack.isEmpty { stack.removeLast() }
    }
}
