package app.tube

import android.content.Context
import app.tube.data.db.TubeDatabase
import app.tube.data.net.Downloader
import app.tube.data.net.createHttpClient
import app.tube.data.repo.ContentRepository
import app.tube.data.repo.DetailsRepository
import app.tube.data.repo.PlaylistRepository
import app.tube.data.settings.SettingsRepository
import app.tube.data.sync.PlaylistSyncer
import app.tube.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled dependency graph; small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val httpClient = createHttpClient()
    val database = TubeDatabase.create(context)
    val settings = SettingsRepository(context)
    val downloader = Downloader(context, httpClient)
    val syncManager = SyncManager(database, PlaylistSyncer(context, database, downloader), settings, appScope)
    val playlists = PlaylistRepository(database, settings, syncManager)
    val content = ContentRepository(database)
    val details = DetailsRepository(downloader)
}
