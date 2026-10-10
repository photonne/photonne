package com.photonne.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.photonne.app.ui.main.ExternalDestination
import com.photonne.app.ui.main.ExternalNavigation
import com.photonne.app.ui.platform.OrientationController
import com.photonne.app.ui.upload.MAX_SELECTION
import com.photonne.app.ui.upload.UploadViewModel
import com.photonne.app.ui.upload.readPickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status + navigation bars so they pick up the app's
        // background color instead of staying opaque white. Material3 Scaffold
        // / TopAppBar / NavigationBar apply the system-bar insets automatically.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Let commonMain flip orientation (the video viewer relaxes the
        // manifest's portrait lock). Cleared in onDestroy to avoid leaking
        // this Activity past recreation.
        OrientationController.attach(this)
        // Solo en el arranque de verdad: al recrearse (giro, tema) el intent
        // es el mismo y volvería a saltar a la pantalla ya abandonada.
        if (savedInstanceState == null) routeFromIntent(intent)
        setContent { App() }
    }

    /** La app ya estaba abierta (singleTop): el toque en la notificación llega aquí. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFromIntent(intent)
    }

    /**
     * Lote M4: destino pedido por una notificación (ver [ExternalDestination]).
     * También "Compartir con Photonne" (SEND / SEND_MULTIPLE) y los enlaces
     * `photonne://share/{token}` (VIEW).
     */
    private fun routeFromIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                receiveSharedMedia(intent)
                return
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { ExternalNavigation.handleUrl(it.toString()) }
                return
            }
        }
        val destination = ExternalDestination.fromKey(
            intent?.getStringExtra(ExternalDestination.EXTRA_KEY)
        ) ?: return
        intent?.removeExtra(ExternalDestination.EXTRA_KEY)
        ExternalNavigation.request(destination)
    }

    /**
     * Lo compartido desde otra app se lee YA, mientras dura el permiso temporal
     * que el intent concede sobre esas URI (se pierde al acabar esta Activity),
     * y entra en la cola de Subida. Sin sesión, el buzón lo guarda hasta el login.
     */
    private fun receiveSharedMedia(intent: Intent) {
        val uris = sharedUris(intent)
            .filter { uri ->
                val type = contentResolver.getType(uri) ?: intent.type.orEmpty()
                type.startsWith("image/") || type.startsWith("video/")
            }
            .distinct()
            .take(MAX_SELECTION)
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    readPickedFile(this@MainActivity, uri, UploadViewModel.MAX_BYTES_PER_FILE)
                }
            }
            ExternalNavigation.requestUpload(files)
        }
    }

    private fun sharedUris(intent: Intent): List<Uri> {
        val fromExtras: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            )
            else -> (
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
            ).orEmpty()
        }
        // Algunas apps solo rellenan el ClipData (es lo que lleva el permiso).
        val fromClip = intent.clipData?.let { clip ->
            (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        }.orEmpty()
        return fromExtras + fromClip
    }

    override fun onDestroy() {
        OrientationController.detach(this)
        super.onDestroy()
    }
}
