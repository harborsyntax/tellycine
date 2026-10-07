package app.mosaic

import android.content.Context
import app.mosaic.cast.CastManager
import app.mosaic.data.db.MosaicDatabase
import app.mosaic.data.net.Downloader
import app.mosaic.data.net.createHttpClient
import app.mosaic.data.repo.ContentRepository
import app.mosaic.data.repo.DetailsRepository
import app.mosaic.data.repo.DvrRepository
import app.mosaic.data.repo.PlaylistRepository
import app.mosaic.data.settings.SettingsRepository
import app.mosaic.data.sync.PlaylistSyncer
import app.mosaic.data.sync.SyncManager
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
    val database = MosaicDatabase.create(context)
    val settings = SettingsRepository(context)
    val downloader = Downloader(context, httpClient)
    val syncManager = SyncManager(database, PlaylistSyncer(context, database, downloader), settings, appScope)
    val playlists = PlaylistRepository(database, settings, syncManager)
    val content = ContentRepository(database)
    val details = DetailsRepository(downloader)
    val dvr = DvrRepository(database, httpClient)
    val cast = CastManager(context)

    /** Cached so playback can be launched synchronously from click handlers. */
    val externalPlayerDefault: StateFlow<Boolean> = settings.settings
        .map { it.externalPlayer }
        .stateIn(appScope, SharingStarted.Eagerly, false)
}
