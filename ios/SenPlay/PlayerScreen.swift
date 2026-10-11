import AVKit
import KSPlayer
import SwiftUI
import UIKit

// MARK: - Preference keys shared with Preferences.swift

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
    // 连播与跳过
    static let autoNext = "pref.play.autoNext"
    static let skipIntroSeconds = "pref.play.skipIntroSeconds"
    static let skipOutroSeconds = "pref.play.skipOutroSeconds"
    static let autoNextCountdown = "pref.play.autoNextCountdown"
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

enum AspectMode: String, CaseIterable, Identifiable {
    case fit, fill, stretch
    var id: String { rawValue }
    var title: String {
        switch self {
        case .fit: return "适应屏幕"
        case .fill: return "裁剪全屏"
        case .stretch: return "拉伸铺满"
        }
    }
}

// MARK: - Side-loaded subtitle overlay
//
// The kernel renders embedded tracks (including ASS effects) on its own surface.
// This controller only covers the external-file case, rendered as an overlay so a
// side-loaded SRT works even when the demuxer reports no subtitle stream.

final class SubtitleController: ObservableObject {
    @Published var currentText: String = ""

    private struct Cue {
        let start: Double
        let end: Double
        let text: String
    }

    private var cues: [Cue] = []
    private var timer: Timer?
    private var lastIndex = 0

    func load(urlString: String) async {
        let text: String?
        if urlString.hasPrefix("http://") || urlString.hasPrefix("https://") {
            text = try? await fetch(urlString)
        } else {
            text = try? String(contentsOfFile: urlString, encoding: .utf8)
        }
        guard let raw = text else { return }
        let parsed = SubtitleController.parse(raw)
        await MainActor.run {
            self.cues = parsed
            self.lastIndex = 0
        }
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
            guard let start = SubtitleController.seconds(parts[0]),
                  let end = SubtitleController.seconds(parts[1]) else { continue }
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

    /// Advances the overlay from the kernel's own time callback, so there is no
    /// second polling timer fighting the player.
    func tick(at seconds: Double) {
        guard !cues.isEmpty, seconds.isFinite else { return }
        if lastIndex < cues.count, seconds > cues[lastIndex].end {
            while lastIndex < cues.count, seconds > cues[lastIndex].end { lastIndex += 1 }
        }
        while lastIndex > 0, seconds < cues[lastIndex - 1].start { lastIndex -= 1 }
        let text: String
        if lastIndex < cues.count, seconds >= cues[lastIndex].start, seconds <= cues[lastIndex].end {
            text = cues[lastIndex].text
        } else {
            text = ""
        }
        if text != currentText {
            currentText = text
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        cues = []
        currentText = ""
    }
}

// MARK: - Kernel bridge
//
// Drives KSPlayerLayer directly so the control bar and gesture layer below stay
// ours. KSPlayer 2.3.4 ships its own tool bar; we never mount it.

final class KernelPlayer: NSObject, ObservableObject {
    @Published var state: KSPlayerState = .initialized
    @Published var currentTime: TimeInterval = 0
    @Published var totalTime: TimeInterval = 0
    @Published var bufferingProgress: Int = 0
    @Published var mediaTitle: String?
    @Published var dynamicInfo: String?

    let layer: KSPlayerLayer

    var onTime: ((TimeInterval, TimeInterval) -> Void)?
    var onFinish: (() -> Void)?

    init(url: URL, options: KSOptions) {
        layer = KSPlayerLayer(url: url, isAutoPlay: true, options: options, delegate: nil)
        super.init()
        layer.delegate = self
    }

    var renderView: UIView? { layer.player.view }
    var isPlaying: Bool { layer.player.isPlaying }

    func play() { layer.play() }
    func pause() { layer.pause() }

    func togglePlay() {
        isPlaying ? pause() : play()
    }

    func seek(to time: TimeInterval) {
        layer.seek(time: time, autoPlay: true) { _ in }
    }

    func setRate(_ rate: Float) { layer.player.playbackRate = rate }
    func setVolume(_ volume: Float) { layer.player.playbackVolume = volume }
    func setMuted(_ muted: Bool) { layer.player.isMuted = muted }
    /// Video out to another screen; the session keeps running on the receiver.
    func setExternalPlayback(_ on: Bool) {
        layer.player.allowsExternalPlayback = on
        layer.player.usesExternalPlaybackWhileExternalScreenIsActive = on
    }

    var isExternalPlaybackActive: Bool { layer.player.isExternalPlaybackActive }

    /// KSPlayerLayer already owns the PiP lifecycle, so flipping the flag is the
    /// whole integration.
    func setPictureInPicture(_ on: Bool) {
        layer.isPipActive = on
    }

    var isPictureInPictureActive: Bool { layer.isPipActive }

    func setAspect(_ mode: AspectMode) {
        switch mode {
        case .fit: layer.player.contentMode = .scaleAspectFit
        case .fill: layer.player.contentMode = .scaleAspectFill
        case .stretch: layer.player.contentMode = .scaleToFill
        }
    }

    func audioTracks() -> [MediaPlayerTrack] { layer.player.tracks(mediaType: .audio) }
    func subtitleTracks() -> [MediaPlayerTrack] { layer.player.tracks(mediaType: .subtitle) }
    func videoTracks() -> [MediaPlayerTrack] { layer.player.tracks(mediaType: .video) }

    func select(_ track: some MediaPlayerTrack) {
        layer.player.select(track: track)
    }

    func rateOptions() -> [Float] { [0.5, 0.75, 1, 1.25, 1.5, 2, 3] }

    func capture() -> UIImage? {
        guard let view = layer.player.view else { return nil }
        let renderer = UIGraphicsImageRenderer(bounds: view.bounds)
        return renderer.image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: false)
        }
    }

    func shutdown() {
        layer.pause()
        layer.delegate = nil
    }
}

extension KernelPlayer: KSPlayerLayerDelegate {
    func player(layer: KSPlayerLayer, state: KSPlayerState) {
        self.state = state
        bufferingProgress = layer.bufferingProgress
        if state == .bufferFinished || state == .buffering {
            mediaTitle = layer.player.dynamicInfo?.metadata["title"]
            if let info = layer.player.dynamicInfo {
                dynamicInfo = String(format: "%.1f fps · %d kbps",
                                     info.displayFPS, info.videoBitrate / 1000)
            }
        }
    }

    func player(layer: KSPlayerLayer, currentTime: TimeInterval, totalTime: TimeInterval) {
        self.currentTime = currentTime
        if totalTime > 0 { self.totalTime = totalTime }
        onTime?(currentTime, totalTime)
    }

    func player(layer: KSPlayerLayer, finish error: Error?) {
        if error != nil {
            state = .error
        } else {
            state = .playedToTheEnd
        }
        onFinish?()
    }

    func player(layer: KSPlayerLayer, bufferedCount: Int, consumeTime: TimeInterval) {}
}

/// Hosts the kernel's render surface. Ownership of the control surface stays with
/// the SwiftUI layers above, so this bridge does nothing but forward the view.
struct KernelSurface: UIViewRepresentable {
    let player: KernelPlayer

    func makeUIView(context: Context) -> UIView {
        player.renderView ?? UIView()
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        if let live = player.renderView, live !== uiView, uiView.subviews.isEmpty {
            uiView.addSubview(live)
            live.frame = uiView.bounds
            live.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        }
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

private struct TrackRow: Identifiable {
    let id: Int
    let title: String
    let subtitle: String
    let selected: Bool
    let name: String
}

struct PlayerScreen: View {
    let request: PlayRequest
    /// Fires when the queue advances; the host swaps the presented request.
    var onPlayNext: ((PlayRequest) -> Void)? = nil
    var onClose: (Double) -> Void = { _ in }

    @AppStorage(PrefKey.resumeFromLast) private var resumeFromLast = true
    @AppStorage(PrefKey.accurateSeek) private var accurateSeek = false
    @AppStorage(PrefKey.forwardSeconds) private var forwardSeconds = 15
    @AppStorage(PrefKey.backwardSeconds) private var backwardSeconds = 15
    @AppStorage(PrefKey.progressDot) private var progressDot = true
    @AppStorage(PrefKey.timeMode) private var timeMode = "total"
    @AppStorage(PrefKey.autoHideSeconds) private var autoHideSeconds = 3
    @AppStorage(PrefKey.fastSeekButtons) private var fastSeekButtons = false
    @AppStorage(PrefKey.switchMediaButton) private var switchMediaButton = true
    @AppStorage(PrefKey.screenshotButton) private var screenshotButton = true
    @AppStorage(PrefKey.doubleLeftAction) private var doubleLeftAction = GestureAction.backward.rawValue
    @AppStorage(PrefKey.doubleRightAction) private var doubleRightAction = GestureAction.forward.rawValue
    @AppStorage(PrefKey.doubleCenterAction) private var doubleCenterAction = GestureAction.playPause.rawValue
    @AppStorage(PrefKey.leftVerticalAction) private var leftVerticalAction = GestureAction.brightness.rawValue
    @AppStorage(PrefKey.rightVerticalAction) private var rightVerticalAction = GestureAction.volume.rawValue
    @AppStorage(PrefKey.rememberZoomSize) private var rememberZoomSize = true
    @AppStorage(PrefKey.useAppBrightness) private var useAppBrightness = false
    @AppStorage(PrefKey.subtitleScale) private var subtitleScale = 1.0
    @AppStorage(PrefKey.subtitleMask) private var subtitleMask = false
    @AppStorage(PrefKey.jpegQuality) private var jpegQuality = 0.8
    @AppStorage(PrefKey.preferHardwareDecode) private var preferHardwareDecode = true
    @AppStorage(PrefKey.rememberAudioTrack) private var rememberAudioTrack = true
    @AppStorage(PrefKey.rememberSubtitleTrack) private var rememberSubtitleTrack = true
    @AppStorage(PrefKey.autoNext) private var autoNext = true
    @AppStorage(PrefKey.skipIntroSeconds) private var skipIntroSeconds = 0
    @AppStorage(PrefKey.skipOutroSeconds) private var skipOutroSeconds = 0
    @AppStorage(PrefKey.autoNextCountdown) private var autoNextCountdown = 5

    @StateObject private var subtitles = SubtitleController()
    @State private var kernel: KernelPlayer?

    @State private var controlsVisible = true
    @State private var isPlaying = true
    @State private var currentTime: Double = 0
    @State private var duration: Double = 0
    @State private var scrubbing = false
    @State private var scrubTarget: Double = 0
    @State private var speed: Float = 1
    @State private var zoom: CGFloat = 1
    @State private var volume: Double = 1
    @State private var brightness: Double = 0.5
    @State private var aspect: AspectMode = .fit
    @State private var dragMode: DragMode = .none
    @State private var dragStartTime: Double = 0
    @State private var dragStartVolume: Double = 1
    @State private var dragStartBrightness: Double = 0.5
    @State private var longPressSpeedActive = false
    @State private var hud: PlayerHUD?
    @State private var toast: String?
    @State private var hideTask: DispatchWorkItem?
    @State private var activePanel: PanelKind?
    @State private var didSkipIntro = false
    @State private var didSkipOutro = false
    @State private var upNext: QueueItem?
    @State private var countdown: Int = 0

    private let speeds: [Float] = [0.5, 0.75, 1, 1.25, 1.5, 2, 3]

    private enum PanelKind: String, Identifiable {
        case subtitle, audio, aspect, more
        var id: String { rawValue }
        var title: String {
            switch self {
            case .subtitle: return "字幕"
            case .audio: return "音轨"
            case .aspect: return "画面比例"
            case .more: return "更多"
            }
        }
    }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.black.ignoresSafeArea()

                if let kernel {
                    KernelSurface(player: kernel)
                        .ignoresSafeArea()
                        .scaleEffect(zoom)
                }

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
                            .padding(.bottom, subtitleMask ? 110 : 78)
                    }
                    .allowsHitTesting(false)
                }

                gestureLayer(size: geo.size)

                if let hud { hudView(hud) }

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
                    controls()
                        .transition(.opacity)
                }

                if let panel = activePanel {
                    panelOverlay(panel)
                }

                if let next = upNext {
                    upNextCard(next)
                }
            }
        }
        .statusBar(hidden: true)
        .onAppear(perform: start)
        .onDisappear(perform: finish)
    }

    // MARK: gestures

    private func gestureLayer(size: CGSize) -> some View {
        HStack(spacing: 0) {
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { perform(doubleLeftAction) }
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { perform(doubleCenterAction) }
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { perform(doubleRightAction) }
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
                    kernel?.setRate(2)
                    hud = PlayerHUD(symbol: "forward.fill", text: "2.00x")
                }
        )
        .onTapGesture { toggleControls() }
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
            kernel?.setVolume(Float(next))
            hud = PlayerHUD(symbol: "speaker.wave.2.fill", text: "\(Int(next * 100))%")

        case .none:
            break
        }
    }

    private func handleDragEnd() {
        if dragMode == .seeking, scrubbing {
            kernel?.seek(to: scrubTarget)
            currentTime = scrubTarget
        }
        dragMode = .none
        scrubbing = false
        longPressSpeedActive = false
        kernel?.setRate(speed)
        scheduleHUDClear()
    }

    private func perform(_ actionRaw: String) {
        let action = GestureAction(rawValue: actionRaw) ?? .none
        switch action {
        case .playPause: togglePlayPause()
        case .fullscreen:
            toast = "全屏由窗口承载"
            scheduleToastClear()
        case .toggleControls: toggleControls()
        case .forward: seek(by: Double(forwardSeconds))
        case .backward: seek(by: -Double(backwardSeconds))
        case .brightness, .volume, .speed: toggleControls()
        case .none: break
        }
    }

    // MARK: controls

    private func controls() -> some View {
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

                VStack(alignment: .leading, spacing: 2) {
                    Text(request.name)
                        .font(.headline)
                        .foregroundColor(.white)
                        .lineLimit(1)
                    if let info = kernel?.dynamicInfo {
                        Text(info)
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                    }
                }

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

                Button { activePanel = .more } label: {
                    Image(systemName: "ellipsis")
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

                HStack(spacing: 20) {
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
                    if switchMediaButton {
                        Button { toast = "切换媒体"; scheduleToastClear() } label: {
                            Image(systemName: "rectangle.stack")
                        }
                    }

                    Spacer()

                    Menu {
                        ForEach(speeds, id: \.self) { value in
                            Button(value == 1 ? "1.0x（默认）" : String(format: "%.2fx", value)) {
                                speed = value
                                kernel?.setRate(value)
                                toast = String(format: "倍速 %.2fx", value)
                                scheduleToastClear()
                            }
                        }
                    } label: {
                        Text(speed == 1 ? "1.0x" : String(format: "%.2fx", speed))
                            .font(.system(size: 15, weight: .semibold))
                    }

                    Button { activePanel = .subtitle } label: { Image(systemName: "captions.bubble") }
                    Button { activePanel = .audio } label: { Image(systemName: "waveform") }
                    Button { activePanel = .aspect } label: { Image(systemName: "aspectratio") }
                }
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)
                .buttonStyle(.plain)
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
                        kernel?.seek(to: scrubTarget)
                        currentTime = scrubTarget
                        scrubbing = false
                        scheduleHide()
                    }
            )
        }
        .frame(height: 20)
    }

    private func upNextCard(_ next: QueueItem) -> some View {
        VStack {
            Spacer()
            HStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("即将播放").font(.caption2).foregroundColor(.white.opacity(0.65))
                    Text(next.name).font(.subheadline.weight(.medium))
                        .foregroundColor(.white).lineLimit(1)
                }
                Spacer()
                Text(countdown > 0 ? "\(countdown)" : "▶")
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                Button("立即播放") { advance() }
                    .font(.footnote.weight(.semibold))
                Button("取消") {
                    upNext = nil
                    countdown = 0
                }
                .font(.footnote)
                .foregroundColor(.white.opacity(0.7))
            }
            .padding(14)
            .background(Color.black.opacity(0.78))
            .cornerRadius(12)
            .padding(.horizontal, 20)
            .padding(.bottom, 120)
        }
    }

    private func panelOverlay(_ panel: PanelKind) -> some View {
        ZStack {
            Color.black.opacity(0.001)
                .ignoresSafeArea()
                .onTapGesture { activePanel = nil }

            VStack(spacing: 0) {
                HStack {
                    Text(panel.title).font(.headline).foregroundColor(.white)
                    Spacer()
                    Button { activePanel = nil } label: {
                        Image(systemName: "xmark").foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                Divider().background(Color.white.opacity(0.2))

                ScrollView {
                    VStack(spacing: 0) {
                        switch panel {
                        case .subtitle:
                            ForEach(subtitleRows()) { row in
                                trackButton(row) { selectSubtitle(byName: row.name) }
                            }
                            if subtitleRows().isEmpty {
                                Text("未检测到字幕轨").font(.footnote).foregroundColor(.secondary).padding()
                            }
                        case .audio:
                            ForEach(audioRows()) { row in
                                trackButton(row) { selectAudio(byName: row.name) }
                            }
                            if audioRows().isEmpty {
                                Text("未检测到音轨").font(.footnote).foregroundColor(.secondary).padding()
                            }
                        case .aspect:
                            ForEach(AspectMode.allCases) { mode in
                                trackButton(TrackRow(id: mode.hashValue, title: mode.title,
                                                     subtitle: "", selected: aspect == mode, name: mode.rawValue)) {
                                    aspect = mode
                                    kernel?.setAspect(mode)
                                    activePanel = nil
                                }
                            }
                        case .more:
                            outputRow(title: "画中画",
                                      detail: kernel?.isPictureInPictureActive == true ? "进行中" : "关闭",
                                      active: kernel?.isPictureInPictureActive == true) {
                                let next = !(kernel?.isPictureInPictureActive ?? false)
                                kernel?.setPictureInPicture(next)
                                toast = next ? "已进入画中画" : "已退出画中画"
                                scheduleToastClear()
                            }
                            outputRow(title: "投屏",
                                      detail: kernel?.isExternalPlaybackActive == true ? "已连接" : "未连接",
                                      active: kernel?.isExternalPlaybackActive == true) {
                                kernel?.setExternalPlayback(true)
                                toast = "已开启外部播放，请在控制中心选择设备"
                                scheduleToastClear()
                            }
                            ForEach(infoRows().indices, id: \.self) { index in
                                let line = infoRows()[index]
                                HStack {
                                    Text(line.0).foregroundColor(.white.opacity(0.75))
                                    Spacer()
                                    Text(line.1).foregroundColor(.white)
                                }
                                .font(.footnote)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                            }
                        }
                    }
                }
                .frame(maxHeight: 320)
            }
            .background(Color.black.opacity(0.92))
            .cornerRadius(14)
            .padding(.horizontal, 28)
        }
    }

    private func outputRow(title: String, detail: String, active: Bool,
                           action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundColor(.white).font(.subheadline)
                    Text(detail).foregroundColor(.white.opacity(0.6)).font(.caption2)
                }
                Spacer()
                Image(systemName: active ? "checkmark" : "chevron.right")
                    .foregroundColor(active ? .accentColor : .white.opacity(0.5))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func trackButton(_ row: TrackRow, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.title).foregroundColor(.white).font(.subheadline)
                    if !row.subtitle.isEmpty {
                        Text(row.subtitle).foregroundColor(.white.opacity(0.6)).font(.caption2)
                    }
                }
                Spacer()
                if row.selected {
                    Image(systemName: "checkmark").foregroundColor(.accentColor)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func trackRows(_ tracks: [MediaPlayerTrack], fallback: String) -> [TrackRow] {
        tracks.enumerated().map { index, track in
            let detail = [track.languageCode, "\(track.bitRate / 1000) kbps"]
                .compactMap { $0 }
                .filter { !$0.isEmpty }
                .joined(separator: " · ")
            return TrackRow(id: Int(track.trackID),
                            title: track.name.isEmpty ? "\(fallback) \(index + 1)" : track.name,
                            subtitle: detail,
                            selected: track.isEnabled,
                            name: track.name)
        }
    }

    private func audioRows() -> [TrackRow] {
        guard let kernel else { return [] }
        return trackRows(kernel.audioTracks(), fallback: "音轨")
    }

    private func subtitleRows() -> [TrackRow] {
        guard let kernel else { return [] }
        return trackRows(kernel.subtitleTracks(), fallback: "字幕")
    }

    private func selectAudio(byName name: String) {
        guard let kernel else { return }
        if let track = kernel.audioTracks().first(where: { $0.name == name }) {
            kernel.select(track)
            toast = "已切换音轨：\(name)"
            scheduleToastClear()
        }
        activePanel = nil
    }

    private func selectSubtitle(byName name: String) {
        guard let kernel else { return }
        if let track = kernel.subtitleTracks().first(where: { $0.name == name }) {
            kernel.select(track)
            toast = "已切换字幕：\(name)"
            scheduleToastClear()
        }
        activePanel = nil
    }

    private func infoRows() -> [(String, String)] {
        guard let kernel else { return [] }
        let info = kernel.layer.player.dynamicInfo
        let video = kernel.videoTracks().first
        let audio = kernel.audioTracks().first
        var rows: [(String, String)] = [
            ("状态", String(describing: kernel.state)),
            ("时长", timecode(kernel.totalTime)),
            ("解码", preferHardwareDecode ? "硬件 (HW/GPU)" : "软件"),
            ("缓冲", "\(kernel.bufferingProgress)%"),
        ]
        if let video {
            if let fd = video.formatDescription {
                let dims = CMVideoFormatDescriptionGetDimensions(fd)
                rows.append(("分辨率", "\(dims.width)×\(dims.height)"))
                rows.append(("视频编码", fourCC(CMFormatDescriptionGetMediaSubType(fd))))
            }
            rows.append(("帧率", String(format: "%.2f", video.nominalFrameRate)))
            rows.append(("位深", "\(video.bitDepth)"))
            if video.dovi != nil {
                rows.append(("杜比视界", "已检测到 RPU"))
            }
        }
        if let audio {
            if let fd = audio.formatDescription {
                rows.append(("音频编码", fourCC(CMFormatDescriptionGetMediaSubType(fd))))
            }
            rows.append(("音频码率", "\(audio.bitRate / 1000) kbps"))
        }
        if let info {
            rows.append(("显示帧率", String(format: "%.1f", info.displayFPS)))
            rows.append(("音画同步差", String(format: "%.1f ms", info.audioVideoSyncDiff)))
            rows.append(("丢帧", "\(info.droppedVideoFrameCount + UInt32(info.droppedVideoPacketCount))"))
            rows.append(("视频码率", "\(info.videoBitrate / 1000) kbps"))
            rows.append(("音频码率", "\(info.audioBitrate / 1000) kbps"))
            rows.append(("已读数据", ByteCountFormatter.string(fromByteCount: info.bytesRead, countStyle: .file)))
        }
        rows.append(("视频轨", String(kernel.videoTracks().count)))
        rows.append(("音频轨", String(kernel.audioTracks().count)))
        rows.append(("字幕轨", String(kernel.subtitleTracks().count)))
        return rows
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

    /// Intro/outro handling. The outro branch only marks the position; advancing is
    /// the countdown's job, so a viewer can cancel it.
    private func applySkipRules(current: Double, total: Double) {
        if skipIntroSeconds > 0, !didSkipIntro, current > 1.0, current < Double(skipIntroSeconds) {
            didSkipIntro = true
            kernel?.seek(to: Double(skipIntroSeconds))
            toast = "已跳过片头 \(skipIntroSeconds) 秒"
            scheduleToastClear()
        }
        if skipOutroSeconds > 0, !didSkipOutro, total > 0,
           total - current <= Double(skipOutroSeconds) {
            didSkipOutro = true
            if autoNext {
                beginCountdown()
            } else {
                toast = "已到片尾"
                scheduleToastClear()
            }
        }
    }

    private func beginCountdown() {
        let queue = request.queue
        guard autoNext,
              let index = queue.firstIndex(where: { $0.url == request.url }),
              index + 1 < queue.count else { return }
        upNext = queue[index + 1]
        countdown = autoNextCountdown
        guard countdown > 0 else { advance() ; return }
        Task { @MainActor in
            while countdown > 0, upNext != nil {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                countdown -= 1
            }
            if upNext != nil { advance() }
        }
    }

    private func advance() {
        guard let next = upNext else { return }
        upNext = nil
        countdown = 0
        let seeded = PlayRequest(
            url: next.url,
            name: next.name,
            userAgent: request.userAgent,
            positionMs: LibraryStore.shared.savedPosition(url: next.url),
            queue: request.queue
        )
        onPlayNext?(seeded)
    }

    private func togglePlayPause() {
        kernel?.togglePlay()
        isPlaying.toggle()
        revealControls()
    }

    private func seek(by delta: Double) {
        guard let kernel else { return }
        let target = min(max(currentTime + delta, 0), max(duration, 0))
        kernel.seek(to: target)
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

    /// Four-character code from a CoreMedia subtype, e.g. `avc1`, `hvc1`, `mp4a`.
    private func fourCC(_ code: FourCharCode) -> String {
        let bytes = [UInt8((code >> 24) & 0xFF), UInt8((code >> 16) & 0xFF),
                     UInt8((code >> 8) & 0xFF), UInt8(code & 0xFF)]
        let text = String(bytes: bytes, encoding: .ascii) ?? ""
        return text.trimmingCharacters(in: .whitespaces).isEmpty ? "\(code)" : text
    }

    private func timecode(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "00:00" }
        let total = Int(seconds.rounded())
        let h = total / 3600, m = (total % 3600) / 60, s = total % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%02d:%02d", m, s)
    }

    private func captureFrame() {
        guard let image = kernel?.capture() else {
            toast = "截图失败"
            scheduleToastClear()
            return
        }
        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
        UIPasteboard.general.image = image
        toast = "已保存截图"
        scheduleToastClear()
    }

    // MARK: lifecycle

    private func start() {
        guard let url = URL(string: request.url) else { return }

        let options = KSOptions()
        options.isAccurateSeek = accurateSeek
        options.hardwareDecode = preferHardwareDecode
        options.userAgent = request.userAgent ?? "SenPlay/1.1 (iOS)"
        options.registerRemoteControll = true
        options.autoSelectEmbedSubtitle = true
        options.isSeekedAutoPlay = true

        let player = KernelPlayer(url: url, options: options)
        player.setExternalPlayback(true)
        player.onTime = { [self] current, total in
            if !scrubbing { currentTime = current }
            subtitles.tick(at: current)
            applySkipRules(current: current, total: total)
            NowPlaying.shared.update(time: current, duration: total, rate: speed)
        }
        player.onFinish = {
            isPlaying = false
            if autoNext { beginCountdown() }
        }
        kernel = player
        isPlaying = true
        volume = Double(player.layer.player.playbackVolume)
        brightness = UIScreen.main.brightness

        let resume = request.positionMs > 0
            ? request.positionMs
            : (resumeFromLast ? LibraryStore.shared.savedPosition(url: request.url) : 0)
        if resume > 0 {
            player.seek(to: resume / 1000)
        }

        if let subtitleURL = request.subtitleURL, !subtitleURL.isEmpty {
            Task { await subtitles.load(urlString: subtitleURL) }
        }

        NowPlaying.shared.activate(
            title: request.name,
            duration: 0,
            onPlay: { player.play() },
            onPause: { player.pause() },
            onToggle: { player.togglePlay() },
            onSeek: { target in player.seek(to: target) }
        )

        scheduleHide()
    }

    private func finish() {
        subtitles.stop()
        NowPlaying.shared.deactivate()
        guard let kernel else { return }
        let seconds = kernel.currentTime
        let total = kernel.totalTime
        if seconds.isFinite, total.isFinite, total > 0 {
            LibraryStore.shared.rememberProgress(url: request.url, name: request.name,
                                                 positionMs: seconds * 1000, durationMs: total * 1000)
        }
        kernel.shutdown()
        self.kernel = nil
    }

    private func close() {
        let seconds = kernel?.currentTime ?? 0
        onClose(seconds.isFinite ? seconds : 0)
    }
}
