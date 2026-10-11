import CoreMedia
import SwiftUI

/// Server browser for Emby-like servers and WebDAV.
///
/// For Emby-like servers it offers the three strips the reference app exposes —
/// files, continue watching and recently added — plus search and sorting. WebDAV
/// stays a plain folder walk because the protocol has nothing more to give.
/// Which of the list-detail toggles are on. Read once when the browser opens; the
/// row body uses them to decide what to draw rather than laying out everything and
/// hiding it.
private struct ListDisplay {
    var showSize = true
    var showDuration = true
    var showProgress = true
    var showFolderTime = false
    /// Title bar only, and off by default: the reference app's "标题栏显示文件数量".
    var showTitleCount = false
    /// Item counts on the artwork, from the library group.
    var showItemCount = true
    /// "显示剩余播放数量" — a count of remaining items, not a duration.
    var showRemainingCount = true
    /// "显示选集指示器"
    var showEpisodeIndicator = true
    /// "显示演职人员"
    var showCast = true
    var rounded = true
    /// "中间" / "左边" / "右边" — this is a choice, not a switch.
    var ellipsisMode = "middle"
    var fullNameOnMenu = false
    // Poster marks. These are the ones the reference app puts on the artwork rather
    // than in the list row.
    var showPlayedMark = true
    var showRating = true
    var showMediaInfo = true
    var titleCentered = false
    var showContinue = true
    var showCollection = false

    init(_ defaults: UserDefaults = .standard) {
        showSize = defaults.object(forKey: "pref.list.showSize") as? Bool ?? true
        showDuration = defaults.object(forKey: "pref.list.showDuration") as? Bool ?? true
        showProgress = defaults.object(forKey: "pref.list.showProgress") as? Bool ?? true
        showFolderTime = defaults.object(forKey: "pref.list.showFolderTime") as? Bool ?? false
        showTitleCount = defaults.object(forKey: "pref.list.showCount") as? Bool ?? false
        showItemCount = defaults.object(forKey: "pref.library.showCount") as? Bool ?? true
        showRemainingCount = defaults.object(forKey: "pref.library.showRemaining") as? Bool ?? true
        showEpisodeIndicator =
            defaults.object(forKey: "pref.library.showEpisodeIndicator") as? Bool ?? true
        showCast = defaults.object(forKey: "pref.library.showCast") as? Bool ?? true
        rounded = defaults.object(forKey: "pref.list.rounded") as? Bool ?? true
        ellipsisMode = defaults.string(forKey: "pref.list.ellipsis") ?? "middle"
        fullNameOnMenu = defaults.object(forKey: "pref.list.fullNameOnMenu") as? Bool ?? false
        showPlayedMark = defaults.object(forKey: "pref.library.showPlayedMark") as? Bool ?? true
        showRating = defaults.object(forKey: "pref.library.showRating") as? Bool ?? true
        showMediaInfo = defaults.object(forKey: "pref.library.showMediaInfo") as? Bool ?? true
        titleCentered = (defaults.string(forKey: "pref.library.titleAlignment") ?? "left")
            == "center"
        showContinue = defaults.object(forKey: "pref.library.showContinue") as? Bool ?? true
        showCollection = defaults.object(forKey: "pref.library.showCollection") as? Bool ?? false
    }
}

struct ServerBrowserView: View {
    let server: ServerConfig
    @Binding var pending: PlayRequest?

    private enum Strip: String, CaseIterable, Identifiable {
        case files, resume, latest, collections
        var id: String { rawValue }
        /// The strips whose section the viewer has not switched off. `files` and
        /// `latest` have no switch in the reference app's settings and are always
        /// available.
        static func visible(_ display: ListDisplay) -> [Strip] {
            allCases.filter { strip in
                switch strip {
                case .resume: return display.showContinue
                case .collections: return display.showCollection
                case .files, .latest: return true
                }
            }
        }

        var title: String {
            switch self {
            case .files: return "文件"
            case .resume: return "继续观看"
            case .latest: return "最近添加"
            case .collections: return "合集"
            }
        }

        /// A collection has no resume position or added date, so those strips have
        /// nothing to show inside one.
        var supportsNestedNavigation: Bool { self == .files || self == .collections }
    }

    private enum Layout: String {
        case list, grid
    }

    @State private var entries: [MediaEntry] = []
    private let display = ListDisplay()

    /// A section switch flipped in settings while this screen was open leaves the
    /// selection pointing at a strip that is no longer offered.
    private func fallBackIfStripHidden() {
        let available = Strip.visible(display)
        if !available.contains(strip), let first = available.first {
            strip = first
        }
    }
    @State private var path = ""
    @State private var parentId: String? = nil
    @State private var stack: [(String, String?)] = []
    @State private var emby: EmbyClient?
    @State private var loading = false
    @State private var errorText: String?

    @State private var strip: Strip = .files
    @State private var layout: Layout = .grid
    @State private var sortBy: EmbyClient.SortBy = .name
    @State private var ascending = true
    @State private var search = ""
    @State private var ftp: FTPClient?
    @State private var plex: PlexClient?
    @State private var plexLibraries: [PlexLibrary] = []
    @State private var s3: S3Client?
    /// Inside a collection: the box set's id, or nil while listing them.
    @State private var collectionId: String? = nil
    @State private var collectionParent: String? = nil
    @State private var bridge: FTPMediaBridge?
    @State private var ftpSize: UInt64 = 0

    /// Both Emby-family servers and Plex expose server-side strips.
    private var hasStrips: Bool { isEmbyLike || server.type == "plex" }

    private var isEmbyLike: Bool { server.isEmbyLike }

    private let columns = [GridItem(.adaptive(minimum: 118), spacing: 12)]

    var body: some View {
        content
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarContent }
            .searchable(text: $search, prompt: "搜索")
            .onChange(of: strip) { _ in handleStripChange() }
            .onChange(of: sortBy) { _ in scheduleReload() }
            .onChange(of: ascending) { _ in scheduleReload() }
            .task {
                fallBackIfStripHidden()
                await reload()
            }
    }

    /// Split out of `body` on purpose: keeping this chain inline made the type
    /// checker give up on the whole expression.
    @ViewBuilder
    private var content: some View {
        if loading && entries.isEmpty {
            ProgressView("载入中…")
        } else {
            ScrollView {
                if let errorText {
                    Text("错误：\(errorText)")
                        .font(.footnote)
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                }
                if entries.isEmpty && !loading {
                    Text(isEmbyLike ? "这个位置没有内容。" : "空目录。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                }
                if layout == .grid {
                    LazyVGrid(columns: columns, spacing: 14) {
                        ForEach(visibleEntries) { entry in
                            posterCell(entry)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                } else {
                    LazyVStack(spacing: 0) {
                        ForEach(visibleEntries) { entry in
                            rowCell(entry)
                            Divider().padding(.leading, 16)
                        }
                    }
                }
            }
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItemGroup(placement: .navigationBarTrailing) {
            if hasStrips {
                Menu {
                    Picker("板块", selection: $strip) {
                        ForEach(Strip.visible(display)) { Text($0.title).tag($0) }
                    }
                    if isEmbyLike {
                        Divider()
                        Picker("排序", selection: $sortBy) {
                            ForEach(EmbyClient.SortBy.allCases) { Text($0.title).tag($0) }
                        }
                        Toggle("升序", isOn: $ascending)
                    }
                } label: {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                }
            }
            Button {
                layout = layout == .grid ? .list : .grid
            } label: {
                Image(systemName: layout == .grid ? "list.bullet" : "square.grid.2x2")
            }
        }
        ToolbarItem(placement: .navigationBarLeading) {
            if collectionId != nil {
                Button("< 合集") { closeCollection() }
            } else if server.type == "plex", !plexLibraries.isEmpty, strip == .files {
                Menu {
                    ForEach(plexLibraries) { library in
                        Button(library.title) {
                            path = library.id
                            scheduleReload()
                        }
                    }
                } label: {
                    Text(plexLibraries.first(where: { $0.id == path })?.title ?? "媒体库")
                }
            } else if !stack.isEmpty {
                Button("< 上一级") { goUp() }
            }
        }
    }

    private func handleStripChange() {
        guard hasStrips else { return }
        stack = []
        path = ""
        parentId = nil
        collectionId = nil
        scheduleReload()
    }

    private func closeCollection() {
        collectionId = nil
        scheduleReload()
    }

    private func scheduleReload() {
        Task { await reload() }
    }

    private var title: String {
        let base = server.name.isEmpty ? server.address : server.name
        guard display.showTitleCount else { return base }
        return "\(base)（\(visibleEntries.count)）"
    }

    private var visibleEntries: [MediaEntry] {
        let query = search.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return entries }
        return entries.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }

    // MARK: cells

    private func posterCell(_ entry: MediaEntry) -> some View {
        Button {
            open(entry)
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                ZStack(alignment: .bottomLeading) {
                    posterImage(entry)
                    if display.showProgress, entry.progress > 0 {
                        progressBar(entry.progress)
                    }
                    if display.showPlayedMark, entry.isPlayed {
                        Text("已看完")
                            .font(.system(size: 9, weight: .semibold))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.black.opacity(0.6))
                            .foregroundColor(.white)
                            .cornerRadius(4)
                            .padding(5)
                            .frame(maxWidth: .infinity, maxHeight: .infinity,
                                   alignment: .topTrailing)
                    }
                }
                Text(entry.name)
                    .font(.caption)
                    .lineLimit(2)
                    .foregroundColor(.primary)
                    .multilineTextAlignment(display.titleCentered ? .center : .leading)
                    .frame(maxWidth: .infinity,
                           alignment: display.titleCentered ? .center : .leading)
                if let mark = posterMark(entry) {
                    Text(mark)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                if !entry.subtitle.isEmpty {
                    Text(entry.subtitle)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
        }
        .buttonStyle(.plain)
    }

    /// Rating and remaining time, in the order the reference app shows them. Only
    /// what the entry carries: a folder has neither, and a server that reports no
    /// rating produces no line rather than a zero.
    private func posterMark(_ entry: MediaEntry) -> String? {
        var parts: [String] = []
        if display.showRating, let rating = entry.rating, rating > 0 {
            parts.append(String(format: "★ %.1f", rating))
        }
        if display.showRemainingCount, entry.unplayedCount > 0 {
            parts.append("剩余 \(entry.unplayedCount) 集")
        }
        if display.showFolderTime, entry.isDirectory, let modified = entry.modifiedAt {
            parts.append(Self.shortDate(modified))
        }
        if display.showMediaInfo, !entry.isDirectory, entry.size > 0 {
            parts.append(ByteCountFormatter.string(fromByteCount: Int64(entry.size),
                                                   countStyle: .file))
        }
        if display.showCast, !entry.cast.isEmpty {
            parts.append(entry.cast.joined(separator: " / "))
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private func progressBar(_ fraction: Double) -> some View {
        GeometryReader { geometry in
            VStack {
                Spacer(minLength: 0)
                Rectangle()
                    .fill(Color.accentColor)
                    .frame(width: geometry.size.width * fraction, height: 3)
            }
        }
    }

    private func posterImage(_ entry: MediaEntry) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.18))
            if let imageURL = entry.imageURL, let url = URL(string: imageURL) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().aspectRatio(contentMode: .fill)
                    case .failure:
                        placeholderIcon(entry)
                    default:
                        ProgressView()
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                placeholderIcon(entry)
            }
        }
        .frame(height: 168)
        .frame(maxWidth: .infinity)
    }

    private func placeholderIcon(_ entry: MediaEntry) -> some View {
        Image(systemName: entry.isDirectory ? "folder" : "film")
            .font(.system(size: 26))
            .foregroundColor(.secondary)
    }

    private func rowCell(_ entry: MediaEntry) -> some View {
        Button {
            open(entry)
        } label: {
            HStack(spacing: 12) {
                if let imageURL = entry.imageURL, let url = URL(string: imageURL) {
                    AsyncImage(url: url) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        placeholderIcon(entry)
                    }
                    .frame(width: 46, height: 66)
                    .clipShape(RoundedRectangle(cornerRadius: display.rounded ? 5 : 0))
                } else {
                    Image(systemName: entry.isDirectory ? "folder" : "doc")
                        .foregroundColor(.accentColor)
                        .frame(width: 24)
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(entry.name).lineLimit(display.ellipsisMode == "none" ? 2 : 1)
                    if !entry.subtitle.isEmpty {
                        Text(entry.subtitle).font(.caption2).foregroundColor(.secondary)
                    }
                    if let detail = rowDetail(entry), !detail.isEmpty {
                        Text(detail).font(.caption2).foregroundColor(.secondary)
                    }
                    if display.showProgress, entry.durationMs > 0, entry.positionMs > 0 {
                        ProgressView(value: min(entry.positionMs / entry.durationMs, 1))
                            .progressViewStyle(.linear)
                            .frame(width: 90)
                    }
                    if entry.isPlayed {
                        Text("已看完").font(.caption2).foregroundColor(.secondary)
                    }
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// The secondary line under a row's title, assembled from whatever this entry
    /// actually has. Directories report a child count; files report size and
    /// duration. Nothing is shown that the source did not supply.
    private func rowDetail(_ entry: MediaEntry) -> String? {
        var parts: [String] = []
        if entry.isDirectory {
            if display.showItemCount, entry.childCount > 0 {
                parts.append("\(entry.childCount) 项")
            }
        } else {
            if display.showSize, entry.size > 0 {
                parts.append(ByteCountFormatter.string(fromByteCount: Int64(entry.size),
                                                       countStyle: .file))
            }
            if display.showEpisodeIndicator, entry.episodeNumber > 0 {
                parts.append("第 \(entry.episodeNumber) 集")
            }
            if display.showDuration, entry.durationMs > 0 {
                parts.append(Self.clock(entry.durationMs / 1000))
            }
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private static let shortDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static func shortDate(_ date: Date) -> String {
        shortDateFormatter.string(from: date)
    }

    private static func clock(_ seconds: Double) -> String {
        let total = Int(seconds.rounded())
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, total % 60)
            : String(format: "%d:%02d", minutes, total % 60)
    }

    // MARK: data

    private func goUp() {
        guard let previous = stack.popLast() else { return }
        path = previous.0
        parentId = previous.1
        Task { await reload() }
    }

    private func plexEntry(_ item: PlexItem, client: PlexClient) -> MediaEntry {
        MediaEntry(
            name: item.title,
            url: client.streamURL(for: item) ?? "",
            isDirectory: false,
            size: item.partSize,
            entryId: item.id,
            imageURL: client.artworkURL(for: item),
            isPlayed: item.isPlayed,
            positionMs: item.viewOffsetMs,
            durationMs: item.durationMs,
            year: item.year
        )
    }

    private func open(_ entry: MediaEntry) {
        if strip == .collections, collectionId == nil, entry.isDirectory {
            collectionId = entry.entryId ?? entry.url
            scheduleReload()
            return
        }
        guard isEmbyLike, strip == .files else {
            if !isEmbyLike, entry.isDirectory {
                stack.append((path, parentId))
                path = entry.url
                Task { await reload() }
            } else {
                play(entry)
            }
            return
        }
        if entry.isDirectory {
            stack.append((path, parentId))
            parentId = entry.entryId
            Task { await reload() }
        } else {
            play(entry)
        }
    }

    /// FTP has no URL the kernel can fetch, so the file is published on loopback
    /// with range support and the player is handed that address instead.
    private func playOverFTP(_ entry: MediaEntry) async {
        guard let client = ftp else { return }
        do {
            let size = UInt64(try await client.size(entry.url))
            let mediaBridge = FTPMediaBridge()
            let port = try mediaBridge.start(client: client, path: entry.url, size: size,
                                             mime: Self.mime(for: entry.name))
            bridge = mediaBridge
            let local = "http://127.0.0.1:\(port)/stream"
            await MainActor.run {
                pending = PlayRequest(url: local, name: entry.name,
                                      queue: siblings)
            }
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func play(_ entry: MediaEntry) {
        if server.type == "s3" {
            // The kernel fetches without our headers, so the signature goes into the
            // URL rather than an Authorization header.
            guard let client = s3 else { return }
            let object = S3Object(key: entry.url, size: Int64(entry.size),
                                  isDirectory: false, modified: nil)
            guard let url = client.presignedURL(for: object) else {
                errorText = "无法生成 S3 下载地址"
                return
            }
            pending = PlayRequest(url: url, name: entry.name, queue: siblings)
            return
        }
        if server.type == "plex" {
            pending = PlayRequest(
                url: entry.url,
                name: entry.name,
                headers: plex?.mediaHeaders() ?? [:],
                positionMs: entry.positionMs,
                queue: siblings
            )
            return
        }
        if server.type == "ftp" {
            Task { await playOverFTP(entry) }
            return
        }
        if isEmbyLike {
            let client = emby ?? EmbyClient(server: server)
            pending = PlayRequest(
                url: client.streamURL(for: entry),
                name: entry.name,
                subtitleURL: matchingSubtitle(for: entry, emby: client),
                headers: client.mediaHeaders(),
                positionMs: entry.positionMs,
                queue: siblings
            )
        } else {
            pending = PlayRequest(
                url: entry.url,
                name: entry.name,
                subtitleURL: matchingSubtitle(for: entry, emby: nil),
                positionMs: LibraryStore.shared.savedPosition(url: entry.url),
                queue: siblings
            )
        }
    }

    /// Sidecar subtitles sharing the media file's stem, in the order a viewer would
    /// prefer them: Simplified Chinese first, then anything else, then only then a
    /// plain `.srt`.
    private func matchingSubtitle(for entry: MediaEntry, emby: EmbyClient?) -> String? {
        let subtitleExtensions: Set<String> = ["srt", "ass", "ssa", "vtt", "sub"]
        let stem = (entry.name as NSString).deletingPathExtension.lowercased()

        let candidates = entries.filter { candidate in
            guard !candidate.isDirectory else { return false }
            let ext = (candidate.name as NSString).pathExtension.lowercased()
            guard subtitleExtensions.contains(ext) else { return false }
            return (candidate.name as NSString).deletingPathExtension
                .lowercased()
                .hasPrefix(stem)
        }
        guard !candidates.isEmpty else { return nil }

        let ranked = candidates.sorted { lhs, rhs in
            subtitleRank(lhs.name) < subtitleRank(rhs.name)
        }
        guard let chosen = ranked.first else { return nil }
        return emby.map { $0.streamURL(for: chosen) } ?? chosen.url
    }

    private static func mime(for name: String) -> String {
        switch (name as NSString).pathExtension.lowercased() {
        case "mp4", "m4v": return "video/mp4"
        case "mkv": return "video/x-matroska"
        case "ts", "m2ts": return "video/mp2t"
        case "mov": return "video/quicktime"
        case "webm": return "video/webm"
        case "mp3": return "audio/mpeg"
        case "flac": return "audio/flac"
        case "m4a": return "audio/mp4"
        default: return "application/octet-stream"
        }
    }

    private func subtitleRank(_ name: String) -> Int {
        let lower = name.lowercased()
        if lower.contains("chs") || lower.contains("sc") || lower.contains("简体") || lower.contains("zh-cn") { return 0 }
        if lower.contains("cht") || lower.contains("tc") || lower.contains("繁体") || lower.contains("zh-tw") { return 1 }
        if lower.contains("eng") || lower.contains("en.") { return 3 }
        return 2
    }

    /// Playable siblings of the current view, so the player can advance on its own.
    private var siblings: [QueueItem] {
        let files = entries.filter { !$0.isDirectory }
        guard isEmbyLike else {
            return files.map { QueueItem(url: $0.url, name: $0.name) }
        }
        let client = emby ?? EmbyClient(server: server)
        return files.map { QueueItem(url: client.streamURL(for: $0), name: $0.name) }
    }

    private func reload() async {
        loading = true
        defer { loading = false }
        errorText = nil
        do {
            if isEmbyLike {
                let client = emby ?? EmbyClient(server: server)
                emby = client
                if client.accessToken.isEmpty {
                    _ = try await client.authenticate()
                }
                switch strip {
                case .files:
                    entries = try await client.items(parentId: parentId,
                                                      sortBy: sortBy,
                                                      ascending: ascending)
                case .resume:
                    entries = try await client.resume()
                case .latest:
                    entries = try await client.latest()
                case .collections:
                    entries = try await client.collections(parentId: collectionParent)
                }
            } else if server.type == "plex" {
                let client = plex ?? PlexClient(server: server)
                plex = client
                if plexLibraries.isEmpty { plexLibraries = try await client.libraries() }
                switch strip {
                case .files:
                    let section = path.isEmpty ? (plexLibraries.first?.id ?? "") : path
                    entries = try await client.items(section: section).map { plexEntry($0, client: client) }
                case .resume:
                    entries = try await client.onDeck().map { plexEntry($0, client: client) }
                case .latest:
                    entries = try await client.recentlyAdded().map { plexEntry($0, client: client) }
                case .collections:
                    if let key = collectionId {
                        entries = try await client.items(inCollection: key).map { plexEntry($0, client: client) }
                    } else {
                        let section = path.isEmpty ? (plexLibraries.first?.id ?? "") : path
                        entries = try await client.collections(section: section).map { collection in
                            MediaEntry(name: collection.title, url: collection.id,
                                       isDirectory: true, entryId: collection.id)
                        }
                    }
                }
            } else if server.type == "s3" {
                guard let target = S3Target(server: server) else { throw ServerError.badAddress }
                let client = s3 ?? S3Client(target: target)
                s3 = client
                entries = try await client.list(prefix: path.isEmpty ? nil : path).map { object in
                    MediaEntry(
                        name: object.name,
                        url: object.key,
                        isDirectory: object.isDirectory,
                        size: Int(object.size)
                    )
                }
            } else if server.type == "ftp" {
                guard let target = FTPTarget(server: server) else { throw ServerError.badAddress }
                let client = ftp ?? FTPClient(target: target)
                ftp = client
                try await client.connect()
                let listing = try await client.list(path.isEmpty ? target.path : path)
                entries = listing.map { entry in
                    MediaEntry(
                        name: entry.name,
                        url: entry.path,
                        isDirectory: entry.isDirectory,
                        size: entry.size
                    )
                }
            } else {
                entries = try await WebDAVClient.list(server: server, path: path)
            }
        } catch {
            errorText = error.localizedDescription
            entries = []
        }
    }
}
