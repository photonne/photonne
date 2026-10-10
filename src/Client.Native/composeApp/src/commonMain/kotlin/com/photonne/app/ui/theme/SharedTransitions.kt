package com.photonne.app.ui.theme

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * Exposes the active [SharedTransitionScope] (provided by the root
 * `SharedTransitionLayout`) to descendants. Grid cells read this so they
 * can register shared-element bounds for thumbnails without having the
 * scope passed through every parameter list.
 *
 * `null` outside the layout (e.g. previews, dialogs).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Asset id currently displayed in the asset viewer's pager, or `null`
 * when the viewer is closed. Drives shared-element visibility — the
 * source thumbnail with this id is "hidden" while the viewer holds the
 * morphed copy, then becomes visible again on close so the morph reverses.
 */
val LocalCurrentDetailAssetId = compositionLocalOf<String?> { null }
