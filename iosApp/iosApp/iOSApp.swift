import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // The Compose view draws its own status-bar spacing, the same
                // way the Android side does, so the safe area is deliberately
                // not applied twice.
                .ignoresSafeArea(.all)
        }
    }
}
