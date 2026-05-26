import BackgroundTasks
import ComposeApp
import Foundation
import Network
import UIKit

/// Bridges UIKit's app lifecycle into the Kotlin shared module so the
/// background sync flow can be wired up at launch.
///
/// The single responsibility here is the BGTaskScheduler handler:
///   1. Register the handler **once**, before
///      `application(_:didFinishLaunchingWithOptions:)` returns. Apple's docs
///      are strict about this — calling `register(forTaskWithIdentifier:)`
///      after launch logs a fatal error.
///   2. When iOS wakes us up, run the Kotlin backup via
///      `IosBackupBridge.shared.runBackup`, then reschedule the next pass
///      and call `setTaskCompleted(success:)`.
///
/// Wi-Fi-only is honored client-side: BGTaskScheduler has no Wi-Fi
/// constraint, so when the user asked for Wi-Fi-only and we're not on
/// Wi-Fi we just complete the task quickly without running.
class AppDelegate: NSObject, UIApplicationDelegate {

    private static let taskIdentifier = "com.photonne.app.backup"

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: AppDelegate.taskIdentifier,
            using: nil
        ) { task in
            self.handle(task: task)
        }
        return true
    }

    private func handle(task: BGTask) {
        // Always submit the next request before doing anything else — even
        // if this run is aborted, iOS will still come back to us later.
        IosBackupBridge.shared.scheduleNext()

        // Wi-Fi-only enforcement (BGTask has no native constraint for this).
        let prefs = IosBackupBridge.shared.currentPreferences()
        if prefs.requireWifi && !NetworkReachability.isOnWiFi() {
            NSLog("[BackupTask] Wi-Fi required but device is on cellular — skipping")
            task.setTaskCompleted(success: true)
            return
        }

        // Wrap the suspend call in a Task. iOS gives us a budget (~30s for
        // BGAppRefresh, longer for BGProcessing) — when it runs out, the
        // expirationHandler fires and we should bail.
        let work = Task {
            let success = (try? await IosBackupBridge.shared.runBackup().boolValue) ?? false
            task.setTaskCompleted(success: success)
        }

        task.expirationHandler = {
            NSLog("[BackupTask] expiration handler fired — cancelling work")
            work.cancel()
            task.setTaskCompleted(success: false)
        }
    }
}

/// Tiny network reachability check — only used to honor the Wi-Fi-only
/// preference inside BGTask handlers. Uses `NWPathMonitor`; synchronous
/// because we just want a one-shot answer.
enum NetworkReachability {
    static func isOnWiFi() -> Bool {
        // Path monitor with a short timeout — we only need the current
        // snapshot, not an ongoing stream.
        let monitor = NWPathMonitor()
        let semaphore = DispatchSemaphore(value: 0)
        var isWiFi = false

        monitor.pathUpdateHandler = { path in
            isWiFi = path.status == .satisfied && path.usesInterfaceType(.wifi)
            semaphore.signal()
        }
        let queue = DispatchQueue(label: "com.photonne.app.reachability")
        monitor.start(queue: queue)

        // 200ms is plenty — NWPathMonitor emits the initial snapshot almost
        // immediately. If it doesn't (rare), we conservatively say "not Wi-Fi"
        // so the user's preference is respected.
        _ = semaphore.wait(timeout: .now() + .milliseconds(200))
        monitor.cancel()
        return isWiFi
    }
}
