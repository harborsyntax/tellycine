package app.tellycine

import android.content.Context
import app.tellycine.cast.CastManager
import app.tellycine.data.db.TellycineDatabase
import app.tellycine.data.net.Downloader
import app.tellycine.data.net.createHttpClient
import app.tellycine.data.repo.ContentRepository
import app.tellycine.data.repo.DetailsRepository
import app.tellycine.data.repo.DownloadRepository
import app.tellycine.data.repo.DvrRepository
import app.tellycine.data.repo.PlaylistRepository
import app.tellycine.data.settings.SettingsRepository
import app.tellycine.data.sync.PlaylistSyncer
import app.tellycine.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Hand-rolled dependency graph; small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val httpClient = createHttpClient()
    val database = TellycineDatabase.create(context)
    val settings = SettingsRepository(context)
    val downloader = Downloader(context, httpClient)
    val syncManager = SyncManager(database, PlaylistSyncer(context, database, downloader), settings, appScope)
    val playlists = PlaylistRepository(context, database, settings, syncManager)
    val content = ContentRepository(database)
    val details = DetailsRepository(downloader)
    val dvr = DvrRepository(database, httpClient)
    val downloads = DownloadRepository(context, database, settings, appScope)
    val cast = CastManager(context)

    /** Cached so playback can be launched synchronously from click handlers. */
    val externalPlayerDefault: StateFlow<Boolean> = settings.settings
        .map { it.externalPlayer }
        .stateIn(appScope, SharingStarted.Eagerly, false)
}
