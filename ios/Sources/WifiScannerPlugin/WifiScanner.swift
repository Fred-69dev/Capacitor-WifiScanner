import Foundation

@objc public class WifiScanner: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
