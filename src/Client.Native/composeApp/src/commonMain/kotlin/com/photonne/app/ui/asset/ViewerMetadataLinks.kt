package com.photonne.app.ui.asset

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

/**
 * Where the details of the viewer's info panel lead: each one to the photos
 * that share it. A null callback leaves that detail as plain text (a host that
 * can't show the destination, a trashed photo).
 */
@Immutable
data class ViewerMetadataLinks(
    /** A user tag, or a detected one by its server name ("Panorama"). */
    val onTag: ((String) -> Unit)? = null,
    val onCamera: ((make: String?, model: String) -> Unit)? = null,
    val onPlace: ((placeId: String, name: String) -> Unit)? = null,
    val onFolder: ((folderId: String) -> Unit)? = null,
    /** The timeline at the photo's month. */
    val onMonth: ((capturedAt: Instant) -> Unit)? = null,
    /** The app's own map centred on the photo; without it the minimap opens an external map. */
    val onMap: ((latitude: Double, longitude: Double) -> Unit)? = null,
)
