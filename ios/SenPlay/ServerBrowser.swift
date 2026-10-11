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

    private var isEmbyLike: Bool { server.isEmbyLike }

    private let columns = [GridItem(.adaptive(minimum: 118), spacing: 12)]

    var body: some View {
        Group {
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
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
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
            if !stack.isEmpty, isEmbyLike == false || strip == .files {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("< 上一级") { goUp() }
                }
            }
        }
        .searchable(text: $search, prompt: "搜索")
        .onChange(of: strip) { _ in
            guard isEmbyLike else { return }
            stack = []
            path = ""
            parentId = nil
            Task { await reload() }
        }
        .onChange(of: sortBy) { _ in Task { await reload() } }
        .onChange(of: ascending) { _ in Task { await reload() } }
        .task { await reload() }
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
                        GeometryReader { geo in
                            Rectangle()
                                .fill(Color.accentColor)
                                .frame(width: geo.size.width * entry.progress, height: 3)
                                .frame(maxHeight: .infinity, alignment: .bottom)
                        }
                        .frame(height: 3)
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

    private func open(_ entry: MediaEntry) {
        guard isEmbyLike, strip == .files else {
            play(entry)
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

    private func play(_ entry: MediaEntry) {
        if isEmbyLike {
            let client = emby ?? EmbyClient(server: server)
            pending = PlayRequest(
                url: client.streamURL(for: entry),
                name: entry.name,
                headers: client.mediaHeaders(),
                positionMs: entry.positionMs,
                queue: siblings
            )
        } else {
            pending = PlayRequest(
                url: entry.url,
                name: entry.name,
                positionMs: LibraryStore.shared.savedPosition(url: entry.url),
                queue: siblings
            )
        }
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
            } else {
                entries = try await WebDAVClient.list(server: server, path: path)
            }
        } catch {
            errorText = error.localizedDescription
            entries = []
        }
    }
}
