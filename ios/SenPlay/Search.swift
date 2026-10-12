import SwiftUI

/// One search across everything this app can open. The reference's search tab spans
/// its whole library; here the equivalents are the URL list and the saved servers.
/// Results carry enough to act on directly — a URL entry plays, a server opens its
/// browser — so the tab is a way in, not a read-only index.
struct SearchView: View {
    @ObservedObject var store: LibraryStore
    @Binding var pending: PlayRequest?

    @State private var query = ""

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
                    Text("输入片名、地址或服务器名。")
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
            .searchable(text: $query, prompt: "搜索片名、地址、服务器")
            .navigationTitle("搜索")
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
                subtitle: item.url,
                request: PlayRequest(url: item.url, name: item.name, userAgent: item.userAgent,
                                     positionMs: store.savedPosition(url: item.url)),
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
