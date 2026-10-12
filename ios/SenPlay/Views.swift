import AVKit
import SwiftUI

struct HomeView: View {
    @ObservedObject var store: LibraryStore
    @Binding var pending: PlayRequest?
    /// The accent the whole app tints with. Read here rather than in each screen
    /// because @AppStorage is per-view and this is the one view they all sit under.
    @AppStorage("pref.general.themeColor") private var themeColor = "blue"
    @AppStorage("pref.general.defaultModule") private var defaultModule = "files"
    /// "显示记录模块" — hides the playback-history tab. The tag is kept at 3 so the
    /// other tabs do not shift under the viewer when it is turned off.
    @AppStorage("pref.ui.showRecordModule") private var showRecordModule = true
    /// "默认启动模块" is about where the app opens, so the choice is read once at
    /// construction rather than moving the tab under the viewer when it changes.
    @State private var selection: Int = HomeView.tabIndex(
        UserDefaults.standard.string(forKey: "pref.general.defaultModule") ?? "files"
    )

    /// The reference app's module names, mapped onto this app's five tabs. "文件" is
    /// the local library, which is tab 0 here.
    /// Servers and IPTV are no longer tabs of their own — they live inside the
    /// library — so a stored default naming one of them opens the library rather
    /// than a tab that no longer exists.
    private static func tabIndex(_ module: String) -> Int {
        switch module {
        case "record": return 2
        default: return 0
        }
    }

    private var accent: Color {
        switch themeColor {
        case "cyan": return Color(red: 0.30, green: 0.82, blue: 0.88)
        case "green": return Color(red: 0.51, green: 0.78, blue: 0.52)
        case "purple": return Color(red: 0.73, green: 0.41, blue: 0.78)
        case "orange": return Color(red: 1.00, green: 0.72, blue: 0.30)
        case "red": return Color(red: 0.90, green: 0.45, blue: 0.45)
        case "indigo": return Color(red: 0.47, green: 0.53, blue: 0.80)
        default: return Color(red: 0.30, green: 0.76, blue: 1.00)
        }
    }

    var body: some View {
        TabView(selection: $selection) {
            LibraryView(store: store, pending: $pending)
                .tabItem { Label("媒体库", systemImage: "film.stack") }
                .tag(0)

            SearchView(store: store, pending: $pending)
                .tabItem { Label("搜索", systemImage: "magnifyingglass") }
                .tag(1)

            if showRecordModule {
                HistoryView(store: store, pending: $pending)
                    .tabItem { Label("记录", systemImage: "clock.arrow.circlepath") }
                    .tag(2)
            }

            PreferencesView()
                .tabItem { Label("设置", systemImage: "gearshape") }
                .tag(3)
        }
        .tint(accent)
        .onChange(of: showRecordModule) { shown in
            if !shown, selection == 2 { selection = 0 }
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
    /// "视频预览图时间" — where in a local file the row's still is taken from. The
    /// crop mode belongs to the still itself and is read where it is drawn.
    @AppStorage("pref.list.previewPosition") private var previewPercent = 10

    var body: some View {
        NavigationView {
            List {
                // The reference files servers and IPTV inside the library rather than
                // as top-level tabs. Keeping them as sub-pages preserves the whole
                // add / delete / discover flow the servers screen carries, which
                // spreading it across a toolbar would scatter.
                Section(header: Text("资源")) {
                    NavigationLink {
                        ServersView(store: store, pending: $pending, standalone: false)
                    } label: {
                        Label("服务器", systemImage: "server.rack")
                    }
                    NavigationLink {
                        IPTVScreen(pending: $pending, standalone: false)
                    } label: {
                        Label("IPTV", systemImage: "tv")
                    }
                }

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
                            HStack(spacing: 12) {
                                LocalThumbnail(file: file, percent: previewPercent)
                                Text(file.lastPathComponent)
                            }
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

    /// One server as a card. The tile is an SF Symbol plus a colour chosen per
    /// protocol, so the grid reads like the reference's layout without using any
    /// of its artwork.
    private func serverCard(_ server: ServerConfig) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: Self.protocolIcon(server.type))
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 30, height: 30)
                .background(Self.protocolColor(server.type))
                .cornerRadius(7)
            Spacer(minLength: 0)
            Text(server.name.isEmpty ? server.address : server.name)
                .font(.subheadline.weight(.medium))
                .lineLimit(1)
            Text(server.type.uppercased() + " · " + server.address)
                .font(.caption2)
                .foregroundColor(.secondary)
                .lineLimit(1)
        }
        .padding(10)
        .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground))
        .cornerRadius(10)
    }

    private static func protocolIcon(_ type: String) -> String {
        switch type {
        case "emby", "jellyfin", "feiniu": return "play.rectangle.fill"
        case "plex": return "play.tv.fill"
        case "ftp", "sftp": return "folder.fill"
        case "s3": return "cloud.fill"
        case "smb": return "externaldrive.connected.to.line.below.fill"
        default: return "externaldrive.fill"
        }
    }

    private static func protocolColor(_ type: String) -> Color {
        switch type {
        case "emby": return .green
        case "jellyfin": return .purple
        case "feiniu": return .teal
        case "plex": return .orange
        case "ftp", "sftp": return .yellow
        case "s3": return .blue
        case "smb": return .indigo
        default: return .blue
        }
    }

    /// False when the library presents this screen inside its own navigation stack.
    /// An inner NavigationView there would nest a second container and swallow the
    /// pushes meant for the outer one — tapping a server would go nowhere visible.
    var standalone = true

    var body: some View {
        if standalone {
            NavigationView { content }
                .navigationViewStyle(.stack)
        } else {
            content
        }
    }

    private var content: some View {
            List {
                Section(header: Text("媒体服务器")) {
                    if store.servers.isEmpty {
                        Text("支持 WebDAV / Emby / Jellyfin / 飞牛 fnOS / Plex / FTP / S3 对象存储。也可以用 senplay://importserver?type=webdav&address=... 导入，或直接扫描局域网。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    // Two columns of cards, the way the reference lays out its library.
                    // The tile is a system symbol plus a colour per protocol — none of
                    // the reference app's own artwork is involved.
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12),
                                        GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(store.servers) { server in
                            NavigationLink {
                                ServerBrowserView(server: server, pending: $pending)
                            } label: {
                                serverCard(server)
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button("删除", role: .destructive) { store.remove(server: server) }
                            }
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

// MARK: - local file thumbnails

/// The still on a local file's row.
///
/// "视频预览图时间" says where in the file the frame comes from, as a percentage. The
/// reference app spells out that 0 means "do not generate one"
/// (`0NotGeneratingPreviewImage`), which is why a zero draws the icon rather than
/// asking for a frame at the very start.
struct LocalThumbnail: View {
    let file: URL
    let percent: Int

    @AppStorage("pref.list.fillThumbnail") private var fillThumbnail = true
    @AppStorage("pref.list.rounded") private var listRounded = true
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: fillThumbnail ? .fill : .fit)
            } else {
                Image(systemName: "film")
                    .foregroundColor(.secondary)
            }
        }
        .frame(width: 46, height: 66)
        // Without this the .fill still draws outside the frame and covers the file
        // name — `frame` constrains layout, not painting.
        .clipShape(RoundedRectangle(cornerRadius: listRounded ? 5 : 0))
        .task(id: file) {
            image = await LocalThumbnailStore.shared.frame(for: file, percent: percent)
        }
    }
}

/// Generates and caches one frame per file.
///
/// The work happens off the main thread — a file has to be opened and seeked — and the
/// result is cached by path and percentage, because otherwise every scroll back to the
/// top would re-open every file in the folder.
final class LocalThumbnailStore {
    static let shared = LocalThumbnailStore()

    private let cache = NSCache<NSString, UIImage>()
    private let queue = DispatchQueue(label: "com.senplay.thumbnails", qos: .utility)

    func frame(for file: URL, percent: Int) async -> UIImage? {
        guard percent > 0 else { return nil }
        let key = "\(file.path)#\(percent)" as NSString
        if let cached = cache.object(forKey: key) { return cached }

        return await withCheckedContinuation { continuation in
            queue.async {
                let asset = AVURLAsset(url: file)
                let seconds = CMTimeGetSeconds(asset.duration)
                guard seconds.isFinite, seconds > 0 else {
                    continuation.resume(returning: nil)
                    return
                }
                let generator = AVAssetImageGenerator(asset: asset)
                generator.appliesPreferredTrackTransform = true
                generator.maximumSize = CGSize(width: 184, height: 264)
                let target = CMTime(seconds: seconds * Double(percent) / 100,
                                    preferredTimescale: 600)
                let frame = try? generator.copyCGImage(at: target, actualTime: nil)
                let image = frame.map { UIImage(cgImage: $0) }
                if let image { self.cache.setObject(image, forKey: key) }
                continuation.resume(returning: image)
            }
        }
    }
}
