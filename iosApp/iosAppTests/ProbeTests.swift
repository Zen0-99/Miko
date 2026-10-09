import XCTest
import MikoKit

/// Track B2 — live HTTPS fetch inside a real .app bundle context.
/// The spawned K/N test.kexe (spike 012) has TCP but no DNS resolver;
/// a real app bundle resolves normally via NSURLSession.
final class ProbeTests: XCTestCase {

    func testLiveNetworkFetch() throws {
        let expectation = expectation(description: "fetch api.github.com")
        var captured: String?

        ProbeKt.probeNetworkAsync(url: "https://api.github.com/repos/Zen0-99/Miko") { body, error in
            captured = body ?? "ERROR: \(error ?? "unknown")"
            expectation.fulfill()
        }

        waitForExpectations(timeout: 30)
        XCTAssertTrue(
            captured?.contains("Zen0-99/Miko") ?? false,
            "probe did not return repo payload — got: \(captured ?? "nil")",
        )
    }
}
