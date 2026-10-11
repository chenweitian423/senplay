import AVKit
import SwiftUI

struct HomeView: View {
    @ObservedObject var store: LibraryStore
    @Binding var pending: PlayRequest?

    var body: some View {
        TabView {
            LibraryView(store: store, pending: $pending)
                .tabItem { Label("媒体库", systemImage: "film.stack") }

            ServersView(store: store, pending: $pending)
                .tabItem { Label("服务器", systemImage: "server.rack") }

            IPTVScreen(pending: $pending)
                .tabItem { Label("IPTV", systemImage: "tv") }

            HistoryView(store: store, pending: $pending)
                .tabItem { Label("记录", systemImage: "clock.arrow.circlepath") }

            PreferencesView()
                .tabItem { Label("设置", systemImage: "gearshape") }
        }
    }
}

/// Playback history with the resume position surfaced per row, matching the
/// reference app's "记录" module.
struct HistoryView: View {
    @ObservedObject var store: LibraryStore
    @Binding var pending: PlayRequest?

    var body: some View {
        NavigationView {
            List {
                if store.history.isEmpty {
                    Text("还没有播放记录。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                ForEach(store.history, id: \.url) { entry in
                    Button {
                        pending = PlayRequest(
                            url: entry.url,
                            name: entry.name,
                            positionMs: entry.positionMs,
                            queue: store.history.map { QueueItem(url: $0.url, name: $0.name) }
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(entry.name).lineLimit(1)
                            if entry.durationMs > 0 {
                                ProgressView(value: min(max(entry.positionMs / entry.durationMs, 0), 1))
                                    .progressViewStyle(.linear)
                            }
                            Text("\(timecode(entry.positionMs)) / \(timecode(entry.durationMs)) · \(relative(entry.updatedAt))")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                    }
                    .swipeActions {
                        Button("删除", role: .destructive) { store.removeHistory(url: entry.url) }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("记录")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("清空") { store.clearHistory() }
                        .disabled(store.history.isEmpty)
                }
            }
        }
        .navigationViewStyle(.stack)
    }

    private func timecode(_ ms: Double) -> String {
        guard ms.isFinite, ms > 0 else { return "00:00" }
        let total = Int(ms / 1000)
        let h = total / 3600, m = (total % 3600) / 60, s = total % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%02d:%02d", m, s)
    }

    private func relative(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

struct LibraryView: View {
    @ObservedObject var store: LibraryStore
    @Binding var pending: PlayRequest?

    @State private var showingAdd = false
    @State private var newURL = ""
    @State private var newTitle = ""
    @State private var localFiles: [URL] = []

    var body: some View {
        NavigationView {
            List {
                Section(header: Text("URL 列表")) {
                    if store.urlList.isEmpty {
                        Text("还没有条目。可以手动添加，或用 senplay:// 链接导入。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    ForEach(store.urlList) { item in
                        Button {
                            pending = PlayRequest(url: item.url, name: item.name, userAgent: item.userAgent,
                                                  positionMs: store.savedPosition(url: item.url))
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.name).font(.body)
                                Text(item.url).font(.caption).foregroundColor(.secondary).lineLimit(1)
                            }
                        }
                        .swipeActions {
                            Button("删除", role: .destructive) { store.remove(url: item.url) }
                        }
                    }
                }

                Section(header: Text("本地文件（App 文档目录）")) {
                    if localFiles.isEmpty {
                        Text("把视频放进「文件」App → 我的 iPhone → SenPlay，或从其他 App 分享进来。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    ForEach(localFiles, id: \.self) { file in
                        Button {
                            pending = PlayRequest(
                                url: file.absoluteString,
                                name: file.lastPathComponent,
                                positionMs: store.savedPosition(url: file.absoluteString),
                                queue: localFiles.map {
                                    QueueItem(url: $0.absoluteString, name: $0.lastPathComponent)
                                }
                            )
                        } label: {
                            Text(file.lastPathComponent)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("媒体库")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showingAdd = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showingAdd) {
                NavigationView {
                    Form {
                        TextField("媒体地址", text: $newURL)
                            .textInputAutocapitalization(.never)
                            .disableAutocorrection(true)
                        TextField("显示名称（可留空）", text: $newTitle)
                    }
                    .navigationTitle("添加 URL")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("取消") { showingAdd = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("保存") {
                                let trimmed = newURL.trimmingCharacters(in: .whitespacesAndNewlines)
                                if !trimmed.isEmpty {
                                    let name = newTitle.isEmpty ? (trimmed as NSString).lastPathComponent : newTitle
                                    store.add(url: UrlListItem(url: trimmed, name: name, userAgent: nil))
                                }
                                newURL = ""
                                newTitle = ""
                                showingAdd = false
                            }
                        }
                    }
                }
            }
            .onAppear(perform: refreshLocalFiles)
        }
        .navigationViewStyle(.stack)
    }

    private func refreshLocalFiles() {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let contents = (try? FileManager.default.contentsOfDirectory(at: documents, includingPropertiesForKeys: nil)) ?? []
        let mediaExtensions: Set<String> = ["mp4", "mkv", "mov", "m4v", "avi", "flv", "ts", "webm", "mp3", "flac", "aac", "wav", "m4a"]
        localFiles = contents.filter { mediaExtensions.contains($0.pathExtension.lowercased()) }
    }
}

struct ServersView: View {
    @ObservedObject var store: LibraryStore
    @Binding var pending: PlayRequest?

    @State private var showingAdd = false
    @State private var newType = "webdav"
    @State private var newName = ""
    @State private var newAddress = ""
    @State private var newUser = ""
    @State private var newPassword = ""
    @State private var showingDiscovery = false

    private let types = ["webdav", "emby", "jellyfin", "feiniu", "plex", "ftp", "s3"]

    var body: some View {
        NavigationView {
            List {
                Section(header: Text("媒体服务器")) {
                    if store.servers.isEmpty {
                        Text("支持 WebDAV / Emby / Jellyfin / 飞牛 fnOS / Plex / FTP / S3 对象存储。也可以用 senplay://importserver?type=webdav&address=... 导入，或直接扫描局域网。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    ForEach(store.servers) { server in
                        NavigationLink {
                            ServerBrowserView(server: server, pending: $pending)
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(server.name.isEmpty ? server.address : server.name)
                                Text(server.type.uppercased() + " · " + server.address)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        .swipeActions {
                            Button("删除", role: .destructive) { store.remove(server: server) }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("服务器")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 16) {
                        Button { showingDiscovery = true } label: {
                            Image(systemName: "wifi")
                        }
                        Button { showingAdd = true } label: { Image(systemName: "plus") }
                    }
                }
            }
            .sheet(isPresented: $showingDiscovery) {
                DiscoverySheet(store: store, isPresented: $showingDiscovery)
            }
            .sheet(isPresented: $showingAdd) {
                NavigationView {
                    Form {
                        Picker("类型", selection: $newType) {
                            ForEach(types, id: \.self) { Text($0).tag($0) }
                        }
                        TextField("地址，例如 https://nas.example.com:5006", text: $newAddress)
                            .textInputAutocapitalization(.never)
                            .disableAutocorrection(true)
                        TextField("名称", text: $newName)
                        TextField("用户名", text: $newUser)
                            .textInputAutocapitalization(.never)
                        SecureField("密码", text: $newPassword)
                    }
                    .navigationTitle("添加服务器")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("取消") { showingAdd = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("保存") {
                                let address = newAddress.trimmingCharacters(in: .whitespacesAndNewlines)
                                if !address.isEmpty {
                                    store.save(ServerConfig(
                                        id: UUID().uuidString,
                                        type: newType,
                                        name: newName.isEmpty ? newType : newName,
                                        address: address.hasSuffix("/") ? String(address.dropLast()) : address,
                                        username: newUser,
                                        password: newPassword
                                    ))
                                }
                                newName = ""
                                newAddress = ""
                                newUser = ""
                                newPassword = ""
                                showingAdd = false
                            }
                        }
                    }
                }
            }
        }
        .navigationViewStyle(.stack)
    }
}

struct SettingsView: View {
    @ObservedObject var store: LibraryStore

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("播放")) {
                    Toggle("记住播放进度", isOn: $store.rememberProgress)
                    HStack {
                        Text("默认 User-Agent")
                        Spacer()
                        TextField("User-Agent", text: $store.defaultUserAgent)
                            .multilineTextAlignment(.trailing)
                            .textInputAutocapitalization(.never)
                            .disableAutocorrection(true)
                    }
                }

                Section(header: Text("数据")) {
                    HStack {
                        Text("播放历史")
                        Spacer()
                        Text("\(store.history.count) 条").foregroundColor(.secondary)
                    }
                    Button("清空播放历史", role: .destructive) { store.clearHistory() }
                    HStack {
                        Text("已保存服务器")
                        Spacer()
                        Text("\(store.servers.count)").foregroundColor(.secondary)
                    }
                }

                Section(header: Text("关于")) {
                    Text("SenPlay 是按公开行为契约自行实现的播放器：URL Scheme、媒体服务器接入方式与交互取自 SenPlayer 6.2.1 的可观察行为，不含其任何代码。")
                        .font(.footnote)
                    Text("版本 1.0.0").font(.footnote).foregroundColor(.secondary)
                }
            }
            .navigationTitle("设置")
        }
        .navigationViewStyle(.stack)
    }
}
