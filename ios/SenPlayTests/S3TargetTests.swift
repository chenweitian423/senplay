import XCTest
@testable import SenPlay

/// Mirrors the Kotlin test over the same fixture. Address parsing is where the two
/// implementations drifted once already, so it is worth pinning from both sides.
final class S3TargetTests: XCTestCase {

    private func fixture() throws -> [String: Any] {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("tests/fixtures/endpoints.json")
        let data = try Data(contentsOf: url)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private func server(_ address: String, _ user: String, _ password: String) -> ServerConfig {
        ServerConfig(type: "s3", name: "test", address: address,
                     username: user, password: password)
    }

    func testAddressesParseAsDocumented() throws {
        let cases = try XCTUnwrap(fixture()["s3"] as? [[String: Any]])
        XCTAssertFalse(cases.isEmpty)

        for item in cases {
            let address = try XCTUnwrap(item["address"] as? String)
            let expected = try XCTUnwrap(item["expect"] as? [String: String])
            let target = try XCTUnwrap(
                S3Target(server: server(address,
                                        item["username"] as? String ?? "",
                                        item["password"] as? String ?? "")),
                "failed to parse \(address)"
            )
            XCTAssertEqual(target.endpoint, expected["endpoint"], address)
            XCTAssertEqual(target.bucket, expected["bucket"], address)
            XCTAssertEqual(target.prefix, expected["prefix"], address)
            XCTAssertEqual(target.region, expected["region"], address)
        }
    }

    func testIncompleteCredentialsAreRejected() throws {
        let cases = try XCTUnwrap(fixture()["rejects"] as? [[String: Any]])
        XCTAssertFalse(cases.isEmpty)

        for item in cases {
            let address = try XCTUnwrap(item["address"] as? String)
            XCTAssertNil(
                S3Target(server: server(address,
                                        item["username"] as? String ?? "",
                                        item["password"] as? String ?? "")),
                "should have been rejected: \(address)"
            )
        }
    }
}
