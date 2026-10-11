import Foundation

struct ServerConfig: Codable, Identifiable, Hashable {
    var id: String = UUID().uuidString
    var type: String = "webdav"
    var name: String = ""
    var address: String = ""
    var username: String = ""
    var password: String = ""
    var note: String = ""

    var isEmbyLike: Bool { type == "emby" || type == "jellyfin" || type == "feiniu" }
}

struct MediaEntry: Identifiable, Hashable {
    let id: String
    let name: String
    let url: String
    let isDirectory: Bool
    let size: Int
    let entryId: String?
    let imageURL: String?

    init(name: String, url: String, isDirectory: Bool, size: Int = 0, entryId: String? = nil, imageURL: String? = nil) {
        self.id = entryId ?? url + name
        self.name = name
        self.url = url
        self.isDirectory = isDirectory
        self.size = size
        self.entryId = entryId
        self.imageURL = imageURL
    }
}

struct UrlListItem: Codable, Hashable, Identifiable {
    var url: String
    var name: String
    var userAgent: String?
    var id: String { url }
}

struct HistoryEntry: Codable, Hashable, Identifiable {
    var url: String
    var name: String
    var positionMs: Double
    var durationMs: Double
    var updatedAt: Date
    var id: String { url }

    var progress: Double {
        durationMs > 0 ? min(max(positionMs / durationMs, 0), 1) : 0
    }
}

/// One entry of the surrounding folder, used to continue playback automatically.
struct QueueItem: Equatable, Hashable {
    var url: String
    var name: String
}

struct PlayRequest: Equatable {
    var url: String
    var name: String
    var subtitleURL: String? = nil
    var userAgent: String? = nil
    var positionMs: Double = 0
    var saveToUrlList: Bool = false
    /// Siblings of the current item, so the player can advance without the host
    /// view having to rebuild the list.
    var queue: [QueueItem] = []
}
