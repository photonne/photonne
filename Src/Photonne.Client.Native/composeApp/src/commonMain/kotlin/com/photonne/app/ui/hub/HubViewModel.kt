package com.photonne.app.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.account.AccountRepository
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.Person
import com.photonne.app.data.models.StorageInfoDto
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.people.PeopleRepository
import com.photonne.app.data.search.SearchRepository
import com.photonne.app.data.timeline.MemoriesRepository
import com.photonne.app.data.timeline.TimelineRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HubFacetKind { Scene, ObjectLabel }

data class HubFacet(
    val label: String,
    val kind: HubFacetKind,
    val assetCount: Int,
    val coverAssetId: String? = null,
    val coverHasThumbnail: Boolean = false
)

data class HubUiState(
    val isLoading: Boolean = false,
    val attempted: Boolean = false,
    val error: UiError? = null,
    val displayName: String? = null,
    val storage: StorageInfoDto? = null,
    val memories: List<TimelineItem> = emptyList(),
    val recents: List<TimelineItem> = emptyList(),
    val people: List<Person> = emptyList(),
    val facets: List<HubFacet> = emptyList()
)

/**
 * Aggregates the data shown on the Inicio hub: account totals, memories,
 * recent photos, top people and top scene/object facets. Each section loads
 * in parallel; a single failure surfaces as [HubUiState.error] but
 * does not prevent the rest of the hub from rendering with what we have.
 */
class HubViewModel(
    private val accountRepository: AccountRepository,
    private val memoriesRepository: MemoriesRepository,
    private val timelineRepository: TimelineRepository,
    private val peopleRepository: PeopleRepository,
    private val searchRepository: SearchRepository,
    private val authStateHolder: AuthStateHolder,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(HubUiState())
    val state: StateFlow<HubUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val displayName = (authStateHolder.state.value as? AuthState.Authenticated)?.user?.let { u ->
                val full = listOfNotNull(u.firstName, u.lastName)
                    .joinToString(" ")
                    .trim()
                if (full.isNotBlank()) full else u.username
            }

            val result = runCatching {
                coroutineScope {
                    val storageJob = async { runCatching { accountRepository.getStorageInfo() } }
                    val memoriesJob = async { runCatching { memoriesRepository.list() } }
                    val recentsJob = async {
                        // Slim hub-only endpoint: returns the N most recent
                        // visible assets without paying the timeline's
                        // filesystem-scan / tag-join cost. Previously the hub
                        // re-used the full timeline call here, which meant
                        // any first-page slowness on the library also wiped
                        // the recents row and the library shortcut.
                        runCatching { timelineRepository.loadRecent(limit = RECENTS_COUNT) }
                    }
                    val peopleJob = async {
                        runCatching {
                            peopleRepository.list(includeHidden = false, limit = PEOPLE_COUNT, offset = 0).items
                        }
                    }
                    val scenesJob = async { runCatching { searchRepository.sceneLabels(limit = SCENE_TOP) } }
                    val objectsJob = async { runCatching { searchRepository.objectLabels(limit = OBJECT_TOP) } }

                    val storageResult = storageJob.await()
                    val memoriesResult = memoriesJob.await()
                    val recentsResult = recentsJob.await()
                    val peopleResult = peopleJob.await()
                    val sceneList = scenesJob.await().getOrDefault(emptyList())
                    val objectList = objectsJob.await().getOrDefault(emptyList())

                    val facets = buildFacets(sceneList, objectList)
                    val facetsWithCovers = enrichFacetCovers(facets)

                    HubLoadResult(
                        storage = storageResult.getOrNull(),
                        memories = memoriesResult.getOrDefault(emptyList()),
                        recents = recentsResult.getOrDefault(emptyList()),
                        people = peopleResult.getOrDefault(emptyList()),
                        facets = facetsWithCovers,
                        firstError = listOf<Result<*>>(
                            storageResult, memoriesResult, recentsResult, peopleResult
                        ).firstNotNullOfOrNull { it.exceptionOrNull() }
                    )
                }
            }

            result
                .onSuccess { load ->
                    _state.value = HubUiState(
                        isLoading = false,
                        attempted = true,
                        error = load.firstError?.let {
                            errorFactory.from(it, "No se pudo cargar el inicio")
                        },
                        displayName = displayName,
                        storage = load.storage,
                        memories = load.memories,
                        recents = load.recents,
                        people = load.people,
                        facets = load.facets
                    )
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            attempted = true,
                            error = errorFactory.from(err, "Error cargando el inicio")
                        )
                    }
                }
        }
    }

    private fun buildFacets(
        scenes: List<com.photonne.app.data.models.SceneLabel>,
        objects: List<com.photonne.app.data.models.ObjectLabel>
    ): List<HubFacet> {
        val sceneFacets = scenes.take(SCENE_TOP).map {
            HubFacet(label = it.label, kind = HubFacetKind.Scene, assetCount = it.assetCount)
        }
        val objectFacets = objects.take(OBJECT_TOP).map {
            HubFacet(label = it.label, kind = HubFacetKind.ObjectLabel, assetCount = it.assetCount)
        }
        return (sceneFacets + objectFacets).take(FACET_TOTAL)
    }

    /**
     * For each facet, fetches a single representative asset thumbnail by
     * issuing a 1-item search filtered to the facet. Failures are tolerated:
     * facets without a cover just render the label without a thumbnail.
     */
    private suspend fun enrichFacetCovers(facets: List<HubFacet>): List<HubFacet> = coroutineScope {
        facets.map { facet ->
            async {
                val response = runCatching {
                    when (facet.kind) {
                        HubFacetKind.Scene -> searchRepository.textSearch(
                            query = null, from = null, to = null, personIds = emptyList(),
                            objectLabels = emptyList(),
                            sceneLabels = listOf(facet.label),
                            pageSize = 1, offset = 0
                        )
                        HubFacetKind.ObjectLabel -> searchRepository.textSearch(
                            query = null, from = null, to = null, personIds = emptyList(),
                            objectLabels = listOf(facet.label),
                            sceneLabels = emptyList(),
                            pageSize = 1, offset = 0
                        )
                    }
                }.getOrNull()
                val cover = response?.items?.firstOrNull()
                facet.copy(
                    coverAssetId = cover?.id,
                    coverHasThumbnail = cover?.hasThumbnails ?: false
                )
            }
        }.awaitAll()
    }

    private data class HubLoadResult(
        val storage: StorageInfoDto?,
        val memories: List<TimelineItem>,
        val recents: List<TimelineItem>,
        val people: List<Person>,
        val facets: List<HubFacet>,
        val firstError: Throwable?
    )

    private companion object {
        const val RECENTS_COUNT = 10
        const val PEOPLE_COUNT = 8
        const val SCENE_TOP = 6
        const val OBJECT_TOP = 4
        const val FACET_TOTAL = 8
    }
}
