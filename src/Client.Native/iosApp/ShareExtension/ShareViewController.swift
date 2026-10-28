import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// "Compartir con Photonne" desde Fotos u otra app.
///
/// Una extensión no puede abrir la app ni usar su sesión, así que copia lo
/// compartido a la bandeja del App Group (`ShareInbox`) y termina. La app lo
/// recoge al volver a primer plano (`IosShareInbox.drain()` en Kotlin) y abre
/// la Subida con esos archivos en cola.
final class ShareViewController: UIViewController {
    private let model = ShareModel()

    override func viewDidLoad() {
        super.viewDidLoad()
        let host = UIHostingController(
            rootView: ShareView(model: model) { [weak self] in
                self?.extensionContext?.completeRequest(returningItems: nil)
            }
        )
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)

        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? [])
            .flatMap { $0.attachments ?? [] }
        Task { await model.save(providers) }
    }
}

/// El App Group compartido con la app; tiene que coincidir con los `.entitlements`.
enum SharedInbox {
    static let appGroup = "group.com.photonne.app"
    static let directory = "ShareInbox"

    static func url() -> URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup)?
            .appendingPathComponent(directory, isDirectory: true)
    }
}

@MainActor
final class ShareModel: ObservableObject {
    enum State {
        case saving
        case saved(Int)
        case failed
    }

    @Published private(set) var state: State = .saving

    func save(_ providers: [NSItemProvider]) async {
        guard let inbox = SharedInbox.url() else {
            state = .failed
            return
        }
        try? FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        // Prefijo de orden + "__" + nombre original: la app ordena por él y
        // recupera el nombre (IosShareInbox).
        let batch = Int(Date().timeIntervalSince1970 * 1000)
        var saved = 0
        for (index, provider) in providers.enumerated() {
            guard let typeId = Self.mediaType(of: provider) else { continue }
            let prefix = "\(batch)-\(String(format: "%03d", index))__"
            if await Self.copy(provider, typeId: typeId, into: inbox, prefix: prefix) {
                saved += 1
            }
        }
        state = saved > 0 ? .saved(saved) : .failed
    }

    /// La representación original (HEIC, MOV…) de una foto o un vídeo.
    nonisolated private static func mediaType(of provider: NSItemProvider) -> String? {
        provider.registeredTypeIdentifiers.first { identifier in
            guard let type = UTType(identifier) else { return false }
            return type.conforms(to: .image) || type.conforms(to: .movie)
        }
    }

    /// iOS borra el archivo que entrega `loadFileRepresentation` en cuanto
    /// vuelve el bloque, así que se copia dentro de él (en disco, nunca a memoria).
    nonisolated private static func copy(
        _ provider: NSItemProvider,
        typeId: String,
        into inbox: URL,
        prefix: String
    ) async -> Bool {
        let suggested = provider.suggestedName
        return await withCheckedContinuation { continuation in
            _ = provider.loadFileRepresentation(forTypeIdentifier: typeId) { url, _ in
                guard let url else {
                    continuation.resume(returning: false)
                    return
                }
                let ext = url.pathExtension.isEmpty
                    ? (UTType(typeId)?.preferredFilenameExtension ?? "dat")
                    : url.pathExtension
                let base = suggested ?? url.deletingPathExtension().lastPathComponent
                let target = inbox.appendingPathComponent(prefix + base + "." + ext)
                do {
                    try FileManager.default.copyItem(at: url, to: target)
                    continuation.resume(returning: true)
                } catch {
                    continuation.resume(returning: false)
                }
            }
        }
    }
}

struct ShareView: View {
    @ObservedObject var model: ShareModel
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            switch model.state {
            case .saving:
                ProgressView()
                Text(ShareText.saving)
            case .saved(let count):
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(.green)
                Text(ShareText.saved(count))
                    .multilineTextAlignment(.center)
                Button(ShareText.done, action: onDone)
                    .buttonStyle(.borderedProminent)
            case .failed:
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(.orange)
                Text(ShareText.failed)
                    .multilineTextAlignment(.center)
                Button(ShareText.done, action: onDone)
                    .buttonStyle(.bordered)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground))
    }
}

/// Textos de la extensión, en español o inglés como la app.
enum ShareText {
    private static var spanish: Bool {
        Locale.preferredLanguages.first?.hasPrefix("es") ?? true
    }

    static var saving: String { spanish ? "Preparando…" : "Preparing…" }
    static var done: String { spanish ? "Hecho" : "Done" }
    static var failed: String {
        spanish
            ? "No se ha podido preparar nada para subir."
            : "Nothing could be prepared for upload."
    }

    static func saved(_ count: Int) -> String {
        if spanish {
            return count == 1
                ? "1 archivo listo. Abre Photonne y se subirá desde la pantalla Subir."
                : "\(count) archivos listos. Abre Photonne y se subirán desde la pantalla Subir."
        }
        return count == 1
            ? "1 file ready. Open Photonne and it will upload from the Upload screen."
            : "\(count) files ready. Open Photonne and they will upload from the Upload screen."
    }
}
