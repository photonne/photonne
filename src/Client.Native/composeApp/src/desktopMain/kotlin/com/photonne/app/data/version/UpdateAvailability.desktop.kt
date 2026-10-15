package com.photonne.app.data.version

// Los instaladores DMG/MSI/DEB se publican como assets de las releases de la
// app (native-vX.Y.Z). releases/latest no vale: es la última del servidor.
actual val clientUpdateUrl: String? =
    "https://github.com/photonne/photonne/releases?q=native-v&expanded=true"
