package com.photonne.app.ui.image

import coil3.ComponentRegistry

/**
 * Hook for platform actuals to register extra Coil components on the
 * shared [buildPhotonneImageLoader]. Used today by iOS to plug in the
 * PhotoKit fetcher so the Backup grid can render thumbnails for
 * `photokit:` URIs that Coil's built-in fetchers can't recognise.
 */
expect fun ComponentRegistry.Builder.addPlatformImageComponents()
