import KSPlayer
import SwiftUI

/// Touches the KSPlayer module so that resolving, compiling and linking the whole
/// graph for `-sdk iphoneos` is exercised end to end. 2.3.4 ships the kernel only
/// (no KSPlayerUI), which is exactly the shape the shipping player wants: swap the
/// decode path, keep our own control bar and gesture layer.
@main
struct KernelProbeApp: App {
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 10) {
                Text("KSPlayer kernel probe").font(.headline)
                Text(String(describing: KSOptions.self))
                    .font(.footnote).monospaced()
                Text(String(describing: KSPlayerLayer.self))
                    .font(.footnote).monospaced()
            }
            .padding()
        }
    }
}
