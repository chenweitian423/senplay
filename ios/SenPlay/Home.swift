import SwiftUI

/// The landing tab: what you were watching and the servers behind it.
///
/// The reference leads with a poster carousel. A frame from the file itself is what
/// this tree can produce — there is no artwork catalogue behind it — so the top shelf
/// is the resume row and it carries the same thing the carousel does: what to play
/// next, already positioned.
struct HomeTabView: View {
    @ObservedObject var store: LibraryStore
    @Binding var pending: PlayRequest?

    @AppStorage("pref.list.previewPosition") private var previewPercent = 10

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    if store.history.isEmpty && store.servers.isEmpty && store.urlList.isEmpty {
                        Text("还没有内容。在「媒体库」里添加服务器或 URL，或把视频放进「文件」App → 我的 iPhone → SenPlay。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 16)
                            .padding(.top, 24)
                    }

                    if !store.history.isEmpty {
                        shelf("继续观看") {
                            ForEach(store.history.prefix(12)) { entry in
                                resumeCard(entry)
                            }
                        }
                    }

                    if !store.servers.isEmpty {
                        shelf("我的媒体") {
                            ForEach(store.servers) { server in
                                NavigationLink {
                                    ServerBrowserView(server: server, pending: $pending)
                                } label: {
                                    serverTile(server)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    if !store.urlList.isEmpty {
                        shelf("URL 列表") {
                            ForEach(store.urlList.prefix(12)) { item in
                                urlCard(item)
                            }
                        }
                    }
                }
                .padding(.vertical, 16)
            }
            .navigationTitle("首页")
        }
        .navigationViewStyle(.stack)
    }

    @ViewBuilder
    private func shelf<Content: View>(_ title: String,
                                      @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
                .padding(.horizontal, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 12) {
                    content()
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func resumeCard(_ entry: HistoryEntry) -> some View {
        Button {
            pending = PlayRequest(url: entry.url, name: entry.name, positionMs: entry.positionMs,
                                  imageURL: entry.imageURL)
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                ZStack(alignment: .bottomLeading) {
                    poster(for: entry)
                        .frame(width: 160, height: 90)
                        .clipped()
                    ProgressView(value: entry.progress)
                        .progressViewStyle(.linear)
                        .tint(.white)
                }
                .frame(width: 160, height: 90)
                .clipShape(RoundedRectangle(cornerRadius: 8))

                Text(entry.name)
                    .font(.caption)
                    .lineLimit(1)
                Text("已看 \(Int(entry.progress * 100))%")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            .frame(width: 160, alignment: .leading)
        }
        .buttonStyle(.plain)
    }

    /// The still the browser showed, when the entry carries one; a frame from the file
    /// for something local; the neutral tile only when there is neither. Remote entries
    /// are not a dead end any more — the artwork travels with the play request now, so
    /// the shelf can draw the same poster the library did.
    @ViewBuilder
    private func poster(for entry: HistoryEntry) -> some View {
        if let address = entry.imageURL, let url = URL(string: address) {
            AsyncImage(url: url) { phase in
                if let image = phase.image {
                    image.resizable().aspectRatio(contentMode: .fill)
                } else {
                    posterPlaceholder()
                }
            }
        } else if let url = URL(string: entry.url), url.isFileURL {
            LocalThumbnail(file: url, percent: previewPercent)
        } else {
            posterPlaceholder()
        }
    }

    private func posterPlaceholder() -> some View {
        Rectangle()
            .fill(Color.secondary.opacity(0.22))
            .overlay(Image(systemName: "film").foregroundColor(.secondary))
    }

    private func serverTile(_ server: ServerConfig) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.accentColor.opacity(0.18))
                .frame(width: 160, height: 90)
                .overlay(
                    Image(systemName: "server.rack")
                        .font(.system(size: 26))
                        .foregroundColor(.accentColor)
                )
            Text(server.name.isEmpty ? server.address : server.name)
                .font(.caption)
                .lineLimit(1)
            Text(server.type.uppercased())
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(width: 160, alignment: .leading)
    }

    private func urlCard(_ item: UrlListItem) -> some View {
        Button {
            pending = PlayRequest(url: item.url, name: item.name, userAgent: item.userAgent,
                                  positionMs: store.savedPosition(url: item.url))
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.secondary.opacity(0.18))
                    .frame(width: 160, height: 90)
                    .overlay(
                        Image(systemName: "link")
                            .font(.system(size: 24))
                            .foregroundColor(.secondary)
                    )
                Text(item.name)
                    .font(.caption)
                    .lineLimit(1)
                Text(item.url)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
            .frame(width: 160, alignment: .leading)
        }
        .buttonStyle(.plain)
    }
}
