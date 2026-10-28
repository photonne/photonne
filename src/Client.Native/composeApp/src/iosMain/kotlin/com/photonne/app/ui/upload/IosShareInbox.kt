@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.photonne.app.ui.upload

import com.photonne.app.IosAppGroup
import com.photonne.app.ui.main.ExternalNavigation
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSLog
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.UniformTypeIdentifiers.UTType

/**
 * "Compartir con Photonne" en iOS. La Share Extension (`iosApp/ShareExtension`)
 * no puede abrir la app ni subir nada con la sesión: copia lo compartido a la
 * bandeja del App Group y termina. Al volver la app a primer plano, [drain] se
 * lo lleva a su propio temporal y abre la Subida con esos archivos en cola, el
 * mismo camino que el `ACTION_SEND` de Android. Sin sesión, el buzón de
 * [ExternalNavigation] lo guarda hasta el login.
 *
 * Expuesto como `object` para que Swift lo llame como `IosShareInbox.shared`.
 */
object IosShareInbox {
    /** Carpeta de la bandeja dentro del App Group; la extensión escribe en la misma. */
    private const val INBOX_DIRECTORY = "ShareInbox"

    /**
     * Separa el prefijo de orden que pone la extensión (`<marca>-<n>__`) del
     * nombre original del archivo.
     */
    private const val NAME_SEPARATOR = "__"

    /** Lleva a la Subida lo que haya dejado la extensión. Llamar al pasar a primer plano. */
    fun drain() {
        val container = IosAppGroup.containerPath() ?: return
        val inbox = "$container/$INBOX_DIRECTORY"
        val fileManager = NSFileManager.defaultManager
        val names = (fileManager.contentsOfDirectoryAtPath(inbox, error = null) ?: return)
            .filterIsInstance<String>()
            .filterNot { it.startsWith(".") }
            .sorted()
            .take(MAX_SELECTION)
        if (names.isEmpty()) return

        val files = names.mapNotNull { stored ->
            // Fuera del App Group en cuanto se recoge: la subida lo borra al
            // terminar (TempFileContent.release) y la bandeja queda vacía.
            val target = NSTemporaryDirectory() + "photonne-share-" + NSUUID().UUIDString
            val moved = fileManager.moveItemAtPath("$inbox/$stored", toPath = target, error = null)
            if (!moved) {
                NSLog("[ShareInbox] could not take $stored")
                return@mapNotNull null
            }
            val name = stored.substringAfter(NAME_SEPARATOR, stored)
            val size = (fileManager.attributesOfItemAtPath(target, error = null)
                ?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
            val mime = UTType.typeWithFilenameExtension(name.substringAfterLast('.', ""))
                ?.preferredMIMEType ?: "application/octet-stream"
            PickedFile(
                name = name,
                mimeType = mime,
                sizeBytes = size,
                content = TempFileContent(target)
            )
        }
        NSLog("[ShareInbox] ${files.size} shared file(s) queued for upload")
        ExternalNavigation.requestUpload(files)
    }
}
