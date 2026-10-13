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
import app.tellycine.data.repo.XtreamFinder
import app.tellycine.data.settings.SettingsRepository
import app.tellycine.data.settings.TvTheme
import app.tellycine.data.sync.PlaylistSyncer
import app.tellycine.data.sync.SyncManager
import app.tellycine.data.tvmaze.TvmazeClient
import app.tellycine.data.tvmaze.TvmazeUpdater
import app.tellycine.player.TvPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

/** Hand-rolled dependency graph; small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val httpClient = createHttpClient()
    val database = TellycineDatabase.create(context)
    val settings = SettingsRepository(context)
    val downloader = Downloader(context, httpClient)
    /** Air dates from TVmaze, when they're turned on. */
    val tvmaze = TvmazeUpdater(database, settings, TvmazeClient(httpClient), appScope)
    val syncManager = SyncManager(database, PlaylistSyncer(context, database, downloader), settings, appScope, onContentSynced = tvmaze::requestCheck)
    val playlists = PlaylistRepository(context, database, settings, syncManager)
    val content = ContentRepository(database)
    val details = DetailsRepository(downloader)
    /** For checking an M3U link's server while it's typed: a quick answer or none. */
    val xtreamFinder = XtreamFinder(Downloader(context, httpClient.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()))
    val dvr = DvrRepository(database, httpClient)
    val downloads = DownloadRepository(context, database, settings, appScope)
    val cast = CastManager(context)
    /** TV only: the player shared by the full-screen player and the guide's mini player. */
    val tvPlayback by lazy { TvPlayback(context, httpClient) }

    /** The TV interface's colours, ready for its first frame. */
    val tvTheme: StateFlow<TvTheme> = settings.settings
        .map { it.tvTheme }
        .stateIn(appScope, SharingStarted.Eagerly, TvTheme.WARM)

    /** Cached so playback can be launched synchronously from click handlers. */
    val externalPlayerDefault: StateFlow<Boolean> = settings.settings
        .map { it.externalPlayer }
        .stateIn(appScope, SharingStarted.Eagerly, false)
}
