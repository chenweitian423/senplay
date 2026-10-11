import KSPlayer
import SwiftUI

/// Exercises the integration shape, not just the package graph: a `KSPlayerLayer`
/// driving the render surface while the app keeps its own control bar, which is
/// how the shipping player will consume the kernel.
@main
struct KernelProbeApp: App {
    var body: some Scene {
        WindowGroup { KernelProbeView() }
    }
}

struct KernelProbeView: View {
    @StateObject private var coordinator: KernelCoordinator

    init() {
        let options = KSOptions()
        options.isAccurateSeek = true
        options.hardwareDecode = true
        options.userAgent = "SenPlay/1.1 (iOS)"
        let url = URL(string: "https://example.invalid/probe.mp4")!
        _coordinator = StateObject(wrappedValue: KernelCoordinator(url: url, options: options))
    }

    var body: some View {
        VStack(spacing: 10) {
            KernelSurface(coordinator: coordinator)
                .frame(height: 200)
                .background(Color.black)

            Text("state: \(String(describing: coordinator.state))")
                .font(.system(.footnote, design: .monospaced))
            Text("audio=\(coordinator.audioTracks().count) subtitle=\(coordinator.subtitleTracks().count)")
                .font(.system(.footnote, design: .monospaced))

            HStack(spacing: 16) {
                Button("play") { coordinator.play() }
                Button("pause") { coordinator.pause() }
                Button("1x") { coordinator.setRate(1) }
                Button("2x") { coordinator.setRate(2) }
            }
        }
        .padding()
    }
}
