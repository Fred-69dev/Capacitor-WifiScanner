// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorWifiScanner",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "CapacitorWifiScanner",
            targets: ["WifiScannerPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "WifiScannerPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/WifiScannerPlugin"),
        .testTarget(
            name: "WifiScannerPluginTests",
            dependencies: ["WifiScannerPlugin"],
            path: "ios/Tests/WifiScannerPluginTests")
    ]
)