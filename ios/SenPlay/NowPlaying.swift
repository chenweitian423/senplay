import Foundation
import MediaPlayer

/// Lock screen and Control Center integration. The kernel already registers for
/// remote control transport, so this only supplies metadata and routes the button
/// presses back into the player.
final class NowPlaying {
    static let shared = NowPlaying()

    private var onPlay: (() -> Void)?
    private var onPause: (() -> Void)?
    private var onToggle: (() -> Void)?
    private var onSeek: ((Double) -> Void)?

    private init() {}

    func activate(
        title: String,
        duration: Double,
        artist: String = "SenPlay",
        onPlay: @escaping () -> Void,
        onPause: @escaping () -> Void,
        onToggle: @escaping () -> Void,
        onSeek: @escaping (Double) -> Void
    ) {
        self.onPlay = onPlay
        self.onPause = onPause
        self.onToggle = onToggle
        self.onSeek = onSeek

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: artist,
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.video.rawValue,
            MPNowPlayingInfoPropertyPlaybackRate: 1.0,
        ]
        if duration > 0, duration.isFinite {
            info[MPMediaItemPropertyPlaybackDuration] = duration
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info

        let center = MPRemoteCommandCenter.shared()

        center.playCommand.isEnabled = true
        center.playCommand.addTarget { [weak self] _ in
            self?.onPlay?()
            MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPNowPlayingInfoPropertyPlaybackRate] = 1.0
            return .success
        }

        center.pauseCommand.isEnabled = true
        center.pauseCommand.addTarget { [weak self] _ in
            self?.onPause?()
            MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPNowPlayingInfoPropertyPlaybackRate] = 0.0
            return .success
        }

        center.togglePlayPauseCommand.isEnabled = true
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.onToggle?()
            return .success
        }

        center.changePlaybackPositionCommand.isEnabled = true
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime else {
                return .commandFailed
            }
            self?.onSeek?(position)
            return .success
        }

        center.skipForwardCommand.isEnabled = true
        center.skipForwardCommand.preferredIntervals = [15]
        center.skipForwardCommand.addTarget { [weak self] event in
            let interval = (event as? MPSkipIntervalCommandEvent)?.interval ?? 15
            self?.seek(by: interval)
            return .success
        }

        center.skipBackwardCommand.isEnabled = true
        center.skipBackwardCommand.preferredIntervals = [15]
        center.skipBackwardCommand.addTarget { [weak self] event in
            let interval = (event as? MPSkipIntervalCommandEvent)?.interval ?? 15
            self?.seek(by: -interval)
            return .success
        }
    }

    private var lastPosition: Double = 0

    private func seek(by delta: Double) {
        onSeek?(max(lastPosition + delta, 0))
    }

    func update(time: Double, duration: Double, rate: Float) {
        lastPosition = time
        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        if time.isFinite, time >= 0 {
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = time
        }
        if duration > 0, duration.isFinite {
            info[MPMediaItemPropertyPlaybackDuration] = duration
        }
        info[MPNowPlayingInfoPropertyPlaybackRate] = rate
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    func deactivate() {
        let center = MPRemoteCommandCenter.shared()
        [center.playCommand, center.pauseCommand, center.togglePlayPauseCommand,
         center.changePlaybackPositionCommand, center.skipForwardCommand,
         center.skipBackwardCommand].forEach { $0.removeTarget(nil) }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        onPlay = nil
        onPause = nil
        onToggle = nil
        onSeek = nil
    }
}
