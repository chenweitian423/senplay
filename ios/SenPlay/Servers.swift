import Foundation

enum ServerError: LocalizedError {
    case badAddress
    case http(Int)
    case empty

    var errorDescription: String? {
        switch self {
        case .badAddress: return "服务器地址无效"
        case .http(let code): return "服务器返回 HTTP \(code)"
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

    func items(parentId: String?, includeTypes: String = "Folder,Movie,Episode,Series,Video") async throws -> [MediaEntry] {
        var components = URLComponents(string: Urls.join(server.address, "/Users/\(userId)/Items"))!
        var items: [URLQueryItem] = [
            URLQueryItem(name: "IncludeItemTypes", value: includeTypes),
            URLQueryItem(name: "Recursive", value: "false"),
            URLQueryItem(name: "SortBy", value: "SortName"),
            URLQueryItem(name: "Fields", value: "Path,MediaSources,RunTimeTicks"),
            URLQueryItem(name: "api_key", value: accessToken)
        ]
        if let parentId { items.append(URLQueryItem(name: "ParentId", value: parentId)) }
        components.queryItems = items

        var request = URLRequest(url: components.url!)
        request.setValue(authHeader, forHTTPHeaderField: "X-Emby-Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw ServerError.http(http.statusCode)
        }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rawItems = json["Items"] as? [[String: Any]] else { return [] }

        return rawItems.compactMap { item in
            guard let id = item["Id"] as? String else { return nil }
            let type = item["Type"] as? String ?? ""
            let folderLike = ["Folder", "Series", "CollectionFolder", "UserView", "Season"].contains(type)
            return MediaEntry(
                name: item["Name"] as? String ?? id,
                url: id,
                isDirectory: folderLike,
                size: 0,
                entryId: id,
                imageURL: Urls.join(server.address, "/Items/\(id)/Images/Primary?maxHeight=320&api_key=\(accessToken)")
            )
        }
    }

    func streamURL(for entry: MediaEntry) -> String {
        Urls.join(server.address, "/Videos/\(entry.entryId ?? "")/stream?static=true&api_key=\(accessToken)")
    }
}

extension ServerConfig {
    var userAgentHeader: String {
        "SenPlay/1.0 (iOS)"
    }
}
