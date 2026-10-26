package com.photonne.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.photonne.app.data.realtime.ServerEventsListener
import org.koin.compose.koinInject

/**
 * Keeps the server's event stream open while the app is in the foreground
 * (no socket held in the background). Its own file, like [WelcomeHost], so
 * AuthenticatedApp stays under the JVM's 64 KB per method.
 */
@Composable
internal fun RealtimeHost(onNotifications: () -> Unit) {
    val listener: ServerEventsListener = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(listener, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            listener.run(onNotifications)
        }
    }
}
