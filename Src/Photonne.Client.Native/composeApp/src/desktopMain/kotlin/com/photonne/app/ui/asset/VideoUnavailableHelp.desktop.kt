package com.photonne.app.ui.asset

// En desktop el motor de vídeo es el libvlc del VLC del sistema: si falta, la
// solución está en manos del usuario y merece un enlace directo.
actual val videoPlaybackUnavailableHelp: VideoPlaybackUnavailableHelp? =
    VideoPlaybackUnavailableHelp(
        message = "Instala VLC para reproducir vídeo en este equipo",
        actionLabel = "Descargar VLC",
        actionUrl = "https://www.videolan.org/vlc/"
    )
