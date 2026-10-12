import SwiftUI

/// One search across everything this app can open: the URL list, the local media
/// files, the IPTV channels and the saved servers. The reference's search tab spans
/// its whole library; those are this tree's equivalents, and each hit acts directly
/// — a URL entry or a file plays, a channel plays with its group — so the tab is a
/// way in, not a read-only index.
struct SearchView: View {
    @ObservedObject var store: LibraryStore
    /// The channel cache is shared with the IPTV screen rather than re-decoded here,
    /// so a playlist refreshed over there is searchable over here.
    @ObservedObject private var iptv = IPTVStore.shared
    @Binding var pending: PlayRequest?

    @State private var query = ""
    /// Filled on appear rather than inside `hits`: the list is otherwise re-walked on
    /// every keystroke.
    @State private var localFiles: [URL] = []

    private struct Hit: Identifiable {
        let id: String
        let title: String
        let subtitle: String
        let request: PlayRequest?
        let server: ServerConfig?
    }

    var body: some View {
        NavigationView {
            List {
                if query.trimmingCharacters(in: .whitespaces).isEmpty {
                    Text("输入片名、频道、地址或服务器名。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                } else if hits.isEmpty {
                    Text("没有匹配「\(query)」的条目。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                ForEach(hits) { hit in
                    Button {
                        if let request = hit.request {
                            pending = request
                        }
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(hit.title)
                            Text(hit.subtitle)
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .searchable(text: $query, prompt: "搜索片名、频道、地址、服务器")
            .navigationTitle("搜索")
            .onAppear { localFiles = localMediaFiles() }
        }
        .navigationViewStyle(.stack)
    }

    private var hits: [Hit] {
        let needle = query.trimmingCharacters(in: .whitespaces)
        guard !needle.isEmpty else { return [] }

        var found: [Hit] = []
        for item in store.urlList where matches(item.name, needle) || matches(item.url, needle) {
            found.append(Hit(
                id: "url-" + item.url,
                title: item.name,
                subtitle: "URL · " + item.url,
                request: PlayRequest(url: item.url, name: item.name, userAgent: item.userAgent,
                                     positionMs: store.savedPosition(url: item.url)),
                server: nil
            ))
        }
        // Local files and channels carry their siblings as the queue, which is how
        // the library and the IPTV screen hand them over — a search hit then behaves
        // exactly like the row it mirrors, next and previous included.
        for file in localFiles where matches(file.lastPathComponent, needle) {
            found.append(Hit(
                id: "local-" + file.absoluteString,
                title: file.lastPathComponent,
                subtitle: "本地文件",
                request: PlayRequest(
                    url: file.absoluteString,
                    name: file.lastPathComponent,
                    positionMs: store.savedPosition(url: file.absoluteString),
                    queue: localFiles.map { QueueItem(url: $0.absoluteString, name: $0.lastPathComponent) }
                ),
                server: nil
            ))
        }
        for channel in iptv.channels where matches(channel.name, needle) || matches(channel.group, needle) {
            found.append(Hit(
                id: "iptv-" + channel.id,
                title: channel.name,
                subtitle: "IPTV · " + channel.group,
                request: PlayRequest(
                    url: channel.url,
                    name: channel.name,
                    queue: iptv.channels(in: channel.group).map { QueueItem(url: $0.url, name: $0.name) }
                ),
                server: nil
            ))
        }
        for server in store.servers where matches(server.name, needle) || matches(server.address, needle) {
            found.append(Hit(
                id: "server-" + server.id,
                title: server.name.isEmpty ? server.address : server.name,
                subtitle: server.type.uppercased() + " · " + server.address,
                request: nil,
                server: server
            ))
        }
        return found
    }

    private func matches(_ text: String, _ needle: String) -> Bool {
        text.range(of: needle, options: .caseInsensitive) != nil
    }
}
