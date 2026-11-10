package app.tellycine.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tellycine.AppContainer
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.sync.SyncProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionState(
    val loaded: Boolean = false,
    val playlists: List<PlaylistEntity> = emptyList(),
    val active: PlaylistEntity? = null,
    val activeProgress: SyncProgress? = null,
    /** The Sports Hub is turned on: its tab and menu item show. */
    val sportsHub: Boolean = true,
)

/** Shared state for the top-level tabs: playlists, the active playlist and its sync status. */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<SessionState> = combine(
        container.playlists.playlists,
        container.playlists.activePlaylist,
        container.syncManager.progress,
        container.settings.settings.map { it.sportsHub }.distinctUntilChanged(),
    ) { list, active, progress, sports ->
        SessionState(true, list, active, active?.let { progress[it.id] }, sports)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState())

    fun select(playlistId: Long) {
        viewModelScope.launch { container.playlists.setActive(playlistId) }
    }
}
