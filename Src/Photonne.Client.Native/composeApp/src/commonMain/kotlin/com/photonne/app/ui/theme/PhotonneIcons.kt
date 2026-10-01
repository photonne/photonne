package com.photonne.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddToPhotos
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Un glifo por concepto. La auditoría encontró el mismo concepto dibujado con
 * glifos o familias distintas según la pantalla (Álbum como `Collections` o
 * `PhotoAlbum`, Miembros como `Group` o `People`, Carpeta/Buscar/Editar en
 * Filled y Outlined a la vez, flechas de navegación de dos familias).
 *
 * Canon:
 * - **Outlined por defecto** para toda acción, navegación y concepto.
 * - **Filled solo para estado activo/seleccionado**, siempre en una entrada
 *   `…Active` explícita (pestaña seleccionada de la nav flotante, favorito
 *   marcado, búsqueda/filtro aplicados). Así el relleno significa algo.
 * - Quedan fuera de este objeto (y siguen en Filled a propósito) los glifos de
 *   estado que se leen como sólidos: `CheckCircle`/`Error` en titulares de
 *   estado, el triángulo de reproducir sobre un vídeo, los controles de
 *   reproducción y las insignias superpuestas a una miniatura.
 *
 * Toda pantalla que pinte uno de estos conceptos debe pasar por aquí en vez de
 * elegir un `Icons.*` suelto.
 */
object PhotonneIcons {
    // ── Colecciones ─────────────────────────────────────────────────────
    /** Álbum (pestaña, estados vacíos, selector de tipo). `Collections` se parecía a la pila de Fotos. */
    val Album: ImageVector = Icons.Outlined.PhotoAlbum
    val AlbumActive: ImageVector = Icons.Filled.PhotoAlbum

    /** Fotos / biblioteca (pestaña Fotos, recuento de fotos). */
    val Photos: ImageVector = Icons.Outlined.PhotoLibrary
    val PhotosActive: ImageVector = Icons.Filled.PhotoLibrary

    val Folder: ImageVector = Icons.Outlined.Folder
    val FolderActive: ImageVector = Icons.Filled.Folder
    val NewFolder: ImageVector = Icons.Outlined.CreateNewFolder

    /** Fijar un álbum arriba (acción); `PinActive` marca uno ya fijado. */
    val Pin: ImageVector = Icons.Outlined.PushPin
    val PinActive: ImageVector = Icons.Filled.PushPin

    /** Pestaña «Más» de la nav flotante. */
    val MoreTab: ImageVector = Icons.Outlined.GridView
    val MoreTabActive: ImageVector = Icons.Filled.GridView

    // ── Acciones sobre fotos y colecciones ──────────────────────────────
    val Add: ImageVector = Icons.Outlined.Add
    val AddToAlbum: ImageVector = Icons.Outlined.AddToPhotos

    /** Elegir portada (de álbum o de persona). No reutiliza el glifo de Álbum. */
    val SetCover: ImageVector = Icons.Outlined.Wallpaper
    val Edit: ImageVector = Icons.Outlined.Edit
    val Rename: ImageVector = Icons.Outlined.DriveFileRenameOutline
    val Move: ImageVector = Icons.AutoMirrored.Outlined.DriveFileMove
    val Copy: ImageVector = Icons.Outlined.ContentCopy
    val Share: ImageVector = Icons.Outlined.Share
    val Download: ImageVector = Icons.Outlined.Download
    val Upload: ImageVector = Icons.Outlined.CloudUpload
    val Archive: ImageVector = Icons.Outlined.Archive
    val Unarchive: ImageVector = Icons.Outlined.Unarchive

    /** Sacar de la papelera (una foto o toda la papelera). */
    val Restore: ImageVector = Icons.Outlined.RestoreFromTrash

    /** Mostrar / ocultar algo (persona, carpeta en el timeline). */
    val Show: ImageVector = Icons.Outlined.Visibility
    val Hide: ImageVector = Icons.Outlined.VisibilityOff

    /** Aceptar en bloque una lista de propuestas. */
    val AcceptAll: ImageVector = Icons.Outlined.DoneAll

    /** Descartar o limpiar en bloque (sugerencias, criterios de búsqueda). */
    val ClearAll: ImageVector = Icons.Outlined.ClearAll

    /** Mover a la papelera / quitar. */
    val Delete: ImageVector = Icons.Outlined.Delete

    /** Borrado definitivo, sin papelera. Solo ahí: el glifo avisa de que no hay vuelta atrás. */
    val DeletePermanent: ImageVector = Icons.Outlined.DeleteForever
    val Refresh: ImageVector = Icons.Outlined.Refresh
    val SelectAll: ImageVector = Icons.Outlined.SelectAll
    val Check: ImageVector = Icons.Outlined.Check

    val Favorite: ImageVector = Icons.Outlined.FavoriteBorder
    val FavoriteActive: ImageVector = Icons.Filled.Favorite

    // ── Personas ────────────────────────────────────────────────────────
    val Person: ImageVector = Icons.Outlined.Person

    /** Miembros de un álbum y usuarios del servidor (cuentas). */
    val Members: ImageVector = Icons.Outlined.Group

    /** Personas reconocidas por caras (sección Personas). */
    val People: ImageVector = Icons.Outlined.People

    /** Sugerencias de caras para una persona. */
    val FaceSuggestions: ImageVector = Icons.Outlined.PersonSearch

    /** Fusionar una persona con otra. */
    val Merge: ImageVector = Icons.AutoMirrored.Outlined.MergeType

    // ── Recuerdos ───────────────────────────────────────────────────────
    /** Sección Recuerdos (entradas desde Fotos, Explorar y Buscar). */
    val Memories: ImageVector = Icons.Outlined.History

    // ── Búsqueda, orden y filtros ───────────────────────────────────────
    val Search: ImageVector = Icons.Outlined.Search
    val SearchActive: ImageVector = Icons.Filled.Search
    val Filter: ImageVector = Icons.Outlined.Tune
    val FilterActive: ImageVector = Icons.Filled.Tune
    val Sort: ImageVector = Icons.AutoMirrored.Outlined.Sort

    /** Búsqueda o filtro sin resultados (estados vacíos). */
    val NoResults: ImageVector = Icons.Outlined.SearchOff

    // ── Navegación ──────────────────────────────────────────────────────
    val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack
    val Forward: ImageVector = Icons.AutoMirrored.Outlined.ArrowForward

    /** Fila navegable / nodo plegado. Misma familia (Outlined) que [Back]. */
    val Chevron: ImageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight
    val ChevronDown: ImageVector = Icons.Outlined.KeyboardArrowDown
    val ChevronUp: ImageVector = Icons.Outlined.KeyboardArrowUp
    val ExpandMore: ImageVector = Icons.Outlined.ExpandMore
    val ExpandLess: ImageVector = Icons.Outlined.ExpandLess
    val More: ImageVector = Icons.Outlined.MoreVert
    val Close: ImageVector = Icons.Outlined.Close

    // ── Varios ──────────────────────────────────────────────────────────
    val Info: ImageVector = Icons.Outlined.Info
    val Settings: ImageVector = Icons.Outlined.Settings
    val Logout: ImageVector = Icons.AutoMirrored.Outlined.Logout

    /** Ubicación como concepto (fila de info, tareas, lotes por viaje). */
    val Location: ImageVector = Icons.Outlined.LocationOn

    /** Chincheta sobre un mapa: sólida por diseño, es un marcador y no una acción. */
    val MapPin: ImageVector = Icons.Filled.LocationOn

    // ── Mapa ────────────────────────────────────────────────────────────
    val ZoomIn: ImageVector = Icons.Outlined.Add
    val ZoomOut: ImageVector = Icons.Outlined.Remove

    /** Encuadrar todas las fotos del mapa (antes `Home`, que se leía como "Inicio"). */
    val FitToBounds: ImageVector = Icons.Outlined.ZoomOutMap
}
