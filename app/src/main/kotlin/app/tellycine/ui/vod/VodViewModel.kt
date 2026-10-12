package app.tellycine.ui.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.tellycine.AppContainer
import app.tellycine.core.ContentKind
import app.tellycine.data.db.CategoryEntity
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.Categories
import app.tellycine.data.repo.PlaylistFilter
import app.tellycine.data.repo.PlaylistScope
import app.tellycine.data.settings.VodSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs both the Movies and the Series tabs. */
@OptIn(ExperimentalCoroutinesApi::class)
class VodViewModel(private val container: AppContainer, val kind: ContentKind) : ViewModel() {
    private val content = container.content
    private val allPlaylists = container.playlists.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The playlists this tab shows: the one chosen, or all of them together. */
    val scope: StateFlow<PlaylistScope> = container.playlists.scope(kind)
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistScope.EMPTY)
    private val scopeIds = scope.map { it.ids }.distinctUntilChanged()

    /** "All playlists" and each playlist, to choose from; null with only one playlist. */
    val playlistFilter: StateFlow<PlaylistFilter?> = combine(allPlaylists, scope) { all, s -> PlaylistFilter.of(all, s) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectPlaylists(id: Long) {
        viewModelScope.launch { container.playlists.setSectionPlaylists(kind, id) }
    }

    /** The name of the playlist [item] is from, when it's one of several shown together. */
    fun sourceOf(item: StreamEntity): String? = scope.value.takeIf { it.combined }?.playlists?.firstOrNull { it.id == item.playlistId }?.name

    private val _selected = MutableStateFlow(Categories.ALL)
    val selectedCategory: StateFlow<String> = _selected.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = scopeIds
        .flatMapLatest { ids -> content.categories(ids, kind) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The order titles are shown in, and the choices for this tab. */
    val sort: StateFlow<VodSort> = container.settings.settings.map { it.vodSort(kind) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, VodSort.PROVIDER)
    val sortChoices: List<VodSort> = VodSort.choices(kind)

    fun setSort(sort: VodSort) {
        viewModelScope.launch { container.settings.setVodSort(kind, sort) }
    }

    val items: Flow<PagingData<StreamEntity>> = combine(scopeIds, _selected, sort) { ids, cat, order -> Triple(ids, cat, order) }
        .flatMapLatest { (ids, cat, order) -> content.items(ids, kind, cat, order) }
        .cachedIn(viewModelScope)

    init {
        // Back to all of them when the playlists shown change.
        viewModelScope.launch {
            var last: List<Long>? = null
            scopeIds.collect { ids ->
                if (last != null && ids != last) _selected.value = Categories.ALL
                last = ids
            }
        }
    }

    fun selectCategory(id: String) {
        _selected.value = id
    }
}
