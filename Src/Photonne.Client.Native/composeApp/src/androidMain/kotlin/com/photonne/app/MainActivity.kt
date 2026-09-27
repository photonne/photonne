package com.photonne.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.photonne.app.ui.main.ExternalDestination
import com.photonne.app.ui.main.ExternalNavigation
import com.photonne.app.ui.platform.OrientationController

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

    /** Lote M4: destino pedido por una notificación (ver [ExternalDestination]). */
    private fun routeFromIntent(intent: Intent?) {
        val destination = ExternalDestination.fromKey(
            intent?.getStringExtra(ExternalDestination.EXTRA_KEY)
        ) ?: return
        intent?.removeExtra(ExternalDestination.EXTRA_KEY)
        ExternalNavigation.request(destination)
    }

    override fun onDestroy() {
        OrientationController.detach(this)
        super.onDestroy()
    }
}
