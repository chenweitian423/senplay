import Foundation

/// Persistence for servers, the URL list, playback history and preferences.
/// UserDefaults + Codable keeps the project free of any third-party dependency.
final class LibraryStore: ObservableObject {
    static let shared = LibraryStore()

    static let defaultUserAgent = "SenPlay/1.0 (iOS)"

    @Published private(set) var servers: [ServerConfig] = []
    @Published private(set) var urlList: [UrlListItem] = []
    @Published private(set) var history: [HistoryEntry] = []

    @Published var rememberProgress: Bool {
        didSet { defaults.set(rememberProgress, forKey: Keys.remember) }
    }

    @Published var defaultUserAgent: String {
        didSet { defaults.set(defaultUserAgent, forKey: Keys.userAgent) }
    }

    private var trackMemory: [String: [String: String]] = [:]

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let servers = "servers.v1"
        static let urls = "urls.v1"
        static let history = "history.v1"
        static let remember = "remember_progress"
        static let userAgent = "default_user_agent"
        static let tracks = "trackMemory.v1"
    }

    private init() {
        rememberProgress = defaults.object(forKey: Keys.remember) as? Bool ?? true
        defaultUserAgent = defaults.string(forKey: Keys.userAgent) ?? LibraryStore.defaultUserAgent
        servers = LibraryStore.decode([ServerConfig].self, from: defaults.data(forKey: Keys.servers)) ?? []
        urlList = LibraryStore.decode([UrlListItem].self, from: defaults.data(forKey: Keys.urls)) ?? []
        history = (LibraryStore.decode([HistoryEntry].self, from: defaults.data(forKey: Keys.history)) ?? [])
            .sorted { $0.updatedAt > $1.updatedAt }
        trackMemory = LibraryStore.decode([String: [String: String]].self,
                                          from: defaults.data(forKey: Keys.tracks)) ?? [:]
    }

    // MARK: - servers

    func save(_ server: ServerConfig) {
        servers.removeAll { $0.id == server.id }
        servers.append(server)
        defaults.set(LibraryStore.encode(servers), forKey: Keys.servers)
    }

    func remove(server: ServerConfig) {
        servers.removeAll { $0.id == server.id }
        defaults.set(LibraryStore.encode(servers), forKey: Keys.servers)
    }

    // MARK: - url list

    func add(url item: UrlListItem) {
        urlList.removeAll { $0.url == item.url }
        urlList.append(item)
        defaults.set(LibraryStore.encode(urlList), forKey: Keys.urls)
    }

    func remove(url: String) {
        urlList.removeAll { $0.url == url }
        defaults.set(LibraryStore.encode(urlList), forKey: Keys.urls)
    }

    // MARK: - history

    func rememberProgress(url: String, name: String, positionMs: Double, durationMs: Double,
                          imageURL: String? = nil) {
        guard rememberProgress else { return }
        // The artwork is sticky: a later visit that arrives carrying none — play resumed
        // from the history list or the home shelf, say — keeps whatever the browser
        // recorded rather than blanking the tile it already drew.
        let kept = imageURL ?? history.first { $0.url == url }?.imageURL
        history.removeAll { $0.url == url }
        history.insert(HistoryEntry(url: url, name: name, positionMs: positionMs, durationMs: durationMs, updatedAt: Date(), imageURL: kept), at: 0)
        history = Array(history.prefix(300))
        defaults.set(LibraryStore.encode(history), forKey: Keys.history)
    }

    func savedPosition(url: String) -> Double {
        rememberProgress ? (history.first { $0.url == url }?.positionMs ?? 0) : 0
    }

    // MARK: - track memory

    /// "记住所选字幕/音轨": which track was picked for a given file, so the next visit
    /// starts on it. One dictionary rather than a key per file — the file is a URL,
    /// and a URL makes a poor storage key.
    func rememberedTrack(url: String, kind: String) -> String? {
        trackMemory[url]?[kind]
    }

    func rememberTrack(url: String, kind: String, value: String) {
        var entry = trackMemory[url] ?? [:]
        entry[kind] = value
        trackMemory[url] = entry
        defaults.set(LibraryStore.encode(trackMemory), forKey: Keys.tracks)
    }

    func removeHistory(url: String) {
        history.removeAll { $0.url == url }
        defaults.set(LibraryStore.encode(history), forKey: Keys.history)
    }

    func clearHistory() {
        history = []
        defaults.set(LibraryStore.encode(history), forKey: Keys.history)
    }

    // MARK: - coding helpers

    private static func encode<T: Encodable>(_ value: T) -> Data? {
        try? JSONEncoder().encode(value)
    }

    private static func decode<T: Decodable>(_ type: T.Type, from data: Data?) -> T? {
        guard let data else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
}
