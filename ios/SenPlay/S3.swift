import CryptoKit
import Foundation

// MARK: - Credentials

/// S3-compatible object storage. Users supply an endpoint, a region, an access key
/// and a secret; no OAuth dance and no per-provider SDK, which is what makes this
/// cover the same ground as the netdisk integrations without their maintenance cost.
struct S3Target {
    var endpoint: String      // e.g. https://oss-cn-hangzhou.aliyuncs.com
    var region: String        // e.g. cn-hangzhou, or "us-east-1" for MinIO
    var accessKey: String
    var secretKey: String
    var bucket: String
    var prefix: String        // path inside the bucket, no leading slash

    init?(server: ServerConfig) {
        var raw = server.address.trimmingCharacters(in: .whitespacesAndNewlines)
        let scheme = raw.lowercased().hasPrefix("https") ? "https://" : "http://"
        for prefix in ["https://", "http://"] where raw.lowercased().hasPrefix(prefix) {
            raw = String(raw.dropFirst(prefix.count))
            break
        }
        guard !raw.isEmpty else { return nil }

        // `bucket@host/prefix` keeps the bucket and the prefix in the single address
        // field the server form offers. Without an `@`, the first path segment is the
        // bucket and the rest is the prefix.
        var explicitBucket = ""
        if let at = raw.firstIndex(of: "@"), at != raw.startIndex {
            explicitBucket = String(raw[raw.startIndex..<at])
            raw = String(raw[raw.index(after: at)...])
        }

        let host = raw.split(separator: "/", maxSplits: 1).first.map(String.init)?
            .split(separator: "?").first.map(String.init) ?? ""
        guard !host.isEmpty else { return nil }

        let path = raw.contains("/") ? String(raw[raw.index(after: raw.firstIndex(of: "/")!)...]) : ""
        let trimmedPath = path.split(separator: "?").first.map(String.init) ?? ""

        let bucket: String
        let directory: String
        if explicitBucket.isEmpty {
            bucket = trimmedPath.split(separator: "/").first.map(String.init) ?? ""
            directory = trimmedPath.contains("/")
                ? String(trimmedPath[trimmedPath.index(after: trimmedPath.firstIndex(of: "/")!)...])
                : ""
        } else {
            bucket = explicitBucket
            directory = trimmedPath.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        }

        guard !server.username.isEmpty, !server.password.isEmpty, !bucket.isEmpty else { return nil }

        self.endpoint = scheme + host
        self.accessKey = server.username
        self.secretKey = server.password
        self.bucket = bucket
        self.prefix = directory

        // Region is not part of the reference app's server form, so it is derived
        // from the host the way each provider spells it, with a `?region=` override
        // and a default that MinIO and friends accept.
        if let queryStart = raw.firstIndex(of: "?"),
           let region = URLComponents(string: "http://x" + raw[queryStart...])?
               .queryItems?.first(where: { $0.name == "region" })?.value, !region.isEmpty {
            self.region = region
        } else if let range = host.range(of: "oss-") {
            self.region = String(host[range.upperBound...].prefix { $0 != "." })
        } else if let range = host.range(of: "s3."), host.contains("amazonaws") {
            self.region = String(host[range.upperBound...].prefix { $0 != "." })
        } else if let range = host.range(of: "cos.") {
            self.region = String(host[range.upperBound...].prefix { $0 != "." })
        } else {
            self.region = "us-east-1"
        }
    }
}

struct S3Object {
    var key: String
    var size: Int64
    var isDirectory: Bool
    var modified: Date?

    var name: String { key.split(separator: "/").last.map(String.init) ?? key }
}

// MARK: - Signature V4

/// Hand-rolled SigV4. Pulling in an SDK for four requests would cost more than it
/// saves, and this keeps the same code shape as the Android side.
enum S3Signer {
    static func sign(
        request: inout URLRequest,
        target: S3Target,
        payloadHash: String,
        now: Date = Date()
    ) {
        guard let url = request.url, let host = url.host else { return }

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd'T'HHmmss'Z'"
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.locale = Locale(identifier: "en_US_POSIX")
        let stamp = formatter.string(from: now)
        let day = String(stamp.prefix(8))

        var hostHeader = host
        if let port = url.port, port != 80, port != 443 {
            hostHeader += ":\(port)"
        }
        request.setValue(hostHeader, forHTTPHeaderField: "Host")
        request.setValue(stamp, forHTTPHeaderField: "x-amz-date")
        request.setValue(payloadHash, forHTTPHeaderField: "x-amz-content-sha256")

        var signedHeaders = ["host", "x-amz-content-sha256", "x-amz-date"]
        if let token = request.value(forHTTPHeaderField: "x-amz-security-token"), !token.isEmpty {
            signedHeaders.append("x-amz-security-token")
        }
        signedHeaders.sort()

        let canonicalHeaders = signedHeaders
            .map { "\($0):\(request.value(forHTTPHeaderField: $0)?.trimmingCharacters(in: .whitespaces) ?? "")" }
            .joined(separator: "\n") + "\n"
        let signedHeaderList = signedHeaders.joined(separator: ";")

        // The canonical query string must be sorted and re-encoded, and S3 expects
        // spaces as %20 rather than +.
        let canonicalQuery = canonicalQueryString(url)

        let canonicalRequest = [
            request.httpMethod ?? "GET",
            url.path.isEmpty ? "/" : url.path,
            canonicalQuery,
            canonicalHeaders,
            signedHeaderList,
            payloadHash,
        ].joined(separator: "\n")

        let scope = "\(day)/\(target.region)/s3/aws4_request"
        let stringToSign = [
            "AWS4-HMAC-SHA256",
            stamp,
            scope,
            sha256Hex(Data(canonicalRequest.utf8)),
        ].joined(separator: "\n")

        let signingKey = deriveKey(secret: target.secretKey, day: day, region: target.region)
        let signature = hmac(key: signingKey, message: Data(stringToSign.utf8))
            .map { String(format: "%02x", $0) }
            .joined()

        request.setValue(
            "AWS4-HMAC-SHA256 Credential=\(target.accessKey)/\(scope), "
                + "SignedHeaders=\(signedHeaderList), Signature=\(signature)",
            forHTTPHeaderField: "Authorization"
        )
    }

    private static func canonicalQueryString(_ url: URL) -> String {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let items = components.queryItems else { return "" }
        return canonicalQuery(items)
    }

    /// Sorted, re-encoded query string. Written out step by step because the chain
    /// form makes the type checker give up.
    static func canonicalQuery(_ items: [URLQueryItem]) -> String {
        var pairs: [(String, String)] = []
        pairs.reserveCapacity(items.count)
        for item in items {
            let name: String = encode(item.name)
            let value: String = encode(item.value ?? "")
            pairs.append((name, value))
        }
        pairs.sort { (lhs: (String, String), rhs: (String, String)) -> Bool in
            lhs.0 == rhs.0 ? lhs.1 < rhs.1 : lhs.0 < rhs.0
        }
        var encoded: [String] = []
        encoded.reserveCapacity(pairs.count)
        for pair in pairs {
            encoded.append(pair.0 + "=" + pair.1)
        }
        return encoded.joined(separator: "&")
    }

    private static func encode(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    /// Exposed so a unit test can drive the derivation with an arbitrary service; the
    /// client itself always signs for "s3". AWS's published test vectors use the
    /// service name "service", which is the only way to check the chain against a
    /// value the specification itself provides.
    static func signatureForTest(
        secret: String,
        day: String,
        region: String,
        service: String,
        stringToSign: String
    ) -> String {
        let kDate = hmac(key: Data(("AWS4" + secret).utf8), message: Data(day.utf8))
        let kRegion = hmac(key: kDate, message: Data(region.utf8))
        let kService = hmac(key: kRegion, message: Data(service.utf8))
        let kSigning = hmac(key: kService, message: Data("aws4_request".utf8))
        return hmac(key: kSigning, message: Data(stringToSign.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
    }

    private static func hmac(key: Data, message: Data) -> Data {
        let symmetric = SymmetricKey(data: key)
        return Data(HMAC<SHA256>.authenticationCode(for: message, using: symmetric))
    }

    private static func deriveKey(secret: String, day: String, region: String) -> Data {
        let kDate = hmac(key: Data(("AWS4" + secret).utf8), message: Data(day.utf8))
        let kRegion = hmac(key: kDate, message: Data(region.utf8))
        let kService = hmac(key: kRegion, message: Data("s3".utf8))
        return hmac(key: kService, message: Data("aws4_request".utf8))
    }
}

// MARK: - Client

final class S3Client {
    private let target: S3Target

    init(target: S3Target) {
        self.target = target
    }

    private func url(path: String, query: [URLQueryItem] = []) -> URL? {
        var components = URLComponents(string: target.endpoint)
        components?.path = path
        components?.queryItems = query.isEmpty ? nil : query
        return components?.url
    }

    private func send(_ request: URLRequest, payloadHash: String) async throws -> Data {
        var signed = request
        S3Signer.sign(request: &signed, target: target, payloadHash: payloadHash)
        let (data, response) = try await URLSession.shared.data(for: signed)
        guard let http = response as? HTTPURLResponse else { throw ServerError.empty }
        guard (200...299).contains(http.statusCode) else {
            // S3 errors carry an XML body with the actual reason; surfacing the code
            // alone would send people hunting for a permissions problem that is not
            // there.
            let body = String(decoding: data, as: UTF8.self)
            let message = body.split(separator: ">")
                .first { $0.contains("<Message") }?
                .split(separator: "<").last.map(String.init) ?? ""
            throw ServerError.httpMessage(http.statusCode, message.isEmpty ? "S3 返回 \(http.statusCode)" : message)
        }
        return data
    }

    private var bucketPath: String { "/\(target.bucket)" }

    /// Path-style addressing. Virtual-host style breaks on endpoints with a port and
    /// on most MinIO deployments, and every provider here accepts path style.
    func list(prefix: String? = nil) async throws -> [S3Object] {
        let key = prefix ?? target.prefix
        var query = [
            URLQueryItem(name: "list-type", value: "2"),
            URLQueryItem(name: "delimiter", value: "/"),
        ]
        if !key.isEmpty {
            query.append(URLQueryItem(name: "prefix", value: key.hasSuffix("/") ? key : key + "/"))
        }
        guard let url = url(path: bucketPath, query: query) else { throw ServerError.badAddress }

        let empty = S3Signer.sha256Hex(Data())
        let data = try await send(URLRequest(url: url), payloadHash: empty)

        let delegate = S3ListParser()
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else { throw ServerError.empty }
        return delegate.objects.sorted { lhs, rhs in
            if lhs.isDirectory != rhs.isDirectory { return lhs.isDirectory }
            return lhs.name.localizedStandardCompare(rhs.name) == .orderedAscending
        }
    }

    func streamURL(for object: S3Object) -> String? {
        url(path: "\(bucketPath)/\(object.key)")?.absoluteString
    }

    /// Signs a GET for a byte range so the kernel can seek inside a remote object.
    func signedRequest(for object: S3Object, range: ClosedRange<Int64>? = nil) -> URLRequest? {
        guard let url = url(path: "\(bucketPath)/\(object.key)") else { return nil }
        var request = URLRequest(url: url)
        if let range {
            request.setValue("bytes=\(range.lowerBound)-\(range.upperBound)", forHTTPHeaderField: "Range")
        }
        // Range does not participate in the canonical request, so hashing the empty
        // payload is correct here and matches what S3 expects for GET.
        S3Signer.sign(request: &request, target: target, payloadHash: S3Signer.sha256Hex(Data()))
        return request
    }

    /// Presigns a URL by moving the signature into the query string, which is what a
    /// player needs when it fetches without our headers.
    func presignedURL(for object: S3Object, expires: Int = 3600) -> String? {
        guard let url = url(path: "\(bucketPath)/\(object.key)") else { return nil }
        let now = Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd'T'HHmmss'Z'"
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.locale = Locale(identifier: "en_US_POSIX")
        let stamp = formatter.string(from: now)
        let day = String(stamp.prefix(8))
        let scope = "\(day)/\(target.region)/s3/aws4_request"

        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        var items = components.queryItems ?? []
        items.append(contentsOf: [
            URLQueryItem(name: "X-Amz-Algorithm", value: "AWS4-HMAC-SHA256"),
            URLQueryItem(name: "X-Amz-Credential", value: "\(target.accessKey)/\(scope)"),
            URLQueryItem(name: "X-Amz-Date", value: stamp),
            URLQueryItem(name: "X-Amz-Expires", value: "\(expires)"),
            URLQueryItem(name: "X-Amz-SignedHeaders", value: "host"),
        ])
        components.queryItems = items
        guard let unsigned = components.url else { return nil }

        let hostHeader: String = {
            guard let host = url.host else { return "" }
            if let port = url.port, port != 80, port != 443 { return "\(host):\(port)" }
            return host
        }()

        let queryItems: [URLQueryItem] = components.queryItems ?? []
        let canonicalQuery: String = S3Signer.canonicalQuery(queryItems)

        let canonicalRequest = [
            "GET",
            url.path.isEmpty ? "/" : url.path,
            canonicalQuery,
            "host:\(hostHeader)\n",
            "host",
            S3Signer.sha256Hex(Data()),
        ].joined(separator: "\n")

        let stringToSign = [
            "AWS4-HMAC-SHA256",
            stamp,
            scope,
            S3Signer.sha256Hex(Data(canonicalRequest.utf8)),
        ].joined(separator: "\n")

        let key = deriveSigningKey(day: day)
        let signature = key.map { String(format: "%02x", $0) }.joined()

        var final = URLComponents(url: unsigned, resolvingAgainstBaseURL: false)
        var finalItems = final?.queryItems ?? []
        finalItems.append(URLQueryItem(name: "X-Amz-Signature", value: signature))
        final?.queryItems = finalItems
        return final?.url?.absoluteString
    }

    private func deriveSigningKey(day: String) -> Data {
        func hmac(_ key: Data, _ message: String) -> Data {
            let symmetric = SymmetricKey(data: key)
            return Data(HMAC<SHA256>.authenticationCode(for: Data(message.utf8), using: symmetric))
        }
        let kDate = hmac(Data(("AWS4" + target.secretKey).utf8), day)
        let kRegion = hmac(kDate, target.region)
        let kService = hmac(kRegion, "s3")
        return hmac(kService, "aws4_request")
    }
}

// MARK: - Listing parser

private final class S3ListParser: NSObject, XMLParserDelegate {
    var objects: [S3Object] = []
    private var current: String?
    private var key: String?
    private var size: String?
    private var modified: String?

    func parser(_ parser: XMLParser, didStartElement elementName: String,
                namespaceURI: String?, qualifiedName: String?,
                attributes attributeDict: [String: String]) {
        current = elementName
        if elementName == "Contents" || elementName == "CommonPrefixes" {
            key = nil
            size = nil
            modified = nil
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        switch current {
        case "Key": key = (key ?? "") + string
        case "Prefix": key = (key ?? "") + string
        case "Size": size = (size ?? "") + string
        case "LastModified": modified = (modified ?? "") + string
        default: break
        }
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String,
                namespaceURI: String?, qualifiedName: String?) {
        if elementName == "Contents" || elementName == "CommonPrefixes" {
            if let key, !key.isEmpty {
                let isDirectory = key.hasSuffix("/")
                let formatter = ISO8601DateFormatter()
                formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                objects.append(S3Object(
                    key: key,
                    size: Int64(size ?? "0") ?? 0,
                    isDirectory: isDirectory,
                    modified: modified.flatMap { formatter.date(from: $0) }
                        ?? ISO8601DateFormatter().date(from: modified ?? "")
                ))
            }
        }
        current = nil
    }
}
