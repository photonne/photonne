@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.photonne.app

import platform.Foundation.NSFileManager

/**
 * El App Group que la app comparte con sus extensiones (la de compartir y el
 * widget de Recuerdos): el único sitio donde las tres pueden leer y escribir.
 * Tiene que coincidir con los `.entitlements` de los tres targets.
 */
object IosAppGroup {
    const val ID = "group.com.photonne.app"

    /** La carpeta del grupo, o null si el binario no lleva el entitlement (p. ej. sin firmar). */
    fun containerPath(): String? =
        NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(ID)
            ?.path
}
