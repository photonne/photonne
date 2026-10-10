package com.photonne.app.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Triggers [action] on a Press event whose primary mouse button is
 * the secondary one (right-click on Desktop, ctrl-click on macOS).
 * On touch platforms there is no secondary button, so this is a
 * no-op there.
 *
 * Use it next to a regular [Modifier.combinedClickable] to provide a
 * desktop equivalent for long-press without altering touch
 * behaviour.
 */
fun Modifier.onSecondaryClick(action: () -> Unit): Modifier = this.pointerInput(action) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                action()
            }
        }
    }
}
