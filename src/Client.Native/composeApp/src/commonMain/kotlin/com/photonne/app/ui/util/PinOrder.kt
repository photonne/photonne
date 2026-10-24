package com.photonne.app.ui.util

import kotlin.time.Instant

/**
 * El orden de "Fijados" que el usuario elige (en la web), uno para álbumes y
 * carpetas: la posición más baja arriba. Lo que no tiene posición va primero:
 * lo recién fijado aquí, que el servidor numera arriba del todo pero aún no
 * hemos vuelto a leer, o todo, con un servidor antiguo. Entre ellos, el último
 * fijado arriba, como antes.
 */
fun <T> List<T>.sortedByPinOrder(position: (T) -> Int?, pinnedAt: (T) -> Instant?): List<T> =
    sortedWith(
        compareBy<T>({ position(it) != null }, { position(it) })
            .thenByDescending { pinnedAt(it) }
    )
