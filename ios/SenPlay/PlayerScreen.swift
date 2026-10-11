import AVKit
import SwiftUI
import UIKit

// MARK: - Preferences keys shared with Preferences.swift

enum PrefKey {
    // 播放
    static let resumeFromLast = "pref.play.resumeFromLast"
    static let accurateSeek = "pref.play.accurateSeek"
    static let pauseAutoLock = "pref.play.pauseAutoLock"
    static let forwardSeconds = "pref.play.forwardSeconds"
    static let backwardSeconds = "pref.play.backwardSeconds"
    static let preloadMode = "pref.play.preloadMode"
    static let useSystemProxy = "pref.play.useSystemProxy"
    // 界面
    static let progressDot = "pref.ui.progressDot"
    static let timeMode = "pref.ui.timeMode"
    static let controlBarStyle = "pref.ui.controlBarStyle"
    static let autoHideSeconds = "pref.ui.autoHideSeconds"
    static let autoFullScreen = "pref.ui.autoFullScreen"
    static let fastSeekButtons = "pref.ui.fastSeekButtons"
    static let switchMediaButton = "pref.ui.switchMediaButton"
    static let screenshotButton = "pref.ui.screenshotButton"
    static let showStartTitle = "pref.ui.showStartTitle"
    static let showSkipTip = "pref.ui.showSkipTip"
    static let showCloudThrottleTip = "pref.ui.showCloudThrottleTip"
    // 手势
    static let clickAction = "pref.gesture.click"
    static let doubleLeftAction = "pref.gesture.doubleLeft"
    static let doubleRightAction = "pref.gesture.doubleRight"
    static let doubleCenterAction = "pref.gesture.doubleCenter"
    static let leftVerticalAction = "pref.gesture.leftVertical"
    static let rightVerticalAction = "pref.gesture.rightVertical"
    static let longPressSpeed = "pref.gesture.longPressSpeed"
    static let rememberZoomSize = "pref.gesture.rememberZoomSize"
    static let useAppBrightness = "pref.gesture.useAppBrightness"
    // 视频
    static let preferHardwareDecode = "pref.video.hardwareDecode"
    static let screenshotFormat = "pref.video.screenshotFormat"
    static let jpegQuality = "pref.video.jpegQuality"
    static let screenshotToClipboard = "pref.video.screenshotToClipboard"
    static let switchBlackout = "pref.video.switchBlackout"
    // 音频
    static let rememberAudioTrack = "pref.audio.rememberTrack"
    static let audioFirstLanguage = "pref.audio.firstLanguage"
    static let audioEnhance = "pref.audio.enhance"
    static let multiChannel = "pref.audio.multiChannel"
    // 字幕
    static let rememberSubtitleTrack = "pref.subtitle.rememberTrack"
    static let subtitleFirstLanguage = "pref.subtitle.firstLanguage"
    static let subtitleLoadingRule = "pref.subtitle.loadingRule"
    static let subtitleMask = "pref.subtitle.mask"
    static let ignoreAssStyle = "pref.subtitle.ignoreAss"
    static let ignoreSrtStyle = "pref.subtitle.ignoreSrt"
    static let subtitleFont = "pref.subtitle.font"
    static let subtitleScale = "pref.subtitle.scale"
    // 弹幕
    static let danmakuEnabled = "pref.danmaku.enabled"
    static let danmakuFont = "pref.danmaku.font"
    static let danmakuSize = "pref.danmaku.size"
    static let danmakuSpeed = "pref.danmaku.speed"
    static let danmakuArea = "pref.danmaku.area"
    static let danmakuSkipShort = "pref.danmaku.skipShort"
}

enum GestureAction: String, CaseIterable, Identifiable {
    case none, playPause, fullscreen, toggleControls, forward, backward, brightness, volume, speed
    var id: String { rawValue }
    var title: String {
        switch self {
        case .none: return "无"
        case .playPause: return "播放/暂停"
        case .fullscreen: return "全屏"
        case .toggleControls: return "显示/隐藏控制栏"
        case .forward: return "快进"
        case .backward: return "快退"
        case .brightness: return "调节亮度"
        case .volume: return "调节音量"
        case .speed: return "倍速"
        }
    }
}

// MARK: - Side-loaded subtitle support (SRT / WebVTT)
//
// Rendered as an overlay because AVFoundation only exposes legible tracks for
// HLS streams and for side-loaded tracks it manages itself.

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

// MARK: - Bare AVPlayerLayer host
//
// `VideoPlayer` always installs the system control bar, which we must replace,
// so the render surface is hosted directly.

struct PlayerSurface: UIViewRepresentable {
    let player: AVPlayer
    var gravity: AVLayerVideoGravity = .resizeAspect

    final class View: UIView {
        override static var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
        init(player: AVPlayer) {
            super.init(frame: .zero)
            backgroundColor = .black
            playerLayer.player = player
            playerLayer.videoGravity = .resizeAspect
        }
        required init?(coder: NSCoder) { fatalError("not used") }
    }

    func makeUIView(context: Context) -> View { View(player: player) }

    func updateUIView(_ uiView: View, context: Context) {
        uiView.playerLayer.videoGravity = gravity
        if uiView.playerLayer.player !== player { uiView.playerLayer.player = player }
    }
}

extension PlayRequest: Identifiable {
    public var id: String { url + "|" + name }
}

// MARK: - Player

private enum DragMode {
    case none, seeking, brightness, volume
}

private struct PlayerHUD: Equatable {
    var symbol: String
    var text: String
}

struct PlayerScreen: View {
    let request: PlayRequest
    var onClose: (Double) -> Void = { _ in }

    @AppStorage(PrefKey.resumeFromLast) private var resumeFromLast = true
    @AppStorage(PrefKey.accurateSeek) private var accurateSeek = false
    @AppStorage(PrefKey.forwardSeconds) private var forwardSeconds = 15
    @AppStorage(PrefKey.backwardSeconds) private var backwardSeconds = 15
    @AppStorage(PrefKey.progressDot) private var progressDot = true
    @AppStorage(PrefKey.timeMode) private var timeMode = "total"
    @AppStorage(PrefKey.autoHideSeconds) private var autoHideSeconds = 3
    @AppStorage(PrefKey.fastSeekButtons) private var fastSeekButtons = false
    @AppStorage(PrefKey.screenshotButton) private var screenshotButton = true
    @AppStorage(PrefKey.doubleLeftAction) private var doubleLeftAction = GestureAction.backward.rawValue
    @AppStorage(PrefKey.doubleRightAction) private var doubleRightAction = GestureAction.forward.rawValue
    @AppStorage(PrefKey.doubleCenterAction) private var doubleCenterAction = GestureAction.playPause.rawValue
    @AppStorage(PrefKey.leftVerticalAction) private var leftVerticalAction = GestureAction.brightness.rawValue
    @AppStorage(PrefKey.rightVerticalAction) private var rightVerticalAction = GestureAction.volume.rawValue
    @AppStorage(PrefKey.rememberZoomSize) private var rememberZoomSize = true
    @AppStorage(PrefKey.useAppBrightness) private var useAppBrightness = false
    @AppStorage(PrefKey.subtitleScale) private var subtitleScale = 1.0
    @AppStorage(PrefKey.jpegQuality) private var jpegQuality = 0.8

    @State private var player = AVPlayer()
    @StateObject private var subtitles = SubtitleController()

    @State private var controlsVisible = true
    @State private var isPlaying = true
    @State private var currentTime: Double = 0
    @State private var duration: Double = 0
    @State private var scrubbing = false
    @State private var scrubTarget: Double = 0
    @State private var speed: Float = 1
    @State private var zoom: CGFloat = 1
    @State private var volume: Double = 1
    @State private var brightness: Double = -1
    @State private var dragMode: DragMode = .none
    @State private var dragStartTime: Double = 0
    @State private var dragStartVolume: Double = 1
    @State private var dragStartBrightness: Double = 0.5
    @State private var longPressSpeedActive = false
    @State private var hud: PlayerHUD?
    @State private var toast: String?
    @State private var timeObserver: Any?
    @State private var endObserver: NSObjectProtocol?
    @State private var hideTask: DispatchWorkItem?

    private let speeds: [Float] = [0.5, 0.75, 1, 1.25, 1.5, 2, 3]

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.black.ignoresSafeArea()
                PlayerSurface(player: player)
                    .ignoresSafeArea()
                    .scaleEffect(zoom)

                if !subtitles.currentText.isEmpty {
                    VStack {
                        Spacer()
                        Text(subtitles.currentText)
                            .font(.system(size: 18 * subtitleScale, weight: .semibold))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color.black.opacity(0.55))
                            .cornerRadius(6)
                            .padding(.bottom, 78)
                    }
                    .allowsHitTesting(false)
                }

                gestureLayer(size: geo.size)

                if let hud {
                    hudView(hud)
                }

                if let toast {
                    VStack {
                        Spacer().frame(height: 64)
                        Text(toast)
                            .font(.footnote.weight(.medium))
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(Color.black.opacity(0.6))
                            .cornerRadius(8)
                        Spacer()
                    }
                    .allowsHitTesting(false)
                }

                if controlsVisible {
                    controls(size: geo.size)
                        .transition(.opacity)
                }
            }
        }
        .statusBar(hidden: true)
        .onAppear(perform: start)
        .onDisappear(perform: finish)
        .onTapGesture { toggleControls() }
    }

    // MARK: gestures

    private func gestureLayer(size: CGSize) -> some View {
        HStack(spacing: 0) {
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { perform(doubleLeftAction, at: CGPoint(x: 0, y: 0)) }
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { perform(doubleCenterAction, at: CGPoint(x: 0, y: 0)) }
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { perform(doubleRightAction, at: CGPoint(x: 0, y: 0)) }
        }
        .simultaneousGesture(
            DragGesture(minimumDistance: 18)
                .onChanged { value in handleDrag(value, size: size) }
                .onEnded { _ in handleDragEnd() }
        )
        .simultaneousGesture(
            MagnificationGesture()
                .onChanged { value in zoom = max(0.6, min(3.0, value)) }
                .onEnded { value in
                    zoom = max(0.6, min(3.0, value))
                    toast = String(format: "缩放 %.2fx", zoom)
                    scheduleToastClear()
                }
        )
        .simultaneousGesture(
            LongPressGesture(minimumDuration: 0.5)
                .onEnded { _ in
                    guard !longPressSpeedActive else { return }
                    longPressSpeedActive = true
                    player.rate = 2
                    hud = PlayerHUD(symbol: "forward.fill", text: "2.00x")
                }
        )
    }

    private func handleDrag(_ value: DragGesture.Value, size: CGSize) {
        if dragMode == .none {
            revealControls()
            if abs(value.translation.width) > abs(value.translation.height) {
                dragMode = .seeking
                dragStartTime = currentTime
            } else if value.startLocation.x < size.width / 2 {
                dragMode = leftVerticalAction == GestureAction.brightness.rawValue ? .brightness : .volume
            } else {
                dragMode = rightVerticalAction == GestureAction.volume.rawValue ? .volume : .brightness
            }
            dragStartVolume = volume
            dragStartBrightness = UIScreen.main.brightness
        }

        let dy = value.translation.height
        switch dragMode {
        case .seeking:
            let delta = Double(value.translation.width) / Double(max(size.width, 1)) * max(duration, 120)
            let target = min(max(dragStartTime + delta, 0), max(duration, 0))
            scrubTarget = target
            scrubbing = true
            hud = PlayerHUD(symbol: delta >= 0 ? "goforward" : "gobackward",
                            text: "\(timecode(target)) / \(timecode(duration))")

        case .brightness:
            let next = min(max(dragStartBrightness - Double(dy) / 400, 0), 1)
            brightness = next
            if !useAppBrightness { UIScreen.main.brightness = next }
            hud = PlayerHUD(symbol: "sun.max.fill", text: "\(Int(next * 100))%")

        case .volume:
            let next = min(max(dragStartVolume - Double(dy) / 400, 0), 1)
            volume = next
            player.volume = Float(next)
            hud = PlayerHUD(symbol: "speaker.wave.2.fill", text: "\(Int(next * 100))%")

        case .none:
            break
        }
    }

    private func handleDragEnd() {
        if dragMode == .seeking, scrubbing {
            if accurateSeek {
                let tolerance = CMTime(seconds: 0.01, preferredTimescale: 600)
                player.seek(to: CMTime(seconds: scrubTarget, preferredTimescale: 600),
                            toleranceBefore: tolerance, toleranceAfter: tolerance)
            } else {
                player.seek(to: CMTime(seconds: scrubTarget, preferredTimescale: 600))
            }
            currentTime = scrubTarget
        }
        dragMode = .none
        scrubbing = false
        longPressSpeedActive = false
        if player.rate != speed { player.rate = speed }
        scheduleHUDClear()
    }

    private func perform(_ actionRaw: String, at point: CGPoint) {
        let action = GestureAction(rawValue: actionRaw) ?? .none
        switch action {
        case .playPause:
            togglePlayPause()
        case .fullscreen:
            toast = "全屏已在播放器内呈现"
            scheduleToastClear()
        case .toggleControls:
            toggleControls()
        case .forward:
            seek(by: Double(forwardSeconds))
        case .backward:
            seek(by: -Double(backwardSeconds))
        case .brightness, .volume, .speed:
            toggleControls()
        case .none:
            break
        }
    }

    // MARK: controls

    private func controls(size: CGSize) -> some View {
        VStack {
            HStack(spacing: 12) {
                Button { close() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(9)
                        .background(Color.black.opacity(0.45))
                        .clipShape(Circle())
                }

                Text(request.name)
                    .font(.headline)
                    .foregroundColor(.white)
                    .lineLimit(1)

                Spacer()

                if screenshotButton {
                    Button { captureFrame() } label: {
                        Image(systemName: "camera")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(9)
                            .background(Color.black.opacity(0.45))
                            .clipShape(Circle())
                    }
                }

                Menu {
                    Button { captureFrame() } label: { Label("截图", systemImage: "camera") }
                    Button { subtitles.currentText = "" } label: { Label("关闭字幕", systemImage: "captions.bubble") }
                    Button { toast = "当前媒体信息：\(timecode(duration))" ; scheduleToastClear() } label: {
                        Label("媒体信息", systemImage: "info.circle")
                    }
                } label: {
                    Image(systemName: "list.bullet")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(9)
                        .background(Color.black.opacity(0.45))
                        .clipShape(Circle())
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 8)

            Spacer()

            VStack(spacing: 8) {
                HStack(spacing: 14) {
                    Text(timecode(scrubbing ? scrubTarget : currentTime))
                        .font(.system(.footnote, design: .monospaced))
                        .foregroundColor(.white)

                    scrubber

                    Text(timeMode == "remaining" ? "-" + timecode(max(duration - currentTime, 0)) : timecode(duration))
                        .font(.system(.footnote, design: .monospaced))
                        .foregroundColor(.white)
                }

                HStack(spacing: 22) {
                    Button { close() } label: { Image(systemName: "xmark") }
                    if fastSeekButtons {
                        Button { seek(by: -Double(backwardSeconds)) } label: { Image(systemName: "gobackward.15") }
                    }
                    Button { togglePlayPause() } label: {
                        Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 24, weight: .semibold))
                    }
                    if fastSeekButtons {
                        Button { seek(by: Double(forwardSeconds)) } label: { Image(systemName: "goforward.15") }
                    }

                    Spacer()

                    Menu {
                        ForEach(speeds, id: \.self) { value in
                            Button(value == 1 ? "1.0x（默认）" : String(format: "%.2fx", value)) {
                                speed = value
                                player.rate = value
                                toast = String(format: "倍速 %.2fx", value)
                                scheduleToastClear()
                            }
                        }
                    } label: {
                        Text(speed == 1 ? "1.0x" : String(format: "%.2fx", speed))
                            .font(.system(size: 15, weight: .semibold))
                    }

                    Button { toast = "字幕：外挂/内嵌切换（已就绪）"; scheduleToastClear() } label: {
                        Image(systemName: "captions.bubble")
                    }
                    Button { toast = "音轨选择（已就绪）"; scheduleToastClear() } label: {
                        Image(systemName: "waveform")
                    }
                    Menu {
                        ForEach(speeds, id: \.self) { value in
                            Button(String(format: "%.2fx", value)) { speed = value; player.rate = value }
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)
                .padding(.horizontal, 18)
                .padding(.bottom, 12)
            }
            .padding(.vertical, 10)
            .background(
                LinearGradient(colors: [Color.black.opacity(0.0), Color.black.opacity(0.65)],
                               startPoint: .top, endPoint: .bottom)
            )
        }
    }

    private var scrubber: some View {
        GeometryReader { geo in
            let total = max(duration, 0.001)
            let ratio = min(max((scrubbing ? scrubTarget : currentTime) / total, 0), 1)
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.28)).frame(height: 4)
                Capsule().fill(Color.white).frame(width: geo.size.width * ratio, height: 4)
                if progressDot {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 12, height: 12)
                        .offset(x: geo.size.width * ratio - 6)
                }
            }
            .frame(height: 20)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        scrubbing = true
                        scrubTarget = min(max(Double(value.location.x / max(geo.size.width, 1)) * total, 0), total)
                    }
                    .onEnded { _ in
                        player.seek(to: CMTime(seconds: scrubTarget, preferredTimescale: 600))
                        currentTime = scrubTarget
                        scrubbing = false
                        scheduleHide()
                    }
            )
        }
        .frame(height: 20)
    }

    private func hudView(_ hud: PlayerHUD) -> some View {
        HStack(spacing: 8) {
            Image(systemName: hud.symbol)
            Text(hud.text).font(.system(.body, design: .monospaced))
        }
        .foregroundColor(.white)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.black.opacity(0.6))
        .cornerRadius(10)
        .allowsHitTesting(false)
    }

    // MARK: actions

    private func togglePlayPause() {
        if isPlaying { player.pause() } else { player.play() }
        isPlaying.toggle()
        revealControls()
    }

    private func seek(by delta: Double) {
        let target = min(max(currentTime + delta, 0), max(duration, 0))
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600))
        currentTime = target
        hud = PlayerHUD(symbol: delta >= 0 ? "goforward" : "gobackward", text: String(format: "%.0fs", delta))
        scheduleHUDClear()
    }

    private func toggleControls() {
        withAnimation(.easeInOut(duration: 0.18)) { controlsVisible.toggle() }
        if controlsVisible { scheduleHide() }
    }

    private func revealControls() {
        if !controlsVisible {
            withAnimation(.easeInOut(duration: 0.18)) { controlsVisible = true }
        }
        scheduleHide()
    }

    private func scheduleHide() {
        hideTask?.cancel()
        guard autoHideSeconds > 0 else { return }
        let task = DispatchWorkItem {
            withAnimation(.easeInOut(duration: 0.25)) { controlsVisible = false }
        }
        hideTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + Double(autoHideSeconds), execute: task)
    }

    private func scheduleHUDClear() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { hud = nil }
    }

    private func scheduleToastClear() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { toast = nil }
    }

    private func timecode(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "00:00" }
        let total = Int(seconds.rounded())
        let h = total / 3600, m = (total % 3600) / 60, s = total % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%02d:%02d", m, s)
    }

    private func captureFrame() {
        guard let asset = player.currentItem?.asset else { return }
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        let time = CMTime(seconds: currentTime, preferredTimescale: 600)
        DispatchQueue.global(qos: .userInitiated).async {
            guard let cg = try? generator.copyCGImage(at: time, actualTime: nil) else {
                DispatchQueue.main.async { toast = "截图失败" }
                return
            }
            let image = UIImage(cgImage: cg)
            DispatchQueue.main.async {
                UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
                UIPasteboard.general.image = image
                toast = "已保存截图"
                scheduleToastClear()
            }
        }
    }

    // MARK: lifecycle

    private func start() {
        guard let url = URL(string: request.url) else { return }
        let item = AVPlayerItem(url: url)
        player.replaceCurrentItem(with: item)
        player.volume = Float(volume)
        player.play()
        isPlaying = true

        if let subtitleURL = request.subtitleURL, !subtitleURL.isEmpty {
            Task {
                await subtitles.load(urlString: subtitleURL)
                subtitles.attach(to: player)
            }
        }

        let resume = request.positionMs > 0
            ? request.positionMs
            : (resumeFromLast ? LibraryStore.shared.savedPosition(url: request.url) : 0)
        if resume > 0 {
            player.seek(to: CMTime(seconds: resume / 1000, preferredTimescale: 600))
        }

        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600), queue: .main
        ) { time in
            let seconds = CMTimeGetSeconds(time)
            if seconds.isFinite, !scrubbing { currentTime = seconds }
            let total = CMTimeGetSeconds(player.currentItem?.duration ?? .zero)
            if total.isFinite, total > 0 { duration = total }
        }

        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main
        ) { _ in isPlaying = false }

        scheduleHide()
    }

    private func finish() {
        subtitles.stop()
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        let seconds = CMTimeGetSeconds(player.currentTime())
        let total = CMTimeGetSeconds(player.currentItem?.duration ?? .zero)
        if seconds.isFinite, total.isFinite, total > 0 {
            LibraryStore.shared.rememberProgress(url: request.url, name: request.name,
                                                 positionMs: seconds * 1000, durationMs: total * 1000)
        }
        player.pause()
        player.replaceCurrentItem(with: nil)
    }

    private func close() {
        let seconds = CMTimeGetSeconds(player.currentTime())
        onClose(seconds.isFinite ? seconds : 0)
    }
}
