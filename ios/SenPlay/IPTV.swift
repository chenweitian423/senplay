import Foundation
import SwiftUI

// MARK: - Model

struct IPTVChannel: Identifiable, Hashable, Codable {
    var id: String { url + "|" + name }
    var name: String
    var url: String
    var group: String
    var logo: String?
    var tvgId: String?
}

struct IPTVSource: Identifiable, Codable, Hashable {
    var id: String = UUID().uuidString
    var name: String
    var url: String
}

// MARK: - Parser

/// M3U / M3U8 playlist parser. Handles the extended attributes that IPTV lists
/// actually ship (`tvg-id`, `tvg-logo`, `group-title`) and tolerates CRLF, BOM and
/// blank lines. Anything it cannot classify is skipped rather than guessed at.
enum M3UParser {
    static func parse(_ text: String) -> [IPTVChannel] {
        var channels: [IPTVChannel] = []
        var pendingName: String?
        var pendingGroup = "未分组"
        var pendingLogo: String?
        var pendingTvgId: String?

        for rawLine in text.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty else { continue }

            if line.hasPrefix("#EXTINF:") {
                let payload = String(line.dropFirst("#EXTINF:".count))
                // "#EXTINF:-1 tvg-logo=\"x\" group-title=\"y\",Name"
                if let comma = payload.lastIndex(of: ",") {
                    pendingName = payload[payload.index(after: comma)...]
                        .trimmingCharacters(in: .whitespaces)
                    let attributes = String(payload[payload.startIndex..<comma])
                    pendingGroup = attribute("group-title", in: attributes) ?? "未分组"
                    pendingLogo = attribute("tvg-logo", in: attributes)
                    pendingTvgId = attribute("tvg-id", in: attributes)
                } else {
                    pendingName = nil
                }
                continue
            }

            if line.hasPrefix("#") { continue }

            // A bare URL line closes the preceding EXTINF.
            if line.contains("://") {
                if let name = pendingName {
                    channels.append(IPTVChannel(name: name, url: line, group: pendingGroup,
                                                logo: pendingLogo, tvgId: pendingTvgId))
                }
                pendingName = nil
                pendingGroup = "未分组"
                pendingLogo = nil
                pendingTvgId = nil
            }
        }
        return channels
    }

    private static func attribute(_ key: String, in text: String) -> String? {
        guard let range = text.range(of: key + "=\"") else { return nil }
        let rest = text[range.upperBound...]
        guard let end = rest.firstIndex(of: "\"") else { return nil }
        let value = String(rest[rest.startIndex..<end]).trimmingCharacters(in: .whitespaces)
        return value.isEmpty ? nil : value
    }
}

// MARK: - Store

final class IPTVStore: ObservableObject {
    @Published private(set) var sources: [IPTVSource] = []
    @Published private(set) var channels: [IPTVChannel] = []
    @Published private(set) var lastError: String?

    private let sourcesKey = "iptv.sources.v1"
    private let channelsKey = "iptv.channels.v1"

    init() {
        let defaults = UserDefaults.standard
        if let data = defaults.data(forKey: sourcesKey),
           let decoded = try? JSONDecoder().decode([IPTVSource].self, from: data) {
            sources = decoded
        }
        if let data = defaults.data(forKey: channelsKey),
           let decoded = try? JSONDecoder().decode([IPTVChannel].self, from: data) {
            channels = decoded
        }
    }

    var groups: [String] {
        Array(Set(channels.map(\.group))).sorted()
    }

    func channels(in group: String) -> [IPTVChannel] {
        channels.filter { $0.group == group }
    }

    func addSource(name: String, url: String) {
        guard !url.isEmpty, !sources.contains(where: { $0.url == url }) else { return }
        sources.append(IPTVSource(name: name.isEmpty ? url : name, url: url))
        persistSources()
    }

    func removeSource(id: String) {
        sources.removeAll { $0.id == id }
        persistSources()
    }

    func clearChannels() {
        channels = []
        persistChannels()
    }

    /// Reloads every configured playlist and replaces the channel list.
    func refresh() async {
        var collected: [IPTVChannel] = []
        var failure: String?
        for source in sources {
            do {
                let text = try await load(source.url)
                collected.append(contentsOf: M3UParser.parse(text))
            } catch {
                failure = "\(source.name)：\(error.localizedDescription)"
            }
        }
        await MainActor.run {
            if !collected.isEmpty {
                channels = collected
                persistChannels()
            }
            lastError = failure
        }
    }

    /// Accepts a remote playlist or a file URL from the document picker.
    func importPlaylist(at fileURL: URL) {
        let scoped = fileURL.startAccessingSecurityScopedResource()
        defer { if scoped { fileURL.stopAccessingSecurityScopedResource() } }
        guard let text = try? String(contentsOf: fileURL, encoding: .utf8) else {
            lastError = "无法读取所选文件"
            return
        }
        let parsed = M3UParser.parse(text)
        guard !parsed.isEmpty else {
            lastError = "所选文件里没有可识别的频道"
            return
        }
        channels = parsed
        persistChannels()
        lastError = nil
    }

    private func load(_ urlString: String) async throws -> String {
        guard let url = URL(string: urlString) else { throw ServerError.badAddress }
        let (data, _) = try await URLSession.shared.data(from: url)
        if let text = String(data: data, encoding: .utf8) { return text }
        if let text = String(data: data, encoding: .isoLatin1) { return text }
        throw ServerError.empty
    }

    private func persistSources() {
        if let data = try? JSONEncoder().encode(sources) {
            UserDefaults.standard.set(data, forKey: sourcesKey)
        }
    }

    private func persistChannels() {
        if let data = try? JSONEncoder().encode(channels) {
            UserDefaults.standard.set(data, forKey: channelsKey)
        }
    }
}

// MARK: - Screen

struct IPTVScreen: View {
    @Binding var pending: PlayRequest?
    @StateObject private var store = IPTVStore()

    @State private var showingAdd = false
    @State private var newName = ""
    @State private var newURL = ""
    @State private var showingImporter = false
    @State private var search = ""
    @State private var refreshing = false

    var body: some View {
        NavigationView {
            List {
                if let error = store.lastError {
                    Text("错误：\(error)").font(.footnote).foregroundColor(.red)
                }

                if store.sources.isEmpty {
                    Section {
                        Text("添加一个 M3U/M3U8 地址，或从「文件」里导入播放列表。")
                            .font(.footnote).foregroundColor(.secondary)
                    }
                }

                if !store.sources.isEmpty {
                    Section(header: Text("播放列表")) {
                        ForEach(store.sources) { source in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(source.name).font(.body)
                                Text(source.url).font(.caption).foregroundColor(.secondary).lineLimit(1)
                            }
                            .swipeActions {
                                Button("删除", role: .destructive) { store.removeSource(id: source.id) }
                            }
                        }
                    }
                }

                let filtered = filteredChannels()
                if !filtered.isEmpty {
                    ForEach(store.groups, id: \.self) { group in
                        let inGroup = filtered.filter { $0.group == group }
                        if !inGroup.isEmpty {
                            Section(header: Text("\(group)（\(inGroup.count)）")) {
                                ForEach(inGroup) { channel in
                                    Button { play(channel, within: inGroup) } label: {
                                        HStack(spacing: 10) {
                                            Image(systemName: "tv")
                                                .foregroundColor(.accentColor)
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(channel.name).lineLimit(1)
                                                if channel.tvgId != nil {
                                                    Text(channel.tvgId!).font(.caption2)
                                                        .foregroundColor(.secondary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .searchable(text: $search, prompt: "搜索频道")
            .navigationTitle("IPTV")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        showingImporter = true
                    } label: {
                        Image(systemName: "folder")
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 14) {
                        Button {
                            refreshing = true
                            Task {
                                await store.refresh()
                                refreshing = false
                            }
                        } label: {
                            if refreshing { ProgressView() } else { Image(systemName: "arrow.clockwise") }
                        }
                        .disabled(store.sources.isEmpty)
                        Button { showingAdd = true } label: { Image(systemName: "plus") }
                    }
                }
            }
            .fileImporter(isPresented: $showingImporter,
                          allowedContentTypes: [.plainText, .data],
                          allowsMultipleSelection: false) { result in
                if case .success(let urls) = result, let first = urls.first {
                    store.importPlaylist(at: first)
                }
            }
            .sheet(isPresented: $showingAdd) {
                NavigationView {
                    Form {
                        TextField("名称", text: $newName)
                        TextField("M3U 地址", text: $newURL)
                            .textInputAutocapitalization(.never)
                            .disableAutocorrection(true)
                        Button("添加并刷新") {
                            store.addSource(name: newName, url: newURL)
                            newName = ""
                            newURL = ""
                            showingAdd = false
                            refreshing = true
                            Task {
                                await store.refresh()
                                refreshing = false
                            }
                        }
                        .disabled(newURL.isEmpty)
                    }
                    .navigationTitle("添加播放列表")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("取消") { showingAdd = false }
                        }
                    }
                }
            }
        }
        .navigationViewStyle(.stack)
    }

    private func filteredChannels() -> [IPTVChannel] {
        let query = search.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return store.channels }
        return store.channels.filter {
            $0.name.localizedCaseInsensitiveContains(query) || $0.group.localizedCaseInsensitiveContains(query)
        }
    }

    private func play(_ channel: IPTVChannel, within group: [IPTVChannel]) {
        pending = PlayRequest(
            url: channel.url,
            name: channel.name,
            queue: group.map { QueueItem(url: $0.url, name: $0.name) }
        )
    }
}
