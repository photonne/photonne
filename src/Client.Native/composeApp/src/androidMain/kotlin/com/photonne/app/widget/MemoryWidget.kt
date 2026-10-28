package com.photonne.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.photonne.app.MainActivity
import com.photonne.app.R
import com.photonne.app.data.api.LocalReachabilityProbe
import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.models.Memory
import com.photonne.app.data.timeline.WIDGET_ROTATION_SIZE
import com.photonne.app.data.timeline.pickWidgetMemory
import com.photonne.app.ui.main.ExternalNavigation
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext

/**
 * Widget de Recuerdos en la pantalla de inicio: la portada de un recuerdo con su
 * título; tocarlo abre ese recuerdo en la app (`photonne://memory/{id}`).
 *
 * Es un `AppWidgetProvider` con `RemoteViews`, sin Glance: no hace falta otra
 * dependencia para una foto y dos líneas de texto. El contenido lo prepara
 * [MemoryWidgetWorker] (red, sesión, descarga de la portada) y el proveedor solo
 * lo pide.
 */
class MemoryWidgetProvider : AppWidgetProvider() {
    /** Al añadir un widget, al reiniciar el móvil y cuando el sistema lo pide. */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        MemoryWidget.refreshNow(context)
    }

    /** El primer widget de Recuerdos de la pantalla de inicio. */
    override fun onEnabled(context: Context) {
        MemoryWidget.schedule(context)
    }

    /** Se ha quitado el último: nada que refrescar. */
    override fun onDisabled(context: Context) {
        MemoryWidget.cancel(context)
    }
}

object MemoryWidget {
    private const val TAG = "MemoryWidget"
    private const val PERIODIC_WORK = "photonne.memoryWidget.periodic"
    private const val REFRESH_WORK = "photonne.memoryWidget.refresh"
    private const val PREFS = "photonne.memoryWidget"
    private const val KEY_LAST_REFRESH = "lastRefreshMs"
    private const val KEY_SIGNED_IN = "signedIn"

    // El recuerdo cambia una vez al día; refrescar más a menudo solo recoge los
    // que genera la pasada nocturna del servidor y un cambio de sesión.
    private const val PERIOD_HOURS = 6L
    // Al salir de la app se refresca si ha pasado al menos esto (ver refreshIfStale).
    private const val STALE_AFTER_MS = 60L * 60 * 1000

    // Las solicitudes de PendingIntent del widget; distintas de las notificaciones.
    private const val REQUEST_OPEN_MEMORY = 7101
    private const val REQUEST_OPEN_APP = 7102

    internal fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, MemoryWidgetProvider::class.java))

    private fun networkConstraints(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MemoryWidgetWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .build()
        // KEEP: volver a programar no reinicia la cuenta atrás.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(REFRESH_WORK)
    }

    /** Refresca ya (en cuanto haya red) y asegura el refresco periódico. */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<MemoryWidgetWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(REFRESH_WORK, ExistingWorkPolicy.REPLACE, request)
        schedule(context)
    }

    /**
     * Al salir de la app: si hay un widget y la sesión ha cambiado desde que se
     * pintó (un cierre de sesión no puede dejar fotos a la vista) o lleva más de
     * una hora sin refrescarse, lo refresca sin esperar a la pasada periódica.
     */
    fun refreshIfStale(context: Context) {
        if (widgetIds(context).isEmpty()) return
        val prefs = prefs(context)
        val signedIn = runCatching {
            GlobalContext.get().get<TokenStorage>().hasSession()
        }.getOrDefault(false)
        val sessionChanged = prefs.getBoolean(KEY_SIGNED_IN, false) != signedIn
        val last = prefs.getLong(KEY_LAST_REFRESH, 0L)
        if (!sessionChanged && System.currentTimeMillis() - last < STALE_AFTER_MS) return
        if (!signedIn) {
            // Sin red también: tapar lo que hubiera no espera a WorkManager.
            render(context, widgetIds(context), Content.SignedOut)
            markRefreshed(context, signedIn = false)
            return
        }
        refreshNow(context)
    }

    internal fun markRefreshed(context: Context, signedIn: Boolean) {
        prefs(context).edit()
            .putLong(KEY_LAST_REFRESH, System.currentTimeMillis())
            .putBoolean(KEY_SIGNED_IN, signedIn)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Lo que enseña el widget. */
    internal sealed interface Content {
        /** Sin sesión: invita a abrir la app. */
        data object SignedOut : Content

        /** Con sesión pero sin recuerdos con portada todavía. */
        data object Empty : Content

        /** [cover] null si no se pudo bajar: queda el texto sobre el fondo. */
        class Showing(val memory: Memory, val cover: Bitmap?) : Content
    }

    internal fun render(context: Context, ids: IntArray, content: Content) {
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_memory)
        when (content) {
            Content.SignedOut, Content.Empty -> {
                views.setViewVisibility(R.id.widget_cover, View.GONE)
                views.setViewVisibility(R.id.widget_scrim, View.GONE)
                views.setTextViewText(R.id.widget_title, context.getString(R.string.memory_widget_label))
                views.setTextViewText(
                    R.id.widget_subtitle,
                    context.getString(
                        if (content == Content.SignedOut) R.string.memory_widget_signed_out
                        else R.string.memory_widget_empty
                    )
                )
                views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                openAppIntent(context)?.let { views.setOnClickPendingIntent(R.id.widget_root, it) }
            }
            is Content.Showing -> {
                val cover = content.cover
                if (cover != null) {
                    views.setImageViewBitmap(R.id.widget_cover, cover)
                    views.setViewVisibility(R.id.widget_cover, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_cover, View.GONE)
                    views.setViewVisibility(R.id.widget_scrim, View.GONE)
                }
                views.setTextViewText(R.id.widget_title, content.memory.title)
                val subtitle = content.memory.subtitle
                if (subtitle.isNullOrBlank()) {
                    views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                } else {
                    views.setTextViewText(R.id.widget_subtitle, subtitle)
                    views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                }
                views.setOnClickPendingIntent(R.id.widget_root, openMemoryIntent(context, content.memory.id))
            }
        }
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
        Log.i(TAG, "Rendered ${content::class.simpleName} on ${ids.size} widget(s)")
    }

    /** Abre el recuerdo por el mismo camino que un enlace `photonne://`. */
    private fun openMemoryIntent(context: Context, memoryId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(ExternalNavigation.memoryUrl(memoryId)))
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        return PendingIntent.getActivity(
            context, REQUEST_OPEN_MEMORY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context, REQUEST_OPEN_APP, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Prepara el contenido del widget: con sesión pide los mejores recuerdos, elige
 * el del día ([pickWidgetMemory]) y baja su portada. Si el servidor no responde
 * deja lo que ya se veía y lo intenta en la siguiente pasada.
 */
class MemoryWidgetWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val ids = MemoryWidget.widgetIds(context)
        if (ids.isEmpty()) return Result.success()
        val koin = runCatching { GlobalContext.get() }.getOrNull() ?: return Result.success()

        val tokenStorage: TokenStorage = koin.get()
        if (!tokenStorage.hasSession()) {
            MemoryWidget.render(context, ids, MemoryWidget.Content.SignedOut)
            MemoryWidget.markRefreshed(context, signedIn = false)
            return Result.success()
        }

        // Proceso sin UI: nadie ha lanzado la sonda LAN (ver BackupWorker).
        val urlStore: ServerUrlStore = koin.get()
        if (!urlStore.isLocalReachable() && !urlStore.getLocal().isNullOrEmpty()) {
            runCatching { koin.get<LocalReachabilityProbe>().runProbe() }
        }

        val memory = try {
            // Solo los mejores: el widget rota entre unos pocos y no necesita el feed entero.
            val feed = koin.get<PhotonneApi>().getMemoryFeed(limit = WIDGET_FEED_LIMIT)
            pickWidgetMemory(feed, LocalDate.now().toEpochDay())
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.i(TAG, "memory feed failed: ${t::class.simpleName}")
            return Result.success()
        }

        if (memory == null) {
            MemoryWidget.render(context, ids, MemoryWidget.Content.Empty)
        } else {
            val cover = memory.coverAssetId?.let { assetId ->
                loadCover(koin.get(), urlStore.requireBaseUrl(), assetId)
            }
            MemoryWidget.render(context, ids, MemoryWidget.Content.Showing(memory, cover))
        }
        MemoryWidget.markRefreshed(context, signedIn = true)
        return Result.success()
    }

    /** La miniatura mediana (640 px): nítida en un widget sin acercarse al límite de RemoteViews. */
    private suspend fun loadCover(client: HttpClient, baseUrl: String, assetId: String): Bitmap? =
        try {
            val response = client.get("$baseUrl/api/assets/$assetId/thumbnail?size=Medium")
            if (!response.status.isSuccess()) {
                null
            } else {
                val bytes: ByteArray = response.body()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.i(TAG, "cover download failed: ${t::class.simpleName}")
            null
        }

    private companion object {
        const val TAG = "MemoryWidget"
        // Algo más que los que rotan, para tener margen si alguno no tiene portada.
        const val WIDGET_FEED_LIMIT = WIDGET_ROTATION_SIZE * 2
    }
}
