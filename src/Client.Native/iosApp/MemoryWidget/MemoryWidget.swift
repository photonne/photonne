import SwiftUI
import UIKit
import WidgetKit

/// Widget de Recuerdos: la portada y el título de un recuerdo; tocarlo lo abre
/// en la app (`photonne://memory/{id}`).
///
/// Un widget no puede ejecutar la app ni tiene su sesión: lee la instantánea que
/// la app deja en el App Group (`IosMemoryWidget` en Kotlin) al pasar a segundo
/// plano y tras el backup en segundo plano, y la app lo recarga entonces con
/// `WidgetCenter`. Por eso la línea de tiempo no programa refrescos propios.
@main
struct PhotonneMemoryWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "MemoryWidget", provider: MemoryProvider()) { entry in
            MemoryWidgetView(entry: entry)
        }
        .configurationDisplayName(WidgetText.name)
        .description(WidgetText.description)
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

/// Lo que escribe la app (`MemoryWidgetSnapshot` en Kotlin): mismos nombres de campo.
struct MemoryWidgetSnapshot: Codable {
    /// "memory", "empty" o "signedOut".
    let state: String
    let memoryId: String?
    let title: String?
    let subtitle: String?
    let url: String?
    let hasCover: Bool?
}

struct MemoryEntry: TimelineEntry {
    let date: Date
    let snapshot: MemoryWidgetSnapshot?
    let cover: UIImage?
}

struct MemoryProvider: TimelineProvider {
    private static let appGroup = "group.com.photonne.app"

    func placeholder(in context: Context) -> MemoryEntry {
        MemoryEntry(date: Date(), snapshot: nil, cover: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (MemoryEntry) -> Void) {
        completion(Self.load())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MemoryEntry>) -> Void) {
        completion(Timeline(entries: [Self.load()], policy: .never))
    }

    private static func load() -> MemoryEntry {
        guard let directory = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup)?
            .appendingPathComponent("MemoryWidget", isDirectory: true)
        else {
            return MemoryEntry(date: Date(), snapshot: nil, cover: nil)
        }
        let snapshot = (try? Data(contentsOf: directory.appendingPathComponent("snapshot.json")))
            .flatMap { try? JSONDecoder().decode(MemoryWidgetSnapshot.self, from: $0) }
        let cover = snapshot?.hasCover == true
            ? UIImage(contentsOfFile: directory.appendingPathComponent("cover.jpg").path)
            : nil
        return MemoryEntry(date: Date(), snapshot: snapshot, cover: cover)
    }
}

struct MemoryWidgetView: View {
    let entry: MemoryEntry

    private var showsMemory: Bool { entry.snapshot?.state == "memory" }

    private var title: String {
        showsMemory ? (entry.snapshot?.title ?? WidgetText.name) : WidgetText.name
    }

    private var subtitle: String? {
        switch entry.snapshot?.state {
        case "memory": return entry.snapshot?.subtitle
        case "empty": return WidgetText.empty
        default: return WidgetText.signedOut
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Spacer(minLength: 0)
            Text(title)
                .font(.headline)
                .foregroundStyle(.white)
                .lineLimit(2)
            if let subtitle, !subtitle.isEmpty {
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(showsMemory ? 1 : 3)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        .containerBackground(for: .widget) {
            ZStack {
                // El gris base del cromo en oscuro (ChromeBaseGrayDark).
                Color(red: 0x24 / 255, green: 0x24 / 255, blue: 0x28 / 255)
                if showsMemory, let cover = entry.cover {
                    Image(uiImage: cover)
                        .resizable()
                        .scaledToFill()
                    LinearGradient(
                        colors: [.black.opacity(0.7), .clear],
                        startPoint: .bottom,
                        endPoint: .center
                    )
                }
            }
        }
        .widgetURL(showsMemory ? entry.snapshot?.url.flatMap(URL.init(string:)) : nil)
    }
}

/// Textos del widget, en español o inglés como la app.
enum WidgetText {
    private static var spanish: Bool {
        Locale.preferredLanguages.first?.hasPrefix("es") ?? true
    }

    static var name: String { spanish ? "Recuerdos" : "Memories" }
    static var description: String {
        spanish ? "Un recuerdo de tu biblioteca cada día." : "A memory from your library every day."
    }
    static var signedOut: String {
        spanish ? "Abre Photonne para ver tus recuerdos" : "Open Photonne to see your memories"
    }
    static var empty: String { spanish ? "Todavía no hay recuerdos" : "No memories yet" }
}
