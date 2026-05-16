package com.photonne.app.ui.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay

/**
 * Wraps a section so it fades in and slides up 24dp the first time it
 * enters composition. [staggerIndex] delays each subsequent section by
 * 70ms — gives the hub a quick "cascading" feel without dragging out
 * the entrance.
 */
@Composable
fun HubSectionEntry(
    staggerIndex: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(staggerIndex * 70L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 320)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 360),
                initialOffsetY = { it / 4 }
            )
    ) {
        content()
    }
}

/**
 * Compose modifier that scales a card down to 0.96 while pressed and
 * springs back when released — the "aggressive" tap feedback used by
 * every interactive hub element.
 */
@Composable
fun Modifier.hubPressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        scale.animateTo(
            targetValue = if (pressed) 0.96f else 1f,
            animationSpec = tween(durationMillis = if (pressed) 90 else 180)
        )
    }
    return this.scale(scale.value)
}
