package com.photonne.app.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.data.actions.ServerTrashPolicy
import org.koin.compose.koinInject

/**
 * Whether "Mover a la papelera" really moves to a trash on this server, re-read
 * every time a screen that offers the action appears (an admin may have just
 * switched the trash off). False ⇒ the action deletes for good: confirm as
 * "Eliminar definitivamente" and never offer Deshacer.
 */
@Composable
fun rememberServerTrashEnabled(): Boolean {
    val policy: ServerTrashPolicy = koinInject()
    LaunchedEffect(policy) { policy.refresh() }
    val enabled by policy.enabled.collectAsStateWithLifecycle()
    return enabled
}
