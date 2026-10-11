import KSPlayer
import KSPlayerUI
import SwiftUI

/// Touches both products of the KSPlayer package. Compiling and linking this for
/// `-sdk iphoneos` is the whole point of the probe: if the FFmpegKit slice and the
/// KSPlayer.xcframework binary target line up, the shipping player can be moved
/// onto this kernel afterwards.
@main
struct KernelProbeApp: App {
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 10) {
                Text("KSPlayer kernel probe")
                    .font(.headline)
                Text(String(describing: type(of: KSOptions())))
                    .font(.footnote)
                    .monospaced()
                Text("KSVideoPlayer: \(String(describing: KSVideoPlayer.self))")
                    .font(.footnote)
                    .monospaced()
            }
            .padding()
        }
    }
}
