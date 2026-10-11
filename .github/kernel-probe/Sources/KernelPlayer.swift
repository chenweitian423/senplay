import KSPlayer
import SwiftUI

/// Prototype of the kernel swap: drive `KSPlayerLayer` directly so the shipping
/// player keeps its own control bar and gesture layer instead of adopting
/// KSPlayer's bundled tool bar. Compiling and linking this for device arm64 is
/// what proves the integration path, not just the package graph.
final class KernelCoordinator: NSObject, ObservableObject, KSPlayerLayerDelegate {
    @Published var state: KSPlayerState = .initialized
    @Published var currentTime: TimeInterval = 0
    @Published var totalTime: TimeInterval = 0
    @Published var bufferingProgress: Int = 0

    let layer: KSPlayerLayer

    init(url: URL, options: KSOptions) {
        layer = KSPlayerLayer(url: url, isAutoPlay: true, options: options, delegate: nil)
        super.init()
        layer.delegate = self
    }

    var renderView: UIView? { layer.player.view }

    func play() { layer.play() }
    func pause() { layer.pause() }
    func toggle() { state == .bufferFinished && layer.player.isPlaying ? pause() : play() }

    func seek(to time: TimeInterval) {
        layer.seek(time: time, autoPlay: true) { _ in }
    }

    func setRate(_ rate: Float) { layer.player.playbackRate = rate }
    func setVolume(_ volume: Float) { layer.player.playbackVolume = volume }
    func setMuted(_ muted: Bool) { layer.player.isMuted = muted }

    /// Track selection feeds the same panel the shipping player exposes.
    func audioTracks() -> [MediaPlayerTrack] {
        layer.player.tracks(mediaType: .audio)
    }

    func subtitleTracks() -> [MediaPlayerTrack] {
        layer.player.tracks(mediaType: .subtitle)
    }

    func select(_ track: some MediaPlayerTrack) {
        layer.player.select(track: track)
    }

    // MARK: KSPlayerLayerDelegate

    func player(layer: KSPlayerLayer, state: KSPlayerState) {
        self.state = state
        bufferingProgress = layer.bufferingProgress
    }

    func player(layer: KSPlayerLayer, currentTime: TimeInterval, totalTime: TimeInterval) {
        self.currentTime = currentTime
        self.totalTime = totalTime
    }

    func player(layer: KSPlayerLayer, finish error: Error?) {}

    func player(layer: KSPlayerLayer, bufferedCount: Int, consumeTime: TimeInterval) {}
}

/// Hosts the kernel's render surface. `KernelCoordinator` owns the control
/// surface, so this view stays a bare UIView bridge.
struct KernelSurface: UIViewRepresentable {
    let coordinator: KernelCoordinator

    func makeUIView(context: Context) -> UIView {
        coordinator.renderView ?? UIView()
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}
