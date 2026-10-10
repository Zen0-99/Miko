import XCTest
import MikoKit

/// Track B2 — live HTTPS fetch inside a real .app bundle context.
/// The spawned K/N test.kexe (spike 012) has TCP but no DNS resolver;
/// a real app bundle resolves normally via NSURLSession.
final class ProbeTests: XCTestCase {

    func testLiveNetworkFetch() throws {
        let expectation = expectation(description: "fetch raw.githubusercontent.com")
        var captured: String?

        // raw.githubusercontent.com is a file CDN — not subject to the
        // api.github.com per-IP rate limit that flakes on shared runner IPs.
        ProbeKt.probeNetworkAsync(
            url: "https://raw.githubusercontent.com/Zen0-99/Miko/main/settings.gradle.kts",
        ) { body, error in
            captured = body ?? "ERROR: \(error ?? "unknown")"
            expectation.fulfill()
        }

        waitForExpectations(timeout: 30)
        XCTAssertTrue(
            captured?.contains("pluginManagement") ?? false,
            "probe did not return repo payload — got: \(captured ?? "nil")",
        )
    }
}
