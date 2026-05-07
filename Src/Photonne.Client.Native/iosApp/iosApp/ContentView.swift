import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let baseUrl = Bundle.main.object(forInfoDictionaryKey: "PhotonneApiBaseUrl") as? String
            ?? "http://localhost:1107"
        return MainViewControllerKt.MainViewController(apiBaseUrl: baseUrl)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView().ignoresSafeArea(.keyboard)
    }
}
