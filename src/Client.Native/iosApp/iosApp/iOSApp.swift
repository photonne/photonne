import ComposeApp
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
                // photonne://share/{token}: el lado Kotlin la deja en el buzón de
                // ExternalNavigation, que la app consume (tras el login si hace falta).
                .onOpenURL { url in
                    _ = ExternalNavigation.shared.handleUrl(url: url.absoluteString)
                }
        }
    }
}
