import Foundation

public struct Output: Equatable, Sendable {
    public enum Kind: Sendable { case speaker, headphones, unknown }
    public let kind: Kind
    // Session-only identity. Never encode or log hardware names / identifiers.
    public let id: String
    public init(kind: Kind, id: String) { self.kind = kind; self.id = id }
}

public protocol AudioSink: AnyObject {
    func mute()
    func stop()
    func unmute()
}

/// The adapter serializes ALL calls with its player lock, including notifications.
public final class RouteGuard {
    public enum State: Equatable { case idle, verifying, playing, blocked }
    public private(set) var state: State = .idle
    private let sink: AudioSink
    private var expected: Output?

    public init(sink: AudioSink) { self.sink = sink }

    @discardableResult public func begin(outputs: [Output]) -> Bool {
        sink.mute()
        sink.stop()
        expected = nil
        guard outputs.count == 1, let output = outputs.first,
              output.kind != .unknown, !output.id.isEmpty else {
            state = .blocked
            return false
        }
        expected = output
        state = .verifying
        return true
    }

    @discardableResult public func verify(outputs: [Output]) -> Bool {
        guard state == .verifying, let expected, outputs == [expected] else {
            invalidate()
            return false
        }
        state = .playing
        sink.unmute()
        return true
    }

    public func matches(outputs: [Output]) -> Bool {
        state == .playing && expected != nil && outputs == [expected!]
    }

    public func invalidate() {
        sink.mute() // Ordering is a product safety invariant.
        sink.stop()
        expected = nil
        state = .blocked
    }

    public func end() {
        sink.mute()
        sink.stop()
        expected = nil
        state = .idle
    }
}
