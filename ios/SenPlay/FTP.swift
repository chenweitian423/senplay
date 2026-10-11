import Foundation
import Network

// MARK: - Address

/// `ftp://user:pass@host:21/path` — everything after the port is a remote path.
struct FTPTarget {
    var host: String
    var port: UInt16
    var user: String
    var password: String
    var path: String

    init?(server: ServerConfig) {
        var raw = server.address.trimmingCharacters(in: .whitespacesAndNewlines)
        if !raw.lowercased().hasPrefix("ftp://") { raw = "ftp://" + raw }
        guard let url = URL(string: raw), let host = url.host, !host.isEmpty else { return nil }
        self.host = host
        self.port = UInt16(url.port ?? 21)
        self.user = url.user ?? server.username
        self.password = url.password ?? server.password
        self.path = url.path.isEmpty ? "/" : url.path
    }
}

enum FTPError: LocalizedError {
    case notConnected
    case protocolViolation(String)
    case server(String)

    var errorDescription: String? {
        switch self {
        case .notConnected: return "FTP 连接已断开"
        case .protocolViolation(let detail): return "FTP 协议错误：\(detail)"
        case .server(let message): return message
        }
    }
}

// MARK: - Control channel

/// Minimal FTP client: enough of RFC 959 to list, size and stream a file with
/// resumption. Passive mode only — active mode cannot work from a phone.
final class FTPClient {
    private let target: FTPTarget
    private let queue = DispatchQueue(label: "com.senplay.ftp")
    private var control: NWConnection?
    private var buffer = Data()

    init(target: FTPTarget) {
        self.target = target
    }

    // MARK: connection

    private func open() async throws {
        let host = NWEndpoint.Host(target.host)
        guard let port = NWEndpoint.Port(rawValue: target.port) else {
            throw FTPError.protocolViolation("端口无效")
        }
        let connection = NWConnection(host: host, port: port, using: .tcp)
        control = connection
        connection.start(queue: queue)
        try await waitForReady(connection)
        _ = try await readReply()   // 220 banner
        _ = try await command("USER \(target.user)")
        _ = try await command("PASS \(target.password)")
        _ = try await command("TYPE I")
    }

    func connect() async throws {
        if control == nil { try await open() }
    }

    func disconnect() {
        control?.cancel()
        control = nil
    }

    private func waitForReady(_ connection: NWConnection) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            var resumed = false
            connection.stateUpdateHandler = { state in
                guard !resumed else { return }
                switch state {
                case .ready:
                    resumed = true
                    continuation.resume()
                case .failed(let error), .waiting(let error):
                    resumed = true
                    continuation.resume(throwing: error)
                default:
                    break
                }
            }
        }
    }

    // MARK: replies

    private func readReply() async throws -> (code: Int, text: String) {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<(Int, String), Error>) in
            readLine { [weak self] result in
                guard let self else { return }
                switch result {
                case .failure(let error):
                    continuation.resume(throwing: error)
                case .success(let line):
                    let code = Int(line.prefix(3)) ?? 0
                    // Multi-line replies continue while the 4th character is '-'.
                    if line.count > 3, line[line.index(line.startIndex, offsetBy: 3)] == "-" {
                        self.readMultiline(code: code, accumulated: [line], continuation: continuation)
                    } else {
                        continuation.resume(returning: (code, line))
                    }
                }
            }
        }
    }

    private func readMultiline(code: Int, accumulated: [String],
                               continuation: CheckedContinuation<(Int, String), Error>) {
        readLine { [weak self] result in
            guard let self else { return }
            switch result {
            case .failure(let error):
                continuation.resume(throwing: error)
            case .success(let line):
                var lines = accumulated
                lines.append(line)
                if line.hasPrefix("\(code) ") {
                    continuation.resume(returning: (code, lines.joined(separator: "\n")))
                } else {
                    self.readMultiline(code: code, accumulated: lines, continuation: continuation)
                }
            }
        }
    }

    private func readLine(completion: @escaping (Result<String, Error>) -> Void) {
        guard let control else {
            completion(.failure(FTPError.notConnected))
            return
        }
        control.receive(minimumIncompleteLength: 1, maximumLength: 4096) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let error {
                completion(.failure(error))
                return
            }
            if let data { self.buffer.append(data) }
            if let range = self.buffer.range(of: Data("\r\n".utf8)) {
                let line = String(decoding: self.buffer[..<range.lowerBound], as: UTF8.self)
                self.buffer.removeSubrange(..<range.upperBound)
                completion(.success(line))
            } else if isComplete {
                completion(.failure(FTPError.notConnected))
            } else {
                self.readLine(completion: completion)
            }
        }
    }

    @discardableResult
    private func command(_ text: String) async throws -> (code: Int, text: String) {
        guard let control else { throw FTPError.notConnected }
        let payload = Data((text + "\r\n").utf8)
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            control.send(content: payload, completion: .contentProcessed { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            })
        }
        let reply = try await readReply()
        if reply.code >= 400 {
            throw FTPError.server(reply.text)
        }
        return reply
    }

    // MARK: operations

    /// Opens a passive data connection and returns it plus the transfer command reply.
    private func passive() async throws -> NWConnection {
        let reply = try await command("PASV")
        // 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2).
        guard let open = reply.text.firstIndex(of: "("),
              let close = reply.text.firstIndex(of: ")") else {
            throw FTPError.protocolViolation("PASV 应答缺少地址")
        }
        let numbers = reply.text[reply.text.index(after: open)..<close]
            .components(separatedBy: ",")
            .compactMap { UInt16($0.trimmingCharacters(in: .whitespaces)) }
        guard numbers.count == 6 else {
            throw FTPError.protocolViolation("PASV 地址格式异常")
        }
        let host = "\(numbers[0]).\(numbers[1]).\(numbers[2]).\(numbers[3])"
        let port = numbers[4] * 256 + numbers[5]

        guard let endpointPort = NWEndpoint.Port(rawValue: port) else {
            throw FTPError.protocolViolation("PASV 端口无效")
        }
        let data = NWConnection(host: NWEndpoint.Host(host), port: endpointPort, using: .tcp)
        data.start(queue: queue)
        try await waitForReady(data)
        return data
    }

    func list(_ path: String) async throws -> [FTPEntry] {
        let data = try await passive()
        defer { data.cancel() }
        let listing = try await collect(from: data, startingTransfer: {
            try await self.command("LIST \(path)")
        })
        return parseListing(String(decoding: listing, as: UTF8.self), parent: path)
    }

    func size(_ path: String) async throws -> UInt64 {
        let reply = try await command("SIZE \(path)")
        guard let value = UInt64(reply.text.drop(while: { !$0.isNumber }).prefix(while: { $0.isNumber })) else {
            throw FTPError.protocolViolation("SIZE 应答无法解析")
        }
        return value
    }

    /// Reads `length` bytes from `offset`, resuming the transfer with REST.
    func read(path: String, offset: UInt64, length: Int) async throws -> Data {
        let data = try await passive()
        defer { data.cancel() }
        if offset > 0 {
            _ = try await command("REST \(offset)")
        }
        var collected = Data()
        _ = try await collect(from: data, startingTransfer: {
            try await self.command("RETR \(path)")
        }) { chunk in
            collected.append(chunk)
            return collected.count < length
        }
        return collected.count > length ? collected.prefix(length) : collected
    }

    private func collect(
        from connection: NWConnection,
        startingTransfer: @escaping () async throws -> Void,
        shouldContinue: @escaping (Data) -> Bool = { $0.count < 8 * 1024 * 1024 }
    ) async throws -> Data {
        try await startingTransfer()

        var collected = Data()
        while true {
            let chunk = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
                connection.receive(minimumIncompleteLength: 1, maximumLength: 32 * 1024) { data, _, isComplete, error in
                    if let error {
                        continuation.resume(throwing: error)
                    } else if let data, !data.isEmpty {
                        continuation.resume(returning: data)
                    } else if isComplete {
                        continuation.resume(returning: Data())
                    } else {
                        continuation.resume(returning: Data())
                    }
                }
            }
            if chunk.isEmpty { break }
            collected.append(chunk)
            if !shouldContinue(collected) { break }
        }
        _ = try? await readReply()   // 226 transfer complete
        return collected
    }

    private func parseListing(_ text: String, parent: String) -> [FTPEntry] {
        text.components(separatedBy: .newlines).compactMap { line in
            // drwxr-xr-x  2 user group  4096 Jan  1 12:00 name
            let parts = line.split(separator: " ", omittingEmptySubsequences: true)
            guard parts.count >= 9 else { return nil }
            let permissions = String(parts[0])
            guard permissions.count >= 10 else { return nil }
            let name = parts[8...].joined(separator: " ")
            guard name != ".", name != ".." else { return nil }
            let isDirectory = permissions.hasPrefix("d")
            let size = Int(parts[4]) ?? 0
            let base = parent.hasSuffix("/") ? parent : parent + "/"
            return FTPEntry(
                name: name,
                isDirectory: isDirectory,
                size: size,
                path: base + name
            )
        }
    }
}

struct FTPEntry {
    var name: String
    var isDirectory: Bool
    var size: Int
    var path: String
}

// MARK: - Loopback bridge

/// Serves an FTP file over loopback HTTP with range support, so the kernel can pull
/// it with the data source it already understands.
final class FTPMediaBridge {
    private var server: LocalRangeServer?
    private(set) var port: UInt16 = 0

    func start(client: FTPClient, path: String, size: UInt64, mime: String) throws -> UInt16 {
        let server = LocalRangeServer()
        server.fileSize = size
        server.contentType = mime
        server.reader = { offset, length in
            try await client.read(path: path, offset: offset, length: length)
        }
        self.server = server
        let port = try server.start()
        self.port = port
        return port
    }

    func stop() {
        server?.stop()
        server = nil
    }
}

/// Minimal HTTP/1.1 range server. Shared by any transport that can only be reached
/// through a bespoke API.
final class LocalRangeServer {
    private var listener: NWListener?
    private var connections: [NWConnection] = []
    private let queue = DispatchQueue(label: "com.senplay.localrange")
    private(set) var port: UInt16 = 0

    var fileSize: UInt64 = 0
    var contentType: String = "application/octet-stream"
    var reader: ((UInt64, Int) async throws -> Data)?

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
        guard let port = listener.port?.rawValue else { throw FTPError.notConnected }
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
        let isRangeRequest: Bool

        for line in header.components(separatedBy: "\r\n").dropFirst() {
            if line.lowercased().hasPrefix("range:") {
                let spec = line.components(separatedBy: "=").last ?? ""
                let parts = spec.components(separatedBy: "-")
                if let value = UInt64(parts.first?.trimmingCharacters(in: .whitespaces) ?? "") { start = value }
                if parts.count > 1, let value = UInt64(parts[1].trimmingCharacters(in: .whitespaces)) {
                    end = min(value, fileSize > 0 ? fileSize - 1 : value)
                }
            }
        }
        isRangeRequest = start > 0

        guard let reader else {
            send(connection, status: "500 Internal Server Error", headers: [:], body: Data())
            return
        }

        // Cap a single response so a large seek does not allocate the rest of the file.
        let requested = end >= start ? Int(end - start + 1) : 0
        let length = min(requested, 4 * 1024 * 1024)

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
        if isRangeRequest {
            status = "206 Partial Content"
            headers["Content-Range"] = "bytes \(start)-\(start + UInt64(max(body.count - 1, 0)))/\(fileSize)"
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
