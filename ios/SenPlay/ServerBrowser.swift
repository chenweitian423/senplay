import CoreMedia
import SwiftUI

/// Server browser for Emby-like servers and WebDAV.
///
/// For Emby-like servers it offers the three strips the reference app exposes —
/// files, continue watching and recently added — plus search and sorting. WebDAV
/// stays a plain folder walk because the protocol has nothing more to give.
struct ServerBrowserView: View {
    let server: ServerConfig
    @Binding var pending: PlayRequest?

    private enum Strip: String, CaseIterable, Identifiable {
        case files, resume, latest
        var id: String { rawValue }
        var title: String {
            switch self {
            case .files: return "文件"
            case .resume: return "继续观看"
            case .latest: return "最近添加"
            }
        }
    }

    private enum Layout: String {
        case list, grid
    }

    @State private var entries: [MediaEntry] = []
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
    @State private var bridge: FTPMediaBridge?
    @State private var ftpSize: UInt64 = 0

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
            .task { await reload() }
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
            if isEmbyLike {
                Menu {
                    Picker("板块", selection: $strip) {
                        ForEach(Strip.allCases) { Text($0.title).tag($0) }
                    }
                    Divider()
                    Picker("排序", selection: $sortBy) {
                        ForEach(EmbyClient.SortBy.allCases) { Text($0.title).tag($0) }
                    }
                    Toggle("升序", isOn: $ascending)
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
            if server.type == "plex", !plexLibraries.isEmpty, strip == .files {
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
        guard isEmbyLike else { return }
        stack = []
        path = ""
        parentId = nil
        scheduleReload()
    }

    private func scheduleReload() {
        Task { await reload() }
    }

    private var title: String {
        server.name.isEmpty ? server.address : server.name
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
                    if entry.progress > 0 {
                        progressBar(entry.progress)
                    }
                }
                Text(entry.name)
                    .font(.caption)
                    .lineLimit(2)
                    .foregroundColor(.primary)
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
                    .clipShape(RoundedRectangle(cornerRadius: 5))
                } else {
                    Image(systemName: entry.isDirectory ? "folder" : "doc")
                        .foregroundColor(.accentColor)
                        .frame(width: 24)
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(entry.name).lineLimit(2)
                    if !entry.subtitle.isEmpty {
                        Text(entry.subtitle).font(.caption2).foregroundColor(.secondary)
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
