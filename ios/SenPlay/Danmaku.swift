import Foundation
import SwiftUI

// MARK: - Model

enum DanmakuMode: Int {
    case scroll = 1      // 右→左滚动
    case top = 4         // 顶部固定
    case bottom = 5      // 底部固定
    case reverse = 6     // 左→右
}

struct DanmakuItem: Identifiable, Hashable {
    var id: String { "\(time)-\(text)" }
    var time: Double          // 秒
    var mode: DanmakuMode
    var color: UInt32
    var text: String
}

struct DanmakuEndpoint: Identifiable, Codable, Hashable {
    var id: String = UUID().uuidString
    var name: String
    /// Base URL of a dandanplay-compatible service. Left empty by default; the
    /// user supplies their own, so no private address ever lands in the repo.
    var url: String

    /// Slots are individual preference keys — `pref.danmaku.endpoint.N` — so the
    /// Android build reads and writes exactly the same entries. The cap matches on
    /// both sides; an empty slot simply means "not configured".
    private static let slotCount = 5

    static func load() -> [DanmakuEndpoint] {
        let slots = (1...slotCount).map { index in
            UserDefaults.standard.string(forKey: "pref.danmaku.endpoint.\(index)") ?? ""
        }
        if slots.allSatisfy({ $0.isEmpty }) {
            return (1...slotCount).map { DanmakuEndpoint(id: "\($0)", name: "API-\($0)", url: "") }
        }
        return slots.enumerated().map { offset, url in
            DanmakuEndpoint(id: "\(offset + 1)", name: "API-\(offset + 1)", url: url)
        }
    }

    static func save(_ list: [DanmakuEndpoint]) {
        for index in 1...slotCount {
            let value = index <= list.count ? list[index - 1].url : ""
            UserDefaults.standard.set(value, forKey: "pref.danmaku.endpoint.\(index)")
        }
    }
}

// MARK: - Loader

/// dandanplay-compatible protocol: search for an episode by keyword, then pull its
/// comment list. Only the two documented endpoints are used, so any service that
/// implements the same shape works.
final class DanmakuLoader {
    private let session = URLSession.shared

    func fetch(keyword: String, base: String) async -> [DanmakuItem] {
        guard !base.isEmpty, !keyword.isEmpty else { return [] }
        let trimmed = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let episodeId = await searchEpisode(keyword: trimmed, base: base) else { return [] }
        return await fetchComments(episodeId: episodeId, base: base)
    }

    private func searchEpisode(keyword: String, base: String) async -> Int? {
        guard var components = URLComponents(string: base + "/api/v2/search/episodes") else { return nil }
        components.queryItems = [URLQueryItem(name: "anime", value: keyword)]
        guard let url = components.url else { return nil }

        guard let data = try? await session.data(from: url).0,
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let animes = json["animes"] as? [[String: Any]] else { return nil }

        for anime in animes {
            if let episodes = anime["episodes"] as? [[String: Any]], let first = episodes.first,
               let episodeId = first["episodeId"] as? Int {
                return episodeId
            }
        }
        return nil
    }

    private func fetchComments(episodeId: Int, base: String) async -> [DanmakuItem] {
        guard let url = URL(string: "\(base)/api/v2/comment/\(episodeId)?withRelated=true"),
              let data = try? await session.data(from: url).0,
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let comments = json["comments"] as? [[String: Any]] else { return [] }

        return comments.compactMap { comment in
            guard let p = comment["p"] as? String, let m = comment["m"] as? String else { return nil }
            let parts = p.components(separatedBy: ",")
            guard let time = Double(parts.first ?? "") else { return nil }
            let modeRaw = parts.count > 1 ? Int(parts[1]) ?? 1 : 1
            let color = parts.count > 2 ? UInt32(parts[2]) ?? 0xFFFFFF : 0xFFFFFF
            return DanmakuItem(
                time: time,
                mode: DanmakuMode(rawValue: modeRaw) ?? .scroll,
                color: color,
                text: m
            )
        }
        .sorted { $0.time < $1.time }
    }
}

// MARK: - Overlay

/// Lightweight danmaku surface.
///
/// Comments are bucketed into lanes; a lane is free once its previous comment has
/// fully entered the screen. This keeps the cost proportional to the number of
/// concurrently visible comments instead of the whole track.
struct DanmakuOverlay: View {
    let items: [DanmakuItem]
    let currentTime: Double
    let fontSize: Double
    let speed: Double
    let area: Double
    /// "弹幕关键词屏蔽" — matched case-insensitively as substrings, which is what a
    /// viewer typing a word into a settings field expects.
    var blockedKeywords: [String] = []
    /// "底部弹幕转为浮动" — the reference app lets bottom comments scroll instead.
    var bottomFloats: Bool = false

    private struct Lane {
        var busyUntil: Double = 0
        var lastText: String = ""
    }

    private var visible: [(item: DanmakuItem, lane: Int)] {
        guard !items.isEmpty else { return [] }
        let window = 12.0 * speed
        let laneCount = max(Int(area * 20), 3)
        var lanes = Array(repeating: Lane(), count: laneCount)
        var result: [(DanmakuItem, Int)] = []

        for item in items {
            guard item.time <= currentTime, item.time > currentTime - window else { continue }
            if !blockedKeywords.isEmpty, blockedKeywords.contains(where: {
                !$0.isEmpty && item.text.localizedCaseInsensitiveContains($0)
            }) { continue }
            // Top comments never queue for a lane; bottom ones only replace in place
            // when the viewer has not asked for them to float.
            if item.mode == .top || (item.mode == .bottom && !bottomFloats) {
                result.append((item, item.mode == .top ? 0 : laneCount - 1))
                continue
            }
            if let index = lanes.firstIndex(where: { $0.busyUntil <= item.time && $0.lastText != item.text }) {
                let travel = 6.0 * speed
                lanes[index].busyUntil = item.time + travel
                lanes[index].lastText = item.text
                result.append((item, index))
            }
        }
        return result
    }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                ForEach(visible, id: \.item.id) { entry in
                    let elapsed = currentTime - entry.item.time
                    let progress = min(max(elapsed / (6.0 * speed), 0), 1)
                    Text(entry.item.text)
                        .font(.system(size: fontSize, weight: .semibold))
                        .foregroundColor(Color(rgb: entry.item.color))
                        .shadow(color: .black.opacity(0.75), radius: 1.5)
                        .fixedSize()
                        .position(
                            x: entry.item.mode == .top || entry.item.mode == .bottom
                                ? geo.size.width / 2
                                : geo.size.width * (1.05 - 1.1 * progress),
                            y: 18 + Double(entry.lane) * (fontSize + 8) + fontSize / 2
                        )
                        .opacity(elapsed < 0 ? 0 : (progress >= 1 ? 0 : 1))
                }
            }
        }
        .allowsHitTesting(false)
    }
}

private extension Color {
    init(rgb: UInt32) {
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }
}
