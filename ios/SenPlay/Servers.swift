import Foundation

enum ServerError: LocalizedError {
    case badAddress
    case http(Int)
    /// Provider-supplied reason, when the service bothered to send one.
    case httpMessage(Int, String)
    case empty

    var errorDescription: String? {
        switch self {
        case .badAddress: return "服务器地址无效"
        case .http(let code): return "服务器返回 HTTP \(code)"
        case .httpMessage(let code, let message): return "\(message)（HTTP \(code)）"
        case .empty: return "服务器未返回内容"
        }
    }
}

enum Urls {
    static func join(_ base: String, _ path: String) -> String {
        if path.hasPrefix("http://") || path.hasPrefix("https://") { return path }
        let trimmed = base.hasSuffix("/") ? String(base.dropLast()) : base
        if path.isEmpty { return trimmed }
        let suffix = path.hasPrefix("/") ? path : "/" + path
        return trimmed + suffix
    }
}

/// Depth-1 PROPFIND listing, sufficient to browse and stream from WebDAV.
struct WebDAVClient {

    private static let body = """
    <?xml version="1.0" encoding="utf-8"?>
    <d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:getcontentlength/><d:resourcetype/></d:prop></d:propfind>
    """

    static func list(server: ServerConfig, path: String) async throws -> [MediaEntry] {
        let address = Urls.join(server.address, path)
        guard let url = URL(string: address) else { throw ServerError.badAddress }

        var request = URLRequest(url: url)
        request.httpMethod = "PROPFIND"
        request.setValue("1", forHTTPHeaderField: "Depth")
        request.setValue("application/xml", forHTTPHeaderField: "Content-Type")
        request.setValue(server.userAgentHeader, forHTTPHeaderField: "User-Agent")
        request.httpBody = body.data(using: .utf8)

        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw ServerError.http(http.statusCode)
        }

        let parser = MultiStatusParser(baseURL: address)
        let xml = XMLParser(data: data)
        xml.delegate = parser
        xml.parse()
        return parser.entries.sorted {
            if $0.isDirectory != $1.isDirectory { return $0.isDirectory }
            return $0.name.localizedStandardCompare($1.name) == .orderedAscending
        }
    }
}

final class MultiStatusParser: NSObject, XMLParserDelegate {
    private let baseURL: String
    private var href: String?
    private var displayName: String?
    private var contentLength = 0
    private var isCollection = false
    private var inResponse = false
    private var currentElement = ""

    private(set) var entries: [MediaEntry] = []

    init(baseURL: String) {
        self.baseURL = baseURL
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?,
                qualifiedName qName: String?, attributes attributeDict: [String: String]) {
        let name = elementName.contains(":") ? String(elementName.split(separator: ":").last!) : elementName
        currentElement = name
        switch name {
        case "response":
            inResponse = true
            href = nil
            displayName = nil
            contentLength = 0
            isCollection = false
        case "collection":
            isCollection = true
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        guard inResponse else { return }
        let trimmed = string.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        switch currentElement {
        case "href": href = (href ?? "") + trimmed
        case "displayname": displayName = (displayName ?? "") + trimmed
        case "getcontentlength": contentLength = Int(trimmed) ?? 0
        default: break
        }
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?,
                qualifiedName qName: String?) {
        let name = elementName.contains(":") ? String(elementName.split(separator: ":").last!) : elementName
        if name == "response" {
            inResponse = false
            if let raw = href {
                let decoded = raw.removingPercentEncoding ?? raw
                let isSelf = decoded.trimmingCharacters(in: CharacterSet(charactersIn: "/")) ==
                    baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                if !isSelf {
                    let label: String
                    if let displayName, !displayName.isEmpty {
                        label = displayName
                    } else {
                        label = decoded.trimmingCharacters(in: CharacterSet(charactersIn: "/")).components(separatedBy: "/").last ?? decoded
                    }
                    let absolute: String
                    if decoded.hasPrefix("http") {
                        absolute = decoded
                    } else if let base = URL(string: baseURL), let resolved = URL(string: decoded, relativeTo: base) {
                        absolute = resolved.absoluteString
                    } else {
                        absolute = Urls.join(baseURL, decoded)
                    }
                    entries.append(MediaEntry(name: label, url: absolute, isDirectory: isCollection, size: contentLength))
                }
            }
        }
    }
}

/// Emby / Jellyfin / 飞牛 compatible client (X-Emby-Authorization + /Items).
final class EmbyClient {
    private let server: ServerConfig
    private let clientId = "senplay-ios"

    private(set) var accessToken = ""
    private(set) var userId = ""

    init(server: ServerConfig) {
        self.server = server
    }

    private var authHeader: String {
        var value = "MediaBrowser Client=\"SenPlay\", Device=\"iOS\", DeviceId=\"\(clientId)\", Version=\"1.0.0\""
        if !accessToken.isEmpty {
            value += ", Token=\"\(accessToken)\""
        }
        return value
    }

    @discardableResult
    func authenticate() async throws -> Bool {
        var request = URLRequest(url: URL(string: Urls.join(server.address, "/Users/AuthenticateByName"))!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(authHeader, forHTTPHeaderField: "X-Emby-Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "Username": server.username,
            "Pw": server.password
        ])

        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw ServerError.http(http.statusCode)
        }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ServerError.empty
        }
        accessToken = json["AccessToken"] as? String ?? ""
        userId = (json["User"] as? [String: Any])?["Id"] as? String ?? ""
        return !accessToken.isEmpty
    }

    func views() async throws -> [MediaEntry] {
        try await items(parentId: nil, includeTypes: "CollectionFolder,Folder,UserView")
    }

    enum SortBy: String, CaseIterable, Identifiable {
        case name = "SortName"
        case dateAdded = "DateCreated"
        case year = "ProductionYear"
        case rating = "CommunityRating"
        case runtime = "Runtime"
        var id: String { rawValue }
        var title: String {
            switch self {
            case .name: return "名称"
            case .dateAdded: return "加入时间"
            case .year: return "年份"
            case .rating: return "评分"
            case .runtime: return "时长"
            }
        }
    }

    func items(
        parentId: String?,
        includeTypes: String = "Folder,Movie,Episode,Series,Video",
        sortBy: SortBy = .name,
        ascending: Bool = true,
        recursive: Bool = false,
        searchTerm: String? = nil,
        filters: [String] = [],
        limit: Int? = nil
    ) async throws -> [MediaEntry] {
        var components = URLComponents(string: Urls.join(server.address, "/Users/\(userId)/Items"))!
        var query: [URLQueryItem] = [
            URLQueryItem(name: "IncludeItemTypes", value: includeTypes),
            URLQueryItem(name: "Recursive", value: recursive ? "true" : "false"),
            URLQueryItem(name: "SortBy", value: sortBy.rawValue),
            URLQueryItem(name: "SortOrder", value: ascending ? "Ascending" : "Descending"),
            URLQueryItem(name: "Fields", value: "Path,MediaSources,RunTimeTicks,Overview,ProductionYear,CommunityRating,UserData,DateModified,People"),
            URLQueryItem(name: "ImageTypeLimit", value: "1"),
            URLQueryItem(name: "EnableImageTypes", value: "Primary,Backdrop,Thumb"),
            URLQueryItem(name: "api_key", value: accessToken)
        ]
        if let parentId { query.append(URLQueryItem(name: "ParentId", value: parentId)) }
        if let searchTerm, !searchTerm.isEmpty {
            query.append(URLQueryItem(name: "SearchTerm", value: searchTerm))
        }
        if !filters.isEmpty {
            query.append(URLQueryItem(name: "Filters", value: filters.joined(separator: ",")))
        }
        if let limit { query.append(URLQueryItem(name: "Limit", value: String(limit))) }
        components.queryItems = query

        var request = URLRequest(url: components.url!)
        request.setValue(authHeader, forHTTPHeaderField: "X-Emby-Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw ServerError.http(http.statusCode)
        }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rawItems = json["Items"] as? [[String: Any]] else { return [] }

        return rawItems.compactMap { Self.entry(from: $0, server: server, token: accessToken) }
    }

    /// "继续观看": items the server reports as partially played, newest first.
    /// Box sets: the server-side groupings that behave as collections.
    func collections(parentId: String? = nil) async throws -> [MediaEntry] {
        try await items(parentId: parentId, includeTypes: "BoxSet",
                        sortBy: .name, ascending: true, recursive: true)
    }

    func resume(limit: Int = 30) async throws -> [MediaEntry] {
        var components = URLComponents(string: Urls.join(server.address, "/Users/\(userId)/Items/Resume"))!
        components.queryItems = [
            URLQueryItem(name: "Recursive", value: "true"),
            URLQueryItem(name: "MediaTypes", value: "Video"),
            URLQueryItem(name: "Limit", value: String(limit)),
            URLQueryItem(name: "Fields", value: "Path,MediaSources,RunTimeTicks,UserData,DateModified"),
            URLQueryItem(name: "api_key", value: accessToken)
        ]

        var request = URLRequest(url: components.url!)
        request.setValue(authHeader, forHTTPHeaderField: "X-Emby-Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw ServerError.http(http.statusCode)
        }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rawItems = json["Items"] as? [[String: Any]] else { return [] }

        return rawItems.compactMap { Self.entry(from: $0, server: server, token: accessToken) }
    }

    /// Recently added, for the "最新" strip.
    func latest(limit: Int = 30) async throws -> [MediaEntry] {
        try await items(parentId: nil, includeTypes: "Movie,Episode,Video",
                        sortBy: .dateAdded, ascending: false, recursive: true, limit: limit)
    }

    /// Emby renders timestamps as ISO-8601 without a timezone suffix; the formatter
    /// is built with a fixed locale so it does not depend on the device's region.
    static func date(_ raw: Any?) -> Date? {
        guard let text = raw as? String, !text.isEmpty else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: text) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: text)
    }

    /// Emby lists everyone credited; the app only has room for a few names, and the
    /// order it returns is the billing order.
    static func cast(from item: [String: Any], limit: Int = 4) -> [String] {
        let people = item["People"] as? [[String: Any]] ?? []
        return people
            .filter { ($0["Type"] as? String) == "Actor" }
            .compactMap { $0["Name"] as? String }
            .prefix(limit)
            .map { $0 }
    }

    static func entry(from item: [String: Any], server: ServerConfig, token: String) -> MediaEntry? {
        guard let id = item["Id"] as? String else { return nil }
        let type = item["Type"] as? String ?? ""
        let folderLike = ["Folder", "Series", "CollectionFolder", "UserView", "Season", "BoxSet"].contains(type)
        let userData = item["UserData"] as? [String: Any] ?? [:]
        let played = userData["Played"] as? Bool ?? false
        let positionTicks = userData["PlaybackPositionTicks"] as? Double ?? 0
        let runTicks = item["RunTimeTicks"] as? Double ?? 0
        return MediaEntry(
            name: item["Name"] as? String ?? id,
            url: id,
            isDirectory: folderLike,
            size: 0,
            unplayedCount: userData["UnplayedItemCount"] as? Int ?? 0,
            modifiedAt: EmbyClient.date(item["DateModified"]),
            episodeNumber: item["IndexNumber"] as? Int ?? 0,
            cast: EmbyClient.cast(from: item),
            entryId: id,
            imageURL: Urls.join(server.address, "/Items/\(id)/Images/Primary?maxHeight=400&api_key=\(token)"),
            isPlayed: played,
            positionMs: positionTicks / 10_000,
            durationMs: runTicks / 10_000,
            overview: item["Overview"] as? String,
            year: item["ProductionYear"] as? Int,
            rating: item["CommunityRating"] as? Double
        )
    }

    func streamURL(for entry: MediaEntry) -> String {
        Urls.join(server.address, "/Videos/\(entry.entryId ?? "")/stream?static=true&api_key=\(accessToken)")
    }

    /// Headers the player needs so the server authorises the media request.
    func mediaHeaders() -> [String: String] {
        ["X-Emby-Authorization": authHeader]
    }
}

extension ServerConfig {
    var userAgentHeader: String {
        "SenPlay/1.0 (iOS)"
    }
}
