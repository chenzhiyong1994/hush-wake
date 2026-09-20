// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "HushWakeCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "HushWakeCore", targets: ["HushWakeCore"])],
    targets: [
        .target(name: "HushWakeCore"),
        .testTarget(name: "HushWakeCoreTests", dependencies: ["HushWakeCore"])
    ]
)
