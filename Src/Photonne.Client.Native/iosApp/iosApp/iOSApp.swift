import SwiftUI

@main
struct iOSApp: App {
    // SwiftUI doesn't run UIApplicationDelegate methods by default — this
    // adaptor opts back in so AppDelegate.application(_:didFinishLaunching…)
    // gets called and can register the BGTaskScheduler handler.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
