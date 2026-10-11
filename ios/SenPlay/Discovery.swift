import Foundation
import Network
import SwiftUI

/// One service found on the local network.
struct DiscoveredService: Identifiable, Hashable {
    var id: String { "\(name)|\(host):\(port)" }
    var name: String
    var host: String
    var port: Int
    var type: String

    var address: String {
        let scheme = type.contains("https") ? "https" : "http"
        return port > 0 ? "\(scheme)://\(host):\(port)" : "\(scheme)://\(host)"
    }

    var kindHint: String {
        let lower = name.lowercased()
        if lower.contains("jellyfin") { return "Jellyfin" }
        if lower.contains("emby") { return "Emby" }
        if lower.contains("plex") { return "Plex" }
        if lower.contains("fnos") || lower.contains("飞牛") { return "飞牛 fnOS" }
        if lower.contains("webdav") || lower.contains("dav") { return "WebDAV" }
        if lower.contains("samba") || lower.contains("smb") { return "SMB" }
        if lower.contains("nas") || lower.contains("synology") || lower.contains("qnap") { return "NAS" }
        return "HTTP 服务"
    }
}

/// Bonjour/mDNS discovery.
///
/// SSDP would cover DLNA devices too, but sending multicast on iOS requires the
/// `com.apple.developer.networking.multicast` entitlement, which has to be
/// requested from Apple. mDNS needs nothing extra, and every media server worth
/// connecting to advertises `_http._tcp` anyway.
final class Discovery: ObservableObject {
    @Published private(set) var services: [DiscoveredService] = []
    @Published private(set) var isBrowsing = false
    @Published private(set) var errorText: String?

    private var browsers: [NWBrowser] = []
    private let queue = DispatchQueue(label: "com.senplay.discovery")

    private let types = [
        "_http._tcp",
        "_https._tcp",
        "_webdav._tcp",
        "_smb._tcp",
    ]

    func start() {
        guard !isBrowsing else { return }
        isBrowsing = true
        errorText = nil
        services = []

        for type in types {
            let parameters = NWParameters()
            parameters.includePeerToPeer = false
            let browser = NWBrowser(for: .bonjour(type: type, domain: nil), using: parameters)

            browser.stateUpdateHandler = { [weak self] state in
                if case .failed(let error) = state {
                    self?.errorText = error.localizedDescription
                }
            }
            browser.browseResultsChangedHandler = { [weak self] results, _ in
                self?.ingest(results, type: type)
            }
            browser.start(queue: queue)
            browsers.append(browser)
        }

        // mDNS answers arrive over a couple of seconds; stop on its own.
        queue.asyncAfter(deadline: .now() + 6) { [weak self] in
            self?.stop()
        }
    }

    func stop() {
        browsers.forEach { $0.cancel() }
        browsers = []
        DispatchQueue.main.async { self.isBrowsing = false }
    }

    private func ingest(_ results: Set<NWBrowser.Result>, type: String) {
        for result in results {
            guard case let .service(name, _, _, _) = result.endpoint else { continue }
            let metadata = result.metadata

            var host = name
            var port = 0
            if case let .bonjour(txt) = metadata, let path = txt["path"] {
                if !path.isEmpty { host = name }
            }

            // Resolve the endpoint to a concrete address.
            let connection = NWConnection(to: result.endpoint, using: .tcp)
            connection.stateUpdateHandler = { [weak self] state in
                defer { connection.cancel() }
                guard case .ready = state, let self else { return }
                if let endpoint = connection.currentPath?.remoteEndpoint,
                   case let .hostPort(remoteHost, remotePort) = endpoint {
                    let text: String
                    switch remoteHost {
                    case .ipv4(let address): text = "\(address)"
                    case .ipv6(let address): text = "\(address)".components(separatedBy: "%").first ?? "\(address)"
                    case .name(let hostname, _): text = hostname
                    @unknown default: text = "\(remoteHost)"
                    }
                    let cleaned = text.components(separatedBy: "%").first ?? text
                    port = Int(remotePort.rawValue)
                    host = cleaned
                }
                let service = DiscoveredService(name: name, host: host, port: port, type: type)
                DispatchQueue.main.async {
                    if !self.services.contains(service) {
                        self.services.append(service)
                        self.services.sort { $0.name < $1.name }
                    }
                }
            }
            connection.start(queue: queue)

            _ = port
        }
    }
}

// MARK: - Screen

struct DiscoverySheet: View {
    @ObservedObject var store: LibraryStore
    @Binding var isPresented: Bool
    @StateObject private var discovery = Discovery()

    var body: some View {
        NavigationView {
            List {
                if discovery.isBrowsing {
                    HStack {
                        ProgressView()
                        Text("正在扫描局域网…").font(.footnote).foregroundColor(.secondary)
                    }
                }
                if let error = discovery.errorText {
                    Text("扫描出错：\(error)").font(.footnote).foregroundColor(.red)
                }
                if discovery.services.isEmpty, !discovery.isBrowsing {
                    Text("没有发现服务。确认设备与服务器在同一网段，然后重新扫描。")
                        .font(.footnote).foregroundColor(.secondary)
                }
                ForEach(discovery.services) { service in
                    Button {
                        store.save(ServerConfig(
                            id: UUID().uuidString,
                            type: "webdav",
                            name: service.name,
                            address: service.address,
                            note: "通过局域网发现 · \(service.kindHint)"
                        ))
                        isPresented = false
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(service.name).lineLimit(1)
                            Text(service.address).font(.caption).foregroundColor(.secondary).lineLimit(1)
                            Text(service.kindHint).font(.caption2).foregroundColor(.accentColor)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("局域网发现")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(discovery.isBrowsing ? "停止" : "扫描") {
                        discovery.isBrowsing ? discovery.stop() : discovery.start()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("完成") { isPresented = false }
                }
            }
            .onAppear { discovery.start() }
            .onDisappear { discovery.stop() }
        }
        .navigationViewStyle(.stack)
    }
}
