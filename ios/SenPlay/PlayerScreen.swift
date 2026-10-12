import AVKit
import CoreMedia
import KSPlayer
import Photos
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
    /// The last pinch scale, kept so "记住缩放大小" has somewhere to remember it.
    static let zoom = "pref.gesture.zoom"
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
    /// Set from the player screen before the layer starts.
    var preferredAudioLanguage = ""
    var preferredSubtitleLanguage = ""
    private var languagesApplied = false
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

    /// Live statistics the kernel keeps for its own OSD. Polled rather than
    /// observed: the object publishes on every frame and the overlay only needs a
    /// readable refresh.
    func statistics() -> OSDStats? {
        guard let info = layer.player.dynamicInfo else { return nil }
        return OSDStats(
            videoBitrate: info.videoBitrate,
            audioBitrate: info.audioBitrate,
            fps: info.displayFPS,
            dropped: info.droppedVideoFrameCount + info.droppedVideoPacketCount,
            bytesRead: info.bytesRead,
            syncDiff: info.audioVideoSyncDiff
        )
    }

    /// Chapter markers reported by the demuxer, used for the jump list.
    func chapters() -> [Chapter] {
        layer.player.chapters
    }

    func audioTracks() -> [MediaPlayerTrack] { layer.player.tracks(mediaType: .audio) }
    func subtitleTracks() -> [MediaPlayerTrack] { layer.player.tracks(mediaType: .subtitle) }
    func videoTracks() -> [MediaPlayerTrack] { layer.player.tracks(mediaType: .video) }

    func select(_ track: some MediaPlayerTrack) {
        layer.player.select(track: track)
    }

    /// Picks the audio and subtitle track whose language matches the viewer's
    /// preference. Language codes arrive variously as "zh", "zh-Hans" or "chi", so
    /// the comparison is on the prefix and the first match wins — the same tolerance
    /// a person would apply reading the track list.
    ///
    /// Called once the player is ready, because tracks do not exist before that.
    func applyPreferredLanguages(audio: String, subtitle: String) {
        if !audio.isEmpty, let track = match(audio, in: audioTracks()) {
            select(track)
        }
        if !subtitle.isEmpty, let track = match(subtitle, in: subtitleTracks()) {
            select(track)
        }
    }

    private func match(_ preferred: String, in tracks: [MediaPlayerTrack]) -> MediaPlayerTrack? {
        let wanted = preferred.lowercased()
        return tracks.first { ($0.languageCode ?? "").lowercased().hasPrefix(wanted) }
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
        // Once only: the viewer can still change tracks by hand afterwards, and a
        // second application would undo that.
        if state == .readyToPlay, !languagesApplied {
            languagesApplied = true
            applyPreferredLanguages(audio: preferredAudioLanguage,
                                    subtitle: preferredSubtitleLanguage)
        }
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
/// Routes hardware keyboard events to the player. The reference app binds
/// space / arrows / +/- / 0 / F / I; the same set is honoured here so an iPad with a
/// keyboard behaves like the desktop build.
final class KeyCommandRouter {
    var handler: ((String) -> Void)?

    func dispatch(_ key: String) {
        handler?(key)
    }
}

final class KeyCommandHost: UIViewController {
    var router: KeyCommandRouter?

    override var canBecomeFirstResponder: Bool { true }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        becomeFirstResponder()
    }

    override var keyCommands: [UIKeyCommand]? {
        func command(_ input: String, _ flags: UIKeyModifierFlags = [], _ name: String) -> UIKeyCommand {
            let command = UIKeyCommand(input: input, modifierFlags: flags, action: #selector(handle(_:)))
            command.wantsPriorityOverSystemBehavior = true
            command.accessibilityLabel = name
            return command
        }
        return [
            command(" ", [], "toggle"),
            command(UIKeyCommand.inputLeftArrow, [], "back"),
            command(UIKeyCommand.inputRightArrow, [], "forward"),
            command(UIKeyCommand.inputUpArrow, [], "volumeUp"),
            command(UIKeyCommand.inputDownArrow, [], "volumeDown"),
            command("=", [], "rateUp"),
            command("+", [.shift], "rateUp"),
            command("-", [], "rateDown"),
            command("0", [], "reset"),
            command("f", [], "fullscreen"),
            command("i", [], "info"),
        ]
    }

    @objc private func handle(_ sender: UIKeyCommand) {
        router?.dispatch(sender.accessibilityLabel ?? "")
    }
}

struct KeyCommandSurface: UIViewControllerRepresentable {
    let router: KeyCommandRouter

    func makeUIViewController(context: Context) -> KeyCommandHost {
        let host = KeyCommandHost()
        host.router = router
        return host
    }

    func updateUIViewController(_ uiViewController: KeyCommandHost, context: Context) {
        uiViewController.router = router
    }
}

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
    @AppStorage(PrefKey.zoom) private var rememberedZoom = 1.0
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
    @AppStorage(PrefKey.danmakuEnabled) private var danmakuEnabled = true
    @AppStorage(PrefKey.danmakuSize) private var danmakuSize = 25.0
    @AppStorage(PrefKey.danmakuSpeed) private var danmakuSpeed = 0.5
    @AppStorage(PrefKey.danmakuArea) private var danmakuArea = 0.15
    @AppStorage(PrefKey.danmakuSkipShort) private var danmakuSkipShort = true
    @AppStorage(PrefKey.danmakuFont) private var danmakuFontName = "SF Pro"
    @AppStorage("pref.danmaku.convert") private var danmakuConvert = "none"
    @AppStorage("pref.ui.showOSD") private var showOSD = false
    @AppStorage("pref.play.rememberSpeed") private var rememberSpeed = true
    @AppStorage("pref.play.lastSpeed") private var lastSpeed = 1.0
    @AppStorage(PrefKey.preloadMode) private var preloadMode = "none"
    @AppStorage(PrefKey.audioEnhance) private var audioEnhance = true
    @AppStorage(PrefKey.multiChannel) private var multiChannel = false
    @AppStorage("pref.video.upscaling") private var upscaling = false
    @AppStorage("pref.video.hdrMetadata") private var hdrMetadata = true

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
    @AppStorage(PrefKey.longPressSpeed) private var longPressSpeedRate = 2.0
    @AppStorage(PrefKey.controlBarStyle) private var controlBarStyle = "bottom"
    @AppStorage("pref.audio.firstLanguage") private var preferredAudioLanguage = ""
    @AppStorage("pref.subtitle.firstLanguage") private var preferredSubtitleLanguage = ""
    @AppStorage("pref.danmaku.keywords") private var danmakuKeywordText = ""
    @AppStorage("pref.audio.backgroundAudio") private var backgroundAudio = false
    @AppStorage("pref.danmaku.bottomToFloat") private var bottomToFloat = false
    /// Declared here as well as in the settings screen: a preference is read where it
    /// is used, and the two views do not share storage declarations.
    @AppStorage(PrefKey.screenshotToClipboard) private var screenshotToClipboard = true
    @AppStorage(PrefKey.clickAction) private var clickAction = GestureAction.playPause.rawValue
    @AppStorage(PrefKey.pauseAutoLock) private var pauseAutoLock = true
    @AppStorage(PrefKey.useSystemProxy) private var useSystemProxy = true
    @AppStorage(PrefKey.screenshotFormat) private var screenshotFormat = "jpg"
    @AppStorage(PrefKey.switchBlackout) private var switchBlackout = true
    @AppStorage(PrefKey.showCloudThrottleTip) private var showCloudThrottleTip = true
    @AppStorage(PrefKey.showStartTitle) private var showStartTitle = true
    @AppStorage(PrefKey.showSkipTip) private var showSkipTip = true
    @AppStorage("pref.video.decodeThreads") private var decodeThreads = 8
    @AppStorage("pref.gesture.longPressLeftSpeed") private var longPressLeftSpeed = 0.5
    @AppStorage("pref.gesture.longPressRightSpeed") private var longPressRightSpeed = 2.0
    @State private var hud: PlayerHUD?
    @State private var toast: String?
    @State private var hideTask: DispatchWorkItem?
    @State private var activePanel: PanelKind?
    @State private var didSkipIntro = false
    @State private var didSkipOutro = false
    @State private var upNext: QueueItem?
    @State private var danmakuItems: [DanmakuItem] = []
    @State private var keyRouter = KeyCommandRouter()
    @State private var stats: OSDStats?
    @State private var countdown: Int = 0
    /// "切换视频时黑屏": covers the hand-off to the next item so the last frame of
    /// the outgoing one is not what the viewer sees while the host swaps screens.
    @State private var blackout = false

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

                if showOSD, let stats, !stats.isEmpty {
                    VStack {
                        osdOverlay(stats)
                        Spacer()
                    }
                    .padding(.top, 54)
                    .padding(.horizontal, 16)
                }

                KeyCommandSurface(router: keyRouter)
                    .frame(width: 0, height: 0)

                if danmakuEnabled, !danmakuItems.isEmpty {
                    DanmakuOverlay(items: danmakuItems,
                                   currentTime: currentTime,
                                   fontSize: danmakuSize,
                                   speed: danmakuSpeed,
                                   area: danmakuArea,
                                   blockedKeywords: blockedKeywords,
                                   bottomFloats: bottomToFloat,
                                   fontName: danmakuFontName,
                                   convert: danmakuConvert)
                        .ignoresSafeArea()
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
                        // "控制栏风格": bottom pins the chrome to the edges,
                        // floating insets it with a scrim so it reads as a layer
                        // over the video rather than part of the frame.
                        .padding(.horizontal, floatingControls ? 18 : 0)
                        .padding(.vertical, floatingControls ? 12 : 0)
                        .background(
                            floatingControls
                                ? Color.black.opacity(0.35)
                                : Color.clear
                        )
                        .transition(.opacity)
                }

                if let panel = activePanel {
                    panelOverlay(panel)
                }

                if let next = upNext {
                    upNextCard(next)
                }

                if blackout {
                    Color.black.ignoresSafeArea()
                }
            }
        }
        .statusBar(hidden: true)
        .onReceive(NotificationCenter.default.publisher(
            for: UIApplication.didEnterBackgroundNotification
        )) { _ in
            pauseIfBackgroundAudioDisabled()
        }
        .onAppear(perform: start)
        .onDisappear(perform: finish)
        .onChange(of: isPlaying) { _ in updateIdleTimer() }
        // Polled once a second: the kernel publishes on every frame, and the overlay
        // only needs to stay readable.
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in
            guard showOSD else {
                if stats != nil { stats = nil }
                return
            }
            stats = kernel?.statistics()
        }
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
                    // "记住缩放大小": the scale is part of how someone set the picture
                    // up, so it comes back with the next file unless the switch says
                    // otherwise.
                    if rememberZoomSize { rememberedZoom = Double(zoom) }
                    toast = String(format: "缩放 %.2fx", zoom)
                    scheduleToastClear()
                }
        )
        // Hold to speed up, release to go back. `LongPressGesture` on its own only
        // reports the moment the hold is recognised — nothing fires when the finger
        // lifts, so the rate stayed at the hold value until the next drag ended.
        .onLongPressGesture(minimumDuration: 0.5, maximumDistance: 60) {
            guard !longPressSpeedActive else { return }
            longPressSpeedActive = true
            // The screen has no left or right, so it uses the general hold rate. The
            // arrow keys do have sides and use their own.
            kernel?.setRate(Float(longPressSpeedRate))
            hud = PlayerHUD(symbol: "forward.fill",
                            text: String(format: "%.2fx", longPressSpeedRate))
        } onPressingChanged: { pressing in
            guard !pressing, longPressSpeedActive else { return }
            longPressSpeedActive = false
            kernel?.setRate(speed)
            scheduleHUDClear()
        }
        // "左键单击" on the desktop is the single tap here; the default is play/pause,
        // which is why the settings row reads that way.
        .onTapGesture { perform(clickAction) }
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
                        Button { switchToNextMedia() } label: {
                            Image(systemName: "rectangle.stack")
                        }
                    }

                    Spacer()

                    Menu {
                        ForEach(speeds, id: \.self) { value in
                            Button(value == 1 ? "1.0x（默认）" : String(format: "%.2fx", value)) {
                                setSpeed(value)
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
                            outputRow(title: "显示状态信息",
                                      detail: showOSD ? "开" : "关",
                                      active: showOSD) {
                                showOSD.toggle()
                            }
                            let chapters = kernel?.chapters() ?? []
                            if !chapters.isEmpty {
                                Text("章节（\(chapters.count)）")
                                    .font(.caption2)
                                    .foregroundColor(.white.opacity(0.6))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, 16)
                                    .padding(.top, 8)
                                ForEach(chapters.indices, id: \.self) { index in
                                    let chapter = chapters[index]
                                    trackButton(
                                        TrackRow(id: index,
                                                 title: chapter.title.isEmpty ? "第 \(index + 1) 章" : chapter.title,
                                                 subtitle: timecode(chapter.start),
                                                 selected: currentTime >= chapter.start && currentTime < chapter.end,
                                                 name: chapter.title)
                                    ) {
                                        kernel?.seek(to: chapter.start)
                                        currentTime = chapter.start
                                        activePanel = nil
                                    }
                                }
                            }
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

    /// Live counters, in the corner where they do not fight the controls.
    private func osdOverlay(_ stats: OSDStats) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("视频 \(Double(stats.videoBitrate).bitrateText)")
            Text("音频 \(Double(stats.audioBitrate).bitrateText)")
            Text(String(format: "帧率 %.1f", stats.fps))
            if stats.dropped > 0 {
                Text("丢帧 \(stats.dropped)")
            }
            Text("已读 \(stats.bytesRead.sizeText)")
            if abs(stats.syncDiff) > 0.001 {
                Text(String(format: "音画差 %.0f ms", stats.syncDiff * 1000))
            }
        }
        .font(.system(size: 11, weight: .medium, design: .monospaced))
        .foregroundColor(.white)
        .padding(8)
        .background(Color.black.opacity(0.45))
        .cornerRadius(6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .allowsHitTesting(false)
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
            if showSkipTip {
                toast = "已跳过片头 \(skipIntroSeconds) 秒"
                scheduleToastClear()
            }
        }
        if skipOutroSeconds > 0, !didSkipOutro, total > 0,
           total - current <= Double(skipOutroSeconds) {
            didSkipOutro = true
            if autoNext {
                beginCountdown()
            } else if showSkipTip {
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
        // The host swaps the presented screen; until it does, the outgoing item is
        // still on screen and still playing. The switch decides whether the viewer
        // sees that or a black frame.
        if switchBlackout { blackout = true }
        let seeded = PlayRequest(
            url: next.url,
            name: next.name,
            userAgent: request.userAgent,
            positionMs: LibraryStore.shared.savedPosition(url: next.url),
            queue: request.queue
        )
        onPlayNext?(seeded)
    }

    /// Moves to the next item in the queue, if there is one.
    ///
    /// Distinct from `beginCountdown`, which waits for the current item to finish;
    /// this is the viewer asking to move on now. Both end up in the same `advance`,
    /// so the resume position and the queue carry over the same way.
    private func switchToNextMedia() {
        let queue = request.queue
        guard let index = queue.firstIndex(where: { $0.url == request.url }),
              index + 1 < queue.count else {
            toast = "队列中没有下一个"
            scheduleToastClear()
            return
        }
        upNext = queue[index + 1]
        advance()
    }

    /// "后台音频" off means the sound stops when the app leaves the foreground.
    ///
    /// The kernel activates a playback session on its own, so the switch is enforced
    /// here rather than by withholding a configuration: leaving the foreground is the
    /// moment it matters, and that is a scene event, not a playback one.
    private func pauseIfBackgroundAudioDisabled() {
        guard !backgroundAudio, kernel != nil else { return }
        kernel?.pause()
        isPlaying = false
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

    /// "暂停后自动锁屏": the screen has to stay awake while playing, and the switch
    /// decides what happens once paused — off keeps it awake through the pause too.
    private func updateIdleTimer() {
        UIApplication.shared.isIdleTimerDisabled = isPlaying || !pauseAutoLock
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

    /// The format switch decides the bytes that leave the player: JPEG at the quality
    /// the video tab sets, or PNG when the viewer wants no loss. Encoding here rather
    /// than handing the image over keeps the two ends of that switch honest.
    private func captureFrame() {
        guard let image = kernel?.capture() else {
            toast = "截图失败"
            scheduleToastClear()
            return
        }
        let data = screenshotFormat == "png"
            ? image.pngData()
            : image.jpegData(compressionQuality: CGFloat(jpegQuality))
        if let data {
            PHPhotoLibrary.shared().performChanges {
                PHAssetCreationRequest.forAsset()
                    .addResource(with: .photo, data: data, options: nil)
            }
            if screenshotToClipboard {
                UIPasteboard.general.image = image
            }
        }
        toast = screenshotToClipboard ? "已保存截图并复制" : "已保存截图"
        scheduleToastClear()
    }

    // MARK: lifecycle

    private func start() {
        guard let url = URL(string: request.url) else { return }

        let options = KSOptions()
        options.isAccurateSeek = accurateSeek
        options.hardwareDecode = preferHardwareDecode
        // "软件解码线程" — only the FFmpeg path has threads to set. AVFoundation decodes
        // inside the system framework and has nothing to configure here, which is why
        // the settings row calls it 软件解码线程.
        options.decoderOptions["threads"] = String(max(1, decodeThreads))
        // The FFmpeg path opens its own sockets, so it never sees the system proxy
        // unless the kernel hands it over. The AVFoundation path ignores this.
        KSOptions.useSystemHTTPProxy = useSystemProxy
        options.userAgent = request.userAgent ?? "SenPlay/1.1 (iOS)"
        // Server-issued headers (Emby's X-Emby-Authorization, referers) go in through
        // the kernel's own header channel so they reach both the AVFoundation and the
        // FFmpeg paths.
        if !request.headers.isEmpty {
            options.appendHeader(request.headers)
        }
        options.registerRemoteControll = true

        // Pre-cache. "不缓存" keeps memory flat on long files; "保留最后一次" trades
        // memory for a smoother scrub near the playhead.
        switch preloadMode {
        case "last":
            options.maxBufferDuration = 60
            options.preferredForwardBufferDuration = 30
        default:
            options.maxBufferDuration = 20
            options.preferredForwardBufferDuration = 0
        }

        // HDR pass-through. Only meaningful when the panel and the source agree.
        if hdrMetadata {
            options.destinationDynamicRange = .hdr10
        }

        // Audio. The gain filter is what "音量到达 100% 后继续放大" actually means;
        // multichannel downmix is the opposite direction and is left off by default
        // because it can drop the dialogue on stereo devices.
        if audioEnhance {
            options.audioFilters.append("volume=2.0")
        }
        if multiChannel {
            options.audioFilters.append("pan=stereo|c0=c0|c1=c1")
        }

        // AI upscaling and de-interlacing are FFmpeg graph work; AVFoundation paths
        // ignore them, which is why the preference says so in its own footnote.
        if upscaling {
            options.videoFilters.append("scale=iw*1.5:ih*1.5:flags=lanczos")
        }
        options.autoDeInterlace = false
        options.autoRotate = true
        options.autoSelectEmbedSubtitle = true
        options.isSeekedAutoPlay = true

        let player = KernelPlayer(url: url, options: options)
        player.preferredAudioLanguage = preferredAudioLanguage
        player.preferredSubtitleLanguage = preferredSubtitleLanguage
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
        // "记住缩放大小": the pinch scale from the last session, unless it was
        // switched off or never set.
        zoom = rememberZoomSize && rememberedZoom > 0 ? CGFloat(rememberedZoom) : 1

        let resume = request.positionMs > 0
            ? request.positionMs
            : (resumeFromLast ? LibraryStore.shared.savedPosition(url: request.url) : 0)
        if resume > 0 {
            player.seek(to: resume / 1000)
        }

        if let subtitleURL = request.subtitleURL, !subtitleURL.isEmpty {
            Task { await subtitles.load(urlString: subtitleURL) }
        }

        loadDanmaku()

        applyRememberedSpeed()

        keyRouter.handler = { key in
            switch key {
            case "toggle": kernel?.togglePlay()
            case "back": kernel?.seek(to: max(currentTime - 15, 0))
            case "forward": kernel?.seek(to: min(currentTime + 15, duration))
            case "volumeUp":
                volume = min(volume + 0.1, 1)
                kernel?.setVolume(Float(volume))
            case "volumeDown":
                volume = max(volume - 0.1, 0)
                kernel?.setVolume(Float(volume))
            case "rateUp":
                setSpeed(min(speed + 0.25, 3))
            case "rateDown":
                setSpeed(max(speed - 0.25, 0.25))
            case "reset":
                setSpeed(1)
            case "fullscreen": controlsVisible.toggle()
            case "info": activePanel = activePanel == .more ? nil : .more
            default: break
            }
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
        updateIdleTimer()

        // "开始播放提示标题" and "网盘限速提示" share the one line, so what the file is
        // served from wins over the title — the same order the Android side uses.
        if showCloudThrottleTip, request.isCloudDrive {
            toast = "网盘限速，播放可能不流畅"
            scheduleToastClear()
        } else if showStartTitle {
            toast = request.name
            scheduleToastClear()
        }
    }

    /// Pulls comments for this title from the first configured endpoint. Skipped for
    /// short clips when the preference says so, mirroring the reference behaviour.
    private func loadDanmaku() {
        guard danmakuEnabled else { return }
        let endpoints = DanmakuEndpoint.load().filter { !$0.url.isEmpty }
        guard let endpoint = endpoints.first else { return }

        Task {
            let keyword = request.name
                .replacingOccurrences(of: "\\.(mp4|mkv|avi|mov|ts|m3u8|flv|webm|rmvb|wmv)$",
                                      with: "", options: [.regularExpression, .caseInsensitive])
            let loader = DanmakuLoader()
            let items = await loader.fetch(keyword: keyword, base: endpoint.url)
            await MainActor.run {
                if danmakuSkipShort, duration > 0, duration < 600 { return }
                danmakuItems = items
            }
        }
    }

    private var floatingControls: Bool { controlBarStyle == "floating" }

    /// Keywords arrive as free text; separators are whatever the viewer used, so the
    /// list is split on any of the common ones rather than a single character.
    private var blockedKeywords: [String] {
        danmakuKeywordText
            .components(separatedBy: CharacterSet(charactersIn: ",，;；\n|"))
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    /// Restores the rate the viewer last chose. Off by default only when the
    /// preference says so — most people who set 1.5x want it next time too.
    private func applyRememberedSpeed() {
        guard rememberSpeed, lastSpeed > 0, lastSpeed != 1 else { return }
        speed = Float(lastSpeed)
        kernel?.setRate(Float(lastSpeed))
    }

    /// Every rate change goes through here so the preference stays in step with what
    /// is actually playing.
    private func setSpeed(_ value: Float) {
        speed = value
        kernel?.setRate(value)
        if rememberSpeed {
            lastSpeed = Double(value)
        }
    }

    private func finish() {
        subtitles.stop()
        NowPlaying.shared.deactivate()
        UIApplication.shared.isIdleTimerDisabled = false
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


/// Snapshot of the kernel's live counters, taken once a second.
struct OSDStats {
    var videoBitrate: Int
    var audioBitrate: Int
    var fps: Double
    var dropped: UInt32
    var bytesRead: Int64
    var syncDiff: Double

    var isEmpty: Bool {
        videoBitrate == 0 && audioBitrate == 0 && fps == 0 && bytesRead == 0
    }
}

private extension Double {
    /// Bitrates arrive in bits per second and read better in the units people quote
    /// them in.
    var bitrateText: String {
        if self >= 1_000_000 { return String(format: "%.2f Mbps", self / 1_000_000) }
        if self >= 1_000 { return String(format: "%.0f kbps", self / 1_000) }
        return String(format: "%.0f bps", self)
    }
}

private extension Int64 {
    var sizeText: String {
        ByteCountFormatter.string(fromByteCount: self, countStyle: .file)
    }
}
