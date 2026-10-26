package com.photonne.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.photonne.app.data.models.UserDto
import com.photonne.app.data.onboarding.WelcomeStore
import com.photonne.app.ui.main.MainTab
import com.photonne.app.ui.onboarding.WelcomeDestination
import com.photonne.app.ui.onboarding.WelcomeSheet
import org.koin.compose.koinInject

/**
 * The welcome after an account's first sign-in on this device. Its own file
 * (and one call from AuthenticatedApp) so that body stays under the JVM's
 * 64 KB per method.
 */
@Composable
internal fun WelcomeHost(user: UserDto, appState: AuthenticatedAppState) {
    val store: WelcomeStore = koinInject()
    var showing by remember(user.id) { mutableStateOf(store.shouldShow(user.id)) }
    if (!showing) return

    fun done() {
        store.markSeen(user.id)
        showing = false
    }

    WelcomeSheet(
        name = user.firstName?.takeIf { it.isNotBlank() } ?: user.username,
        onOpen = { destination ->
            done()
            appState.selectedTab = MainTab.More
            appState.moreSubscreen = when (destination) {
                WelcomeDestination.Backup -> MoreSubscreen.DeviceBackup
                WelcomeDestination.People -> MoreSubscreen.People
                WelcomeDestination.Memories -> MoreSubscreen.Memories
            }
        },
        onDismiss = ::done
    )
}
