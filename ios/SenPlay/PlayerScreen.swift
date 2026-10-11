import AVKit
import SwiftUI

/// Side-loaded subtitle support (SRT / WebVTT) rendered as an overlay, because
/// AVFoundation only exposes legible tracks for HLS streams.
final class SubtitleController: ObservableObject {
    @Published var currentText: String = ""

    private struct Cue {
        let start: Double
        let end: Double
        let text: String
    }

    private var cues: [Cue] = []
    private var timer: Timer?

    func load(urlString: String) async {
        let text: String?
        if urlString.hasPrefix("http://") || urlString.hasPrefix("https://") {
            text = try? await fetch(urlString)
        } else {
            text = try? String(contentsOfFile: urlString, encoding: .utf8)
        }
        guard let raw = text else { return }
        let parsed = SubtitleController.parse(raw)
        await MainActor.run { self.cues = parsed }
    }

    private func fetch(_ urlString: String) async throws -> String {
        guard let url = URL(string: urlString) else { return "" }
        let (data, _) = try await URLSession.shared.data(from: url)
        if let utf8 = String(data: data, encoding: .utf8) { return utf8 }
        return String(data: data, encoding: .isoLatin1) ?? ""
    }

    private static func parse(_ raw: String) -> [Cue] {
        var cues: [Cue] = []
        let normalized = raw.replacingOccurrences(of: "\r\n", with: "\n")
        for block in normalized.components(separatedBy: "\n\n") {
            let lines = block.components(separatedBy: "\n").filter { !$0.isEmpty }
            guard let timeLine = lines.first(where: { $0.contains("-->") }) else { continue }
            let parts = timeLine.components(separatedBy: "-->")
            guard parts.count == 2 else { continue }
            guard let start = SubtitleController.seconds(parts[0]), let end = SubtitleController.seconds(parts[1]) else { continue }
            let body = lines.filter { !$0.contains("-->") && !$0.hasPrefix("WEBVTT") && Int($0) == nil }
            let text = body.joined(separator: "\n")
            if !text.isEmpty {
                cues.append(Cue(start: start, end: end, text: text))
            }
        }
        return cues.sorted { $0.start < $1.start }
    }

    private static func seconds(_ raw: String) -> Double? {
        let cleaned = raw.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        let parts = cleaned.components(separatedBy: ":")
        guard parts.count >= 2 else { return nil }
        let secondsPart = Double(parts.last ?? "") ?? 0
        let minutes = Double(parts[parts.count - 2]) ?? 0
        let hours = parts.count > 2 ? (Double(parts[parts.count - 3]) ?? 0) : 0
        return hours * 3600 + minutes * 60 + secondsPart
    }

    func attach(to player: AVPlayer) {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            guard let self else { return }
            let now = CMTimeGetSeconds(player.currentTime())
            guard now.isFinite else { return }
            let match = self.cues.first { now >= $0.start && now <= $0.end }
            let text = match?.text ?? ""
            if text != self.currentText {
                DispatchQueue.main.async { self.currentText = text }
            }
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
    }
}

extension PlayRequest: Identifiable {
    var id: String { url + "|" + name }
}

struct PlayerScreen: View {
    let request: PlayRequest
    var onClose: (Double) -> Void = { _ in }

    @State private var player = AVPlayer()
    @StateObject private var subtitles = SubtitleController()
    @State private var speed: Float = 1
    @State private var showingSpeeds = false

    private let speeds: [Float] = [0.5, 0.75, 1, 1.25, 1.5, 2, 3]

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VideoPlayer(player: player)
                .ignoresSafeArea()

            if !subtitles.currentText.isEmpty {
                VStack {
                    Spacer()
                    Text(subtitles.currentText)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.black.opacity(0.55))
                        .cornerRadius(6)
                        .padding(.bottom, 72)
                }
                .allowsHitTesting(false)
            }

            VStack {
                HStack {
                    Button {
                        close()
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(10)
                            .background(Color.black.opacity(0.4))
                            .clipShape(Circle())
                    }

                    Text(request.name)
                        .font(.headline)
                        .foregroundColor(.white)
                        .lineLimit(1)

                    Spacer()

                    Menu {
                        ForEach(speeds, id: \.self) { value in
                            Button(value == 1 ? "1.0x（默认）" : String(format: "%.2fx", value)) {
                                speed = value
                                player.rate = value
                            }
                        }
                    } label: {
                        Image(systemName: "speedometer")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(10)
                            .background(Color.black.opacity(0.4))
                            .clipShape(Circle())
                    }
                }
                .padding(.horizontal, 12)
                .padding(.top, 8)

                Spacer()
            }
        }
        .statusBar(hidden: true)
        .onAppear(perform: start)
        .onDisappear(perform: finish)
    }

    private func start() {
        guard let url = URL(string: request.url) else { return }
        let item = AVPlayerItem(url: url)
        player.replaceCurrentItem(with: item)
        player.play()

        if let subtitleURL = request.subtitleURL, !subtitleURL.isEmpty {
            Task {
                await subtitles.load(urlString: subtitleURL)
                subtitles.attach(to: player)
            }
        }

        let resume = request.positionMs > 0 ? request.positionMs : LibraryStore.shared.savedPosition(url: request.url)
        if resume > 0 {
            player.seek(to: CMTime(seconds: resume / 1000, preferredTimescale: 600))
        }
    }

    private func finish() {
        subtitles.stop()
        let seconds = CMTimeGetSeconds(player.currentTime())
        let duration = CMTimeGetSeconds(player.currentItem?.duration ?? CMTime.zero)
        if seconds.isFinite, duration.isFinite, duration > 0 {
            LibraryStore.shared.rememberProgress(url: request.url, name: request.name, positionMs: seconds * 1000, durationMs: duration * 1000)
        }
        player.pause()
        player.replaceCurrentItem(with: nil)
    }

    private func close() {
        let seconds = CMTimeGetSeconds(player.currentTime())
        onClose(seconds.isFinite ? seconds : 0)
    }
}
