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
    /// Directory child count, when the source reports it. Emby and WebDAV do; a
    /// plain file listing does not, and zero means unknown rather than empty.
    let childCount: Int
    /// Episodes not yet watched, from Emby's UserData. This is what "剩余播放数量"
    /// means — a count of items, not a duration.
    let unplayedCount: Int
    /// Last modification, when the source reports it. The list's "文件夹项目显示时间".
    let modifiedAt: Date?
    let entryId: String?
    let imageURL: String?
    /// Server-reported watch state, used by the poster wall and the resume strip.
    let isPlayed: Bool
    let positionMs: Double
    let durationMs: Double
    let overview: String?
    let year: Int?
    let rating: Double?

    init(
        name: String,
        url: String,
        isDirectory: Bool,
        size: Int = 0,
        childCount: Int = 0,
        unplayedCount: Int = 0,
        modifiedAt: Date? = nil,
        entryId: String? = nil,
        imageURL: String? = nil,
        isPlayed: Bool = false,
        positionMs: Double = 0,
        durationMs: Double = 0,
        overview: String? = nil,
        year: Int? = nil,
        rating: Double? = nil
    ) {
        self.id = entryId ?? url + name
        self.name = name
        self.url = url
        self.isDirectory = isDirectory
        self.size = size
        self.childCount = childCount
        self.unplayedCount = unplayedCount
        self.modifiedAt = modifiedAt
        self.entryId = entryId
        self.imageURL = imageURL
        self.isPlayed = isPlayed
        self.positionMs = positionMs
        self.durationMs = durationMs
        self.overview = overview
        self.year = year
        self.rating = rating
    }

    /// Zero when there is nothing watched yet, otherwise the fraction played.
    var progress: Double {
        durationMs > 0 ? min(max(positionMs / durationMs, 0), 1) : 0
    }

    var subtitle: String {
        var parts: [String] = []
        if let year { parts.append(String(year)) }
        if let rating, rating > 0 { parts.append(String(format: "%.1f", rating)) }
        if size > 0 { parts.append("\(size / 1024 / 1024) MB") }
        if durationMs > 0 { parts.append(Self.timecode(durationMs)) }
        return parts.joined(separator: " · ")
    }

    static func timecode(_ ms: Double) -> String {
        guard ms.isFinite, ms > 0 else { return "" }
        let total = Int(ms / 1000)
        let h = total / 3600, m = (total % 3600) / 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, total % 60) : String(format: "%d 分 %02d 秒", m, total % 60)
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
    /// Extra request headers the server needs to authorise the media request.
    var headers: [String: String] = [:]
    var positionMs: Double = 0
    var saveToUrlList: Bool = false
    /// Siblings of the current item, so the player can advance without the host
    /// view having to rebuild the list.
    var queue: [QueueItem] = []
}
