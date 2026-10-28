package com.photonne.app.widget

import com.photonne.app.IosAppGroup
import com.photonne.app.data.api.LocalReachabilityProbe
import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.timeline.WIDGET_ROTATION_SIZE
import com.photonne.app.data.timeline.pickWidgetMemory
import com.photonne.app.ui.main.ExternalNavigation
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.timeIntervalSince1970

/**
 * Lo que lee el widget de Recuerdos de iOS (`iosApp/MemoryWidget`). El widget
 * no puede ejecutar la app ni tener su sesión, así que la app le deja esta
 * instantánea en el App Group y la portada al lado ([COVER_FILE]). Los nombres
 * de los campos los decodifica Swift (`MemoryWidgetSnapshot`): no cambiarlos
 * sin cambiar los dos lados.
 */
@Serializable
internal data class MemoryWidgetSnapshot(
    /** "memory", "empty" o "signedOut". */
    val state: String,
    val memoryId: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    /** El enlace que abre el recuerdo al tocar el widget. */
    val url: String? = null,
    /** Si hay portada en [COVER_FILE]. */
    val hasCover: Boolean = false,
    /** Segundos desde 1970 de la última vez que se escribió. */
    val refreshedAt: Double = 0.0,
)

/**
 * Prepara el widget de Recuerdos: con sesión elige el recuerdo del día con el
 * mismo criterio que el de Android ([pickWidgetMemory]), baja la portada
 * mediana y escribe la instantánea; sin sesión escribe "signedOut" y borra la
 * portada. Swift llama a [refresh] al pasar la app a segundo plano y tras el
 * backup en segundo plano, y luego recarga el widget con WidgetCenter.
 *
 * Expuesto como `object` para Swift (`IosMemoryWidget.shared`).
 */
object IosMemoryWidget : KoinComponent {
    private const val DIRECTORY = "MemoryWidget"
    private const val SNAPSHOT_FILE = "snapshot.json"
    private const val COVER_FILE = "cover.jpg"

    /** Con la misma sesión, no se vuelve a pedir antes de esto. */
    private const val MIN_INTERVAL_SECONDS = 60.0 * 60.0

    // Algo más que los que rotan, para tener margen si alguno no tiene portada.
    private const val FEED_LIMIT = WIDGET_ROTATION_SIZE * 2

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var running = false

    /**
     * Devuelve true si ha escrito algo nuevo (Swift solo recarga el widget
     * entonces). Nunca lanza: un fallo deja lo que ya se veía.
     */
    suspend fun refresh(): Boolean {
        if (running) return false
        running = true
        try {
            val directory = IosAppGroup.containerPath()?.let { "$it/$DIRECTORY" } ?: return false
            SystemFileSystem.createDirectories(Path(directory))

            val signedIn = get<TokenStorage>().hasSession()
            val previous = readSnapshot(directory)
            val now = NSDate().timeIntervalSince1970
            val sessionUnchanged = previous != null && (previous.state != SIGNED_OUT) == signedIn
            if (sessionUnchanged && now - previous!!.refreshedAt < MIN_INTERVAL_SECONDS) return false

            if (!signedIn) {
                SystemFileSystem.delete(Path("$directory/$COVER_FILE"), mustExist = false)
                writeSnapshot(directory, MemoryWidgetSnapshot(state = SIGNED_OUT, refreshedAt = now))
                return true
            }

            // Proceso sin UI (backup en segundo plano): nadie ha lanzado la sonda LAN.
            val urlStore = get<ServerUrlStore>()
            if (!urlStore.isLocalReachable() && !urlStore.getLocal().isNullOrEmpty()) {
                runCatching { get<LocalReachabilityProbe>().runProbe() }
            }

            val memory = pickWidgetMemory(get<PhotonneApi>().getMemoryFeed(limit = FEED_LIMIT), localEpochDay())
            if (memory == null) {
                SystemFileSystem.delete(Path("$directory/$COVER_FILE"), mustExist = false)
                writeSnapshot(directory, MemoryWidgetSnapshot(state = EMPTY, refreshedAt = now))
                return true
            }

            val cover = memory.coverAssetId?.let { downloadCover(urlStore.requireBaseUrl(), it) }
            if (cover != null) {
                writeAtomically("$directory/$COVER_FILE", cover)
            } else {
                SystemFileSystem.delete(Path("$directory/$COVER_FILE"), mustExist = false)
            }
            writeSnapshot(
                directory,
                MemoryWidgetSnapshot(
                    state = MEMORY,
                    memoryId = memory.id,
                    title = memory.title,
                    subtitle = memory.subtitle,
                    url = ExternalNavigation.memoryUrl(memory.id),
                    hasCover = cover != null,
                    refreshedAt = now,
                )
            )
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            NSLog("[MemoryWidget] refresh failed: ${t.message}")
            return false
        } finally {
            running = false
        }
    }

    private suspend fun downloadCover(baseUrl: String, assetId: String): ByteArray? =
        try {
            val response = get<HttpClient>().get("$baseUrl/api/assets/$assetId/thumbnail?size=Medium")
            if (response.status.isSuccess()) response.body<ByteArray>() else null
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            NSLog("[MemoryWidget] cover download failed: ${t.message}")
            null
        }

    private fun readSnapshot(directory: String): MemoryWidgetSnapshot? = runCatching {
        val path = Path("$directory/$SNAPSHOT_FILE")
        if (!SystemFileSystem.exists(path)) return null
        val text = SystemFileSystem.source(path).buffered().use { it.readByteArray() }.decodeToString()
        json.decodeFromString(MemoryWidgetSnapshot.serializer(), text)
    }.getOrNull()

    private fun writeSnapshot(directory: String, snapshot: MemoryWidgetSnapshot) {
        val text = json.encodeToString(MemoryWidgetSnapshot.serializer(), snapshot)
        writeAtomically("$directory/$SNAPSHOT_FILE", text.encodeToByteArray())
    }

    /** El widget puede leer en cualquier momento: nunca ve un archivo a medias. */
    private fun writeAtomically(path: String, bytes: ByteArray) {
        val temp = Path("$path.tmp")
        SystemFileSystem.sink(temp).buffered().use { it.write(bytes) }
        SystemFileSystem.atomicMove(temp, Path(path))
    }

    /** Días desde 1970 en la zona del dispositivo: el mismo día da el mismo recuerdo. */
    private fun localEpochDay(): Long =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()

    private const val MEMORY = "memory"
    private const val EMPTY = "empty"
    private const val SIGNED_OUT = "signedOut"
}
