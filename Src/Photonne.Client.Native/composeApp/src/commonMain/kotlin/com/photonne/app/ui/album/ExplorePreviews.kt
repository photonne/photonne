package com.photonne.app.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.photonne.app.data.people.PeopleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * Contenido real en las tarjetas de Explorar (Álbumes): en vez del icono,
 * una muestra de lo que hay detrás. Solo donde el dato es barato:
 *
 * - Personas: las caras de las tres personas con más fotos (primera página
 *   del listado de Personas, ya ordenado por número de caras).
 * - Mapa: se queda con el icono. Un minimapa de teselas de OSM exige la
 *   atribución sobre el propio mapa (ver MapAttribution) y en una tarjeta de
 *   ~80 dp no cabe legible; además el único origen de "la última foto con
 *   GPS" es la lista completa de puntos del mapa, que no es barata.
 * - Escenas y Objetos: con el icono.
 *
 * Se carga una vez por sesión (el ViewModel vive con ella), nunca bloquea la
 * lista y, mientras carga o si falla, la tarjeta enseña el icono de siempre.
 */

/** Muestras para las tarjetas de Explorar; vacías = icono. */
data class ExplorePreviewsState(
    /** Ids de cara de portada de las personas con más fotos (máx. 3). */
    val peopleFaceIds: List<String> = emptyList(),
)

class ExplorePreviewsViewModel(
    private val peopleRepository: PeopleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExplorePreviewsState())
    val state: StateFlow<ExplorePreviewsState> = _state.asStateFlow()

    private var loaded = false

    /** Idempotente: solo la primera llamada de la sesión pide nada. */
    fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            try {
                // Un poco más de 3: alguna persona puede no tener portada aún.
                val page = peopleRepository.list(limit = PEOPLE_PAGE)
                val faceIds = page.items
                    .filter { !it.isHidden }
                    .mapNotNull { it.coverFaceId }
                    .take(PEOPLE_SHOWN)
                _state.value = _state.value.copy(peopleFaceIds = faceIds)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Sin muestra: la tarjeta sigue con su icono. Se reintenta en
                // la siguiente sesión, no en cada vuelta a la pestaña.
            }
        }
    }

    private companion object {
        const val PEOPLE_PAGE = 6
        const val PEOPLE_SHOWN = 3
    }
}

/** Diámetro de cada cara en la muestra de Personas. */
private val FaceSize = 30.dp

/** Cuánto se monta cada cara sobre la anterior. */
private val FaceOverlap = 10.dp

/**
 * Caras superpuestas (la primera delante), a la altura del círculo de icono
 * de la tarjeta para que no salte al cargar.
 */
@Composable
internal fun OverlappingFaces(faceIds: List<String>, baseUrl: String) {
    val step = FaceSize - FaceOverlap
    Box(
        modifier = Modifier.size(width = FaceSize + step * (faceIds.size - 1), height = 34.dp)
    ) {
        // Se pintan de la última a la primera para que la primera quede encima.
        faceIds.withIndex().reversed().forEach { (index, faceId) ->
            AsyncImage(
                model = "$baseUrl/api/faces/$faceId/thumbnail",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = step * index, y = 2.dp)
                    .size(FaceSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    // Anillo del color de la tarjeta: separa las caras.
                    .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            )
        }
    }
}
