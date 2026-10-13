package com.photonne.app.data.settings

import com.photonne.app.data.models.AdminStatsResponse
import com.photonne.app.data.models.AdminUserUsage
import com.photonne.app.data.models.StorageInfoDto
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dónde viven los bytes. Solo [Personal] cuenta para la cuota: las carpetas
 * compartidas son de la casa (las suelen llenar los administradores) y una
 * biblioteca externa es el disco de otro sistema.
 */
enum class StorageSpace { Personal, Shared, Library }

/** Los espacios que se pueden quitar del total; el personal cuenta siempre. */
val HideableStorageSpaces = setOf(StorageSpace.Shared, StorageSpace.Library)

data class SpaceBytes(val personal: Long = 0L, val shared: Long = 0L, val library: Long = 0L) {
    operator fun get(space: StorageSpace): Long = when (space) {
        StorageSpace.Personal -> personal
        StorageSpace.Shared -> shared
        StorageSpace.Library -> library
    }

    val total: Long get() = personal + shared + library

    /** Lo que queda al quitar los espacios ocultos. */
    fun visible(hidden: Set<StorageSpace>): Long =
        StorageSpace.entries.filter { it == StorageSpace.Personal || it !in hidden }.sumOf { get(it) }
}

/**
 * Un servidor anterior no manda el reparto y todo llega a 0: entonces todo es
 * personal, que es como contaba antes.
 */
fun AdminUserUsage.spaceBytes(): SpaceBytes {
    val all = photoBytes + videoBytes
    return if (personalBytes + sharedBytes + libraryBytes == 0L && all > 0L) SpaceBytes(personal = all)
    else SpaceBytes(personalBytes, sharedBytes, libraryBytes)
}

fun AdminStatsResponse.spaceBytes(): SpaceBytes =
    if (personalBytes + sharedBytes + libraryBytes == 0L && totalBytes > 0L) SpaceBytes(personal = totalBytes)
    else SpaceBytes(personalBytes, sharedBytes, libraryBytes)

fun StorageInfoDto.spaceBytes(): SpaceBytes = SpaceBytes(
    personal = personalPhotoBytes + personalVideoBytes,
    shared = sharedPhotoBytes + sharedVideoBytes,
    library = libraries.sumOf { it.photoBytes + it.videoBytes }
)

/**
 * Lee lo guardado. Los nombres desconocidos (y Personal) se descartan: una
 * preferencia rancia cuesta un interruptor, no la pantalla.
 */
internal fun parseHiddenSpaces(raw: String?): Set<StorageSpace> =
    raw.orEmpty().split(',')
        .mapNotNull { name -> runCatching { StorageSpace.valueOf(name.trim()) }.getOrNull() }
        .filter { it in HideableStorageSpaces }
        .toSet()

/**
 * Los espacios que este dispositivo quitó del total. Uno solo para Ajustes →
 * Almacenamiento y el Panel de administración: la elección vale en los dos.
 */
class HiddenStorageSpacesStore(private val settings: Settings) {
    private val _value = MutableStateFlow(parseHiddenSpaces(settings.getStringOrNull(KEY)))
    val value: StateFlow<Set<StorageSpace>> = _value.asStateFlow()

    fun toggle(space: StorageSpace) {
        if (space !in HideableStorageSpaces) return
        val next = if (space in _value.value) _value.value - space else _value.value + space
        settings.putString(KEY, next.joinToString(",") { it.name })
        _value.value = next
    }

    private companion object {
        const val KEY = "photonne.storage.hidden_spaces"
    }
}
