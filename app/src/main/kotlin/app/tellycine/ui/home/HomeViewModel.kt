package app.tellycine.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tellycine.AppContainer
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.sync.SyncProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionState(
    val loaded: Boolean = false,
    val playlists: List<PlaylistEntity> = emptyList(),
    val active: PlaylistEntity? = null,
    val activeProgress: SyncProgress? = null,
)

/** Shared state for the top-level tabs: playlists, the active playlist and its sync status. */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<SessionState> = combine(
        container.playlists.playlists,
        container.playlists.activePlaylist,
        container.syncManager.progress,
    ) { list, active, progress ->
        SessionState(true, list, active, active?.let { progress[it.id] })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState())

    fun select(playlistId: Long) {
        viewModelScope.launch { container.playlists.setActive(playlistId) }
    }
}
