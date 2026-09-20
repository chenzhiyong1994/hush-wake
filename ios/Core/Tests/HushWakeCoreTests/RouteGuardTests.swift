import XCTest
@testable import HushWakeCore

final class RouteGuardTests: XCTestCase {
    final class Sink: AudioSink {
        var events: [String] = []
        func mute() { events.append("mute") }
        func stop() { events.append("stop") }
        func unmute() { events.append("unmute") }
    }

    func testDisconnectMutesBeforeStopAndCannotResumeOnSpeaker() {
        let sink = Sink()
        let guardrail = RouteGuard(sink: sink)
        let headphones = [Output(kind: .headphones, id: "ephemeral-a")]
        XCTAssertTrue(guardrail.begin(outputs: headphones))
        XCTAssertTrue(guardrail.verify(outputs: headphones))
        sink.events = []
        guardrail.invalidate()
        XCTAssertEqual(sink.events, ["mute", "stop"])
        XCTAssertFalse(guardrail.verify(outputs: [Output(kind: .speaker, id: "speaker")]))
        XCTAssertEqual(guardrail.state, .blocked)
        XCTAssertFalse(sink.events.contains("unmute"))
    }

    func testUnknownEmptyAndMultipleOutputsNeverUnmute() {
        let cases: [[Output]] = [[], [.init(kind: .unknown, id: "bluetooth")],
                                [.init(kind: .headphones, id: "a"), .init(kind: .speaker, id: "b")]]
        for outputs in cases {
            let sink = Sink()
            let guardrail = RouteGuard(sink: sink)
            XCTAssertFalse(guardrail.begin(outputs: outputs))
            XCTAssertFalse(guardrail.verify(outputs: outputs))
            XCTAssertFalse(sink.events.contains("unmute"))
            XCTAssertEqual(sink.events.suffix(2), ["mute", "stop"])
        }
    }

    func testRouteMustMatchBothKindAndSessionIdentity() {
        let sink = Sink()
        let guardrail = RouteGuard(sink: sink)
        XCTAssertTrue(guardrail.begin(outputs: [.init(kind: .headphones, id: "a")]))
        XCTAssertFalse(guardrail.verify(outputs: [.init(kind: .headphones, id: "b")]))
        XCTAssertFalse(sink.events.contains("unmute"))
    }

    func testSpeakerRequiresFreshExplicitSessionAndMutedStartup() {
        let sink = Sink()
        let guardrail = RouteGuard(sink: sink)
        let speaker = [Output(kind: .speaker, id: "speaker")]
        XCTAssertTrue(guardrail.begin(outputs: speaker))
        XCTAssertEqual(sink.events, ["mute", "stop"])
        XCTAssertEqual(guardrail.state, .verifying)
        XCTAssertTrue(guardrail.verify(outputs: speaker))
        XCTAssertEqual(sink.events.last, "unmute")
        guardrail.end()
        XCTAssertFalse(guardrail.verify(outputs: speaker))
    }
}
