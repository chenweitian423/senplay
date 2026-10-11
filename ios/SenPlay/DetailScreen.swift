import SwiftUI

/// One item's own page: artwork, the metadata the list has no room for, and the
/// similar items the server suggests.
///
/// It asks for the item by id rather than taking the list row's copy. A list entry is
/// built from a query that asks only for what a row shows, and a detail view wants
/// genres, studios and tags as well; fetching by id also means a list that has gone
/// stale behind a rotation cannot produce a stale page.
struct DetailScreen: View {
    let entry: MediaEntry
    let client: EmbyClient
    var onPlay: (MediaEntry) -> Void

    @Environment(\.dismiss) private var dismiss

    @AppStorage("pref.library.showSimilar") private var showSimilar = true
    @AppStorage("pref.library.showCast") private var showCast = true
    @AppStorage("pref.library.showRating") private var showRating = true
    @AppStorage("pref.list.showResolution") private var showResolution = true
    @AppStorage("pref.list.showHDRTag") private var showHDRTag = true

    @State private var detail: MediaEntry?
    @State private var similar: [MediaEntry] = []
    @State private var loading = true

    private var shown: MediaEntry { detail ?? entry }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header
                if let overview = shown.overview, !overview.isEmpty {
                    Text(overview)
                        .font(.callout)
                        .foregroundColor(.secondary)
                }
                if showCast, !shown.cast.isEmpty {
                    labelled("演职人员", shown.cast.joined(separator: " / "))
                }
                if showSimilar, !similar.isEmpty {
                    similarSection
                }
            }
            .padding(20)
        }
        .navigationTitle(shown.name)
        .navigationBarTitleDisplayMode(.inline)
        .overlay {
            if loading {
                ProgressView()
            }
        }
        .task { await load() }
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 16) {
            poster
            VStack(alignment: .leading, spacing: 8) {
                if showRating, let rating = shown.rating, rating > 0 {
                    Text(String(format: "★ %.1f", rating))
                        .font(.subheadline.weight(.medium))
                }
                if let facts = facts, !facts.isEmpty {
                    Text(facts)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                Button {
                    onPlay(shown)
                    dismiss()
                } label: {
                    Label(shown.positionMs > 0 ? "继续播放" : "播放", systemImage: "play.fill")
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 18)
                        .padding(.vertical, 9)
                        .background(Color.accentColor)
                        .foregroundColor(.white)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .padding(.top, 4)
            }
            Spacer(minLength: 0)
        }
    }

    private var poster: some View {
        AsyncImage(url: shown.imageURL.flatMap(URL.init(string:))) { phase in
            switch phase {
            case .success(let image): image.resizable().aspectRatio(contentMode: .fill)
            default: Color.secondary.opacity(0.2)
            }
        }
        .frame(width: 110, height: 165)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    /// Year, runtime, resolution and the HDR tag, in the same order and spelling the
    /// list rows use — the two views are showing the same facts about the same file.
    private var facts: String? {
        var parts: [String] = []
        if let year = shown.year, year > 0 { parts.append(String(year)) }
        if shown.durationMs > 0 {
            parts.append(Self.clock(shown.durationMs / 1000))
        }
        if showHDRTag, !shown.videoRange.isEmpty, shown.videoRange != "SDR" {
            parts.append(shown.videoRange.uppercased())
        }
        if showResolution, shown.width > 0, shown.height > 0 {
            parts.append("\(shown.width)×\(shown.height)")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private func labelled(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.footnote.weight(.semibold))
            Text(value).font(.footnote).foregroundColor(.secondary)
        }
    }

    private var similarSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("类似作品").font(.footnote.weight(.semibold))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(similar, id: \.url) { item in
                        Button {
                            onPlay(item)
                            dismiss()
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                AsyncImage(url: item.imageURL.flatMap(URL.init(string:))) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image.resizable().aspectRatio(contentMode: .fill)
                                    default:
                                        Color.secondary.opacity(0.2)
                                    }
                                }
                                .frame(width: 92, height: 138)
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                                Text(item.name)
                                    .font(.caption2)
                                    .lineLimit(2)
                                    .frame(width: 92, alignment: .leading)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func load() async {
        loading = true
        defer { loading = false }
        detail = try? await client.item(id: entry.entryId ?? entry.url)
        guard showSimilar else { return }
        similar = (try? await client.similar(id: entry.entryId ?? entry.url)) ?? []
    }

    private static func clock(_ seconds: Double) -> String {
        let total = Int(seconds.rounded())
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, total % 60)
            : String(format: "%d:%02d", minutes, total % 60)
    }
}
