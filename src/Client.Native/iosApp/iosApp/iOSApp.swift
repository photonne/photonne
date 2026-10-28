import ComposeApp
import SwiftUI
import UIKit
import WidgetKit

@main
struct iOSApp: App {
    // SwiftUI doesn't run UIApplicationDelegate methods by default — this
    // adaptor opts back in so AppDelegate.application(_:didFinishLaunching…)
    // gets called and can register the BGTaskScheduler handler.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                // photonne://share/{token} y photonne://memory/{id} (el widget):
                // el lado Kotlin los deja en el buzón de ExternalNavigation, que
                // la app consume (tras el login si hace falta).
                .onOpenURL { url in
                    _ = ExternalNavigation.shared.handleUrl(url: url.absoluteString)
                }
                .onChange(of: scenePhase) { _, phase in
                    switch phase {
                    case .active:
                        // Lo que haya dejado la Share Extension va a la Subida.
                        IosShareInbox.shared.drain()
                    case .background:
                        MemoryWidgetRefresher.refresh()
                    default:
                        break
                    }
                }
        }
    }
}

/// Pone al día el widget de Recuerdos (la instantánea en el App Group) y lo
/// recarga si ha cambiado. Pide unos segundos de ejecución en segundo plano:
/// se llama justo al salir de la app.
enum MemoryWidgetRefresher {
    static func refresh() {
        let backgroundTask = BackgroundTaskBox()
        backgroundTask.begin()
        Task {
            let changed = (try? await IosMemoryWidget.shared.refresh())?.boolValue ?? false
            if changed {
                WidgetCenter.shared.reloadAllTimelines()
            }
            backgroundTask.end()
        }
    }
}

/// Una tarea de `beginBackgroundTask` que se cierra una sola vez, la cierre el
/// trabajo al acabar o iOS al agotar el tiempo.
@MainActor
final class BackgroundTaskBox {
    private var identifier: UIBackgroundTaskIdentifier = .invalid

    func begin() {
        identifier = UIApplication.shared.beginBackgroundTask(withName: "MemoryWidget") { [weak self] in
            self?.end()
        }
    }

    func end() {
        guard identifier != .invalid else { return }
        UIApplication.shared.endBackgroundTask(identifier)
        identifier = .invalid
    }
}
