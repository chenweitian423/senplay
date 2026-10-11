import Foundation
import Network

/// Minimal HTTP/1.1 range server.
///
/// Some transports expose only a bespoke API — an FTP control connection, an SMB
/// handle — and the kernel can only fetch over HTTP. Publishing the file on loopback
/// with range support is what lets those play at all.
///
/// Kept in its own file because it is not specific to any one protocol.
final class LocalRangeServer {
    private var listener: NWListener?
    private var connections: [NWConnection] = []
    private let queue = DispatchQueue(label: "com.senplay.localrange")
    private(set) var port: UInt16 = 0

    var fileSize: UInt64 = 0
    var contentType: String = "application/octet-stream"
    var reader: ((UInt64, Int) async throws -> Data)?

    /// Ceiling on a single response body.
    private let maxChunk = 4 * 1024 * 1024

    func start() throws -> UInt16 {
        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        let listener = try NWListener(using: parameters, on: .any)
        self.listener = listener
        listener.newConnectionHandler = { [weak self] connection in
            self?.accept(connection)
        }
        listener.start(queue: queue)

        let deadline = Date().addingTimeInterval(5)
        while listener.port == nil, Date() < deadline {
            usleep(20_000)
        }
        guard let port = listener.port?.rawValue else {
            throw ServerError.empty
        }
        self.port = port
        return port
    }

    func stop() {
        connections.forEach { $0.cancel() }
        connections = []
        listener?.cancel()
        listener = nil
    }

    private func accept(_ connection: NWConnection) {
        connections.append(connection)
        connection.start(queue: queue)
        receive(connection, buffer: Data())
    }

    private func receive(_ connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16 * 1024) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            var accumulated = buffer
            if let data { accumulated.append(data) }

            if let headerEnd = accumulated.range(of: Data("\r\n\r\n".utf8)) {
                let header = String(decoding: accumulated[..<headerEnd.lowerBound], as: UTF8.self)
                Task { await self.respond(to: header, on: connection) }
            } else if error != nil || isComplete {
                connection.cancel()
            } else {
                self.receive(connection, buffer: accumulated)
            }
        }
    }

    private func respond(to header: String, on connection: NWConnection) async {
        var start: UInt64 = 0
        var end: UInt64 = fileSize > 0 ? fileSize - 1 : 0
        var sawRange = false

        for line in header.components(separatedBy: "\r\n").dropFirst() {
            guard line.lowercased().hasPrefix("range:") else { continue }
            sawRange = true
            let spec = line.components(separatedBy: "=").last ?? ""
            let parts = spec.components(separatedBy: "-")
            if let value = UInt64(parts.first?.trimmingCharacters(in: .whitespaces) ?? "") {
                start = value
            }
            if parts.count > 1, let value = UInt64(parts[1].trimmingCharacters(in: .whitespaces)) {
                end = min(value, fileSize > 0 ? fileSize - 1 : value)
            }
        }

        guard let reader else {
            send(connection, status: "500 Internal Server Error", headers: [:], body: Data())
            return
        }

        let requested = end >= start ? Int(min(end - start + 1, UInt64(Int.max))) : 0
        let length = min(requested, maxChunk)

        let body: Data
        do {
            body = try await reader(start, length)
        } catch {
            send(connection, status: "502 Bad Gateway", headers: [:], body: Data())
            return
        }

        var headers: [String: String] = [
            "Content-Type": contentType,
            "Content-Length": "\(body.count)",
            "Accept-Ranges": "bytes",
            "Connection": "close",
        ]
        let status: String
        if sawRange {
            status = "206 Partial Content"
            let last = start + UInt64(max(body.count - 1, 0))
            headers["Content-Range"] = "bytes \(start)-\(last)/\(fileSize)"
        } else {
            status = "200 OK"
        }
        send(connection, status: status, headers: headers, body: body)
    }

    private func send(_ connection: NWConnection, status: String, headers: [String: String], body: Data) {
        var response = "HTTP/1.1 \(status)\r\n"
        for (key, value) in headers { response += "\(key): \(value)\r\n" }
        response += "\r\n"
        var payload = Data(response.utf8)
        payload.append(body)
        connection.send(content: payload, completion: .contentProcessed { _ in connection.cancel() })
    }
}
