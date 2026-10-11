import XCTest
@testable import SenPlay

/// Mirrors the Android test case for case, over the same fixture files.
///
/// Two parsers written twice will drift; this is what catches it. The assertions are
/// deliberately the same set, so a disagreement fails on whichever platform changed.
final class M3UParserTests: XCTestCase {

    /// Fixtures live at the repository root and are shared with the Android tests,
    /// so they are located from this file's own path rather than bundled. One copy,
    /// and a divergence between the two parsers fails on whichever side changed.
    private func fixture(_ name: String) throws -> String {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // SenPlayTests
            .deletingLastPathComponent()   // ios
            .deletingLastPathComponent()   // repository root
            .appendingPathComponent("tests/fixtures")
        let url = root.appendingPathComponent(name)
        return try String(contentsOf: url, encoding: .utf8)
    }

    func testBasicListKeepsEveryAttribute() throws {
        let channels = M3UParser.parse(try fixture("iptv-basic.m3u"))
        XCTAssertEqual(channels.count, 4)

        let first = channels[0]
        XCTAssertEqual(first.name, "CCTV-1 综合")
        XCTAssertEqual(first.url, "http://example.test/live/cctv1.m3u8")
        XCTAssertEqual(first.group, "央视")
        XCTAssertEqual(first.logo, "http://logo/1.png")
        XCTAssertEqual(first.tvgId, "cctv1")

        XCTAssertEqual(channels[2].group, "卫视")
        XCTAssertEqual(channels[3].group, "未分组")
    }

    func testMessyListSkipsWhatItCannotClassify() throws {
        let channels = M3UParser.parse(try fixture("iptv-messy.m3u"))
        XCTAssertEqual(
            channels.map(\.url),
            [
                "http://example.test/a.m3u8",
                "http://example.test/b.m3u8",
                "http://example.test/c.m3u8",
                "http://example.test/d.m3u8",
            ]
        )
        XCTAssertFalse(channels.contains { $0.url.contains("not-a-url-line") })
    }

    func testBOMAndCRLFDoNotLeakIntoNames() throws {
        let channels = M3UParser.parse(try fixture("iptv-crlf.m3u"))
        XCTAssertEqual(channels.count, 1)
        let channel = try XCTUnwrap(channels.first)
        XCTAssertEqual(channel.name, "CRLF 频道")
        XCTAssertEqual(channel.group, "CRLF 组")
        XCTAssertFalse(channel.name.unicodeScalars.contains { $0.value < 32 })
        XCTAssertFalse(channel.group.unicodeScalars.contains { $0.value < 32 })
    }
}
