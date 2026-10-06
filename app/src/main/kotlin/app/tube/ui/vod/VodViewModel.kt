package app.tube.ui.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.tube.AppContainer
import app.tube.core.ContentKind
import app.tube.data.db.CategoryEntity
import app.tube.data.db.StreamEntity
import app.tube.data.repo.Categories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs both the Movies and the Series tabs. */
@OptIn(ExperimentalCoroutinesApi::class)
class VodViewModel(container: AppContainer, val kind: ContentKind) : ViewModel() {
    private val content = container.content
    private val playlistId = container.playlists.activePlaylist.map { it?.id }.distinctUntilChanged()

    private val _selected = MutableStateFlow(Categories.ALL)
    val selectedCategory: StateFlow<String> = _selected.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = playlistId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else content.categories(id, kind) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: Flow<PagingData<StreamEntity>> = combine(playlistId, _selected) { id, cat -> id to cat }
        .flatMapLatest { (id, cat) -> if (id == null) flowOf(PagingData.empty()) else content.items(id, kind, cat) }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            var last: Long? = null
            playlistId.collect { id ->
                if (last != null && id != last) _selected.value = Categories.ALL
                last = id
            }
        }
    }

    fun selectCategory(id: String) {
        _selected.value = id
    }
}
