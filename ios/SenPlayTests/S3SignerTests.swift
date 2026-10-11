import XCTest
@testable import SenPlay

/// Signature V4 against AWS's published `aws4_testsuite` vectors.
///
/// A wrong signature only ever shows up as a bare 403 at runtime, which is not
/// something anyone can debug from the app. This mirrors the Kotlin test so the two
/// implementations are pinned to the same specification rather than to each other.
final class S3SignerTests: XCTestCase {

    private let accessKey = "AKIDEXAMPLE"
    private let secretKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"

    func testEmptyPayloadHashMatchesTheKnownConstant() {
        XCTAssertEqual(
            S3Signer.sha256Hex(Data()),
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )
    }

    func testCanonicalQuerySortsByNameThenValueAndEncodes() {
        let query = S3Signer.canonicalQuery([
            URLQueryItem(name: "list-type", value: "2"),
            URLQueryItem(name: "prefix", value: "a b/c"),
            URLQueryItem(name: "delimiter", value: "/"),
        ])
        XCTAssertEqual(query, "delimiter=%2F&list-type=2&prefix=a%20b%2Fc")
    }

    func testCanonicalQueryOrdersRepeatedNamesByValue() {
        let query = S3Signer.canonicalQuery([
            URLQueryItem(name: "tag", value: "b"),
            URLQueryItem(name: "tag", value: "a"),
            URLQueryItem(name: "x", value: "1"),
        ])
        XCTAssertEqual(query, "tag=a&tag=b&x=1")
    }

    /// Reproduces AWS's `get-vanilla` case, which pins both the canonical-request
    /// layout and the key derivation chain.
    func testSignatureMatchesThePublishedGetVanillaVector() {
        let stamp = "20150830T123600Z"
        let day = String(stamp.prefix(8))

        let canonicalRequest = [
            "GET",
            "/",
            "",
            "host:example.amazonaws.com\nx-amz-date:\(stamp)\n",
            "host;x-amz-date",
            S3Signer.sha256Hex(Data()),
        ].joined(separator: "\n")

        let scope = "\(day)/us-east-1/service/aws4_request"
        let stringToSign = [
            "AWS4-HMAC-SHA256",
            stamp,
            scope,
            S3Signer.sha256Hex(Data(canonicalRequest.utf8)),
        ].joined(separator: "\n")

        XCTAssertEqual(
            S3Signer.signatureForTest(secret: secretKey, day: day, region: "us-east-1",
                                      service: "service", stringToSign: stringToSign),
            "5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31"
        )
    }

    /// The header the client actually emits, checked for shape rather than a
    /// constant: it must carry the credential, the signed-header list and a
    /// signature, in that order and all present.
    func testSignedRequestCarriesAFullyFormedAuthorizationHeader() throws {
        let server = ServerConfig(type: "s3", name: "t",
                                  address: "bucket@minio.lan:9000",
                                  username: accessKey, password: secretKey)
        let target = try XCTUnwrap(S3Target(server: server))
        var request = URLRequest(url: try XCTUnwrap(URL(string: "http://minio.lan:9000/bucket/key")))
        request.httpMethod = "GET"

        S3Signer.sign(request: &request, target: target,
                      payloadHash: S3Signer.sha256Hex(Data()),
                      now: Date(timeIntervalSince1970: 1_440_936_960))

        let header = try XCTUnwrap(request.value(forHTTPHeaderField: "Authorization"))
        XCTAssertTrue(header.hasPrefix("AWS4-HMAC-SHA256 Credential=\(accessKey)/"), header)
        XCTAssertTrue(header.contains("/us-east-1/s3/aws4_request"), header)
        XCTAssertTrue(header.contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"), header)
        XCTAssertTrue(header.contains("Signature="), header)
        // The signature is the last field and is 64 hex characters.
        let signature = header.components(separatedBy: "Signature=").last ?? ""
        XCTAssertEqual(signature.count, 64, header)
        XCTAssertTrue(signature.allSatisfy { $0.isHexDigit }, header)
    }
}
