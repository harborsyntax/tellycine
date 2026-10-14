package app.tellycine.tv

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import app.tellycine.R
import app.tellycine.core.ContentKind
import app.tellycine.data.db.StreamEntity
import app.tellycine.tv.detail.TvDetailScreen
import app.tellycine.tv.downloads.TvDownloadsScreen
import app.tellycine.tv.library.TvLibraryScreen
import app.tellycine.tv.live.TvLiveScreen
import app.tellycine.tv.playlists.TvCategoryFilterScreen
import app.tellycine.tv.playlists.TvEditPlaylistScreen
import app.tellycine.tv.playlists.TvPhoneSetupScreen
import app.tellycine.tv.playlists.TvPlaylistsScreen
import app.tellycine.tv.recordings.TvRecordingsScreen
import app.tellycine.tv.search.TvSearchScreen
import app.tellycine.tv.settings.TvSettingsScreen
import app.tellycine.tv.vod.TvVodScreen
import app.tellycine.ui.components.TellycineLogo
import app.tellycine.ui.components.tellycineViewModel
import app.tellycine.ui.home.HomeViewModel
import app.tellycine.ui.home.SessionState

enum class TvTab(val label: String, val icon: ImageVector) {
    SEARCH("Search", Icons.Filled.Search),
    LIVE("Live TV", Icons.Filled.LiveTv),
    MOVIES("Movies", Icons.Filled.Movie),
    SERIES("Series", Icons.Filled.VideoLibrary),
    LIBRARY("Library", Icons.Filled.Favorite),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
    SETTINGS("Settings", Icons.Filled.Settings),
}

private object TvRoutes {
    const val HOME = "home"
    const val DETAIL = "detail/{kind}/{playlistId}/{streamId}"
    const val EDIT = "playlist/edit?id={id}"
    const val CATEGORIES = "playlist/{id}/categories"
    const val RECORDINGS = "playlist/{id}/recordings"
    const val DOWNLOADS = "downloads"
    const val PHONE_SETUP = "playlist/phone"

    fun detail(s: StreamEntity) = "detail/${s.kind.name}/${s.playlistId}/${Uri.encode(s.streamId)}"
    fun edit(id: Long?) = "playlist/edit?id=${id ?: -1}"
    fun categories(id: Long) = "playlist/$id/categories"
    fun recordings(id: Long) = "playlist/$id/recordings"
}

/** Width of the collapsed side menu, which the screens leave room for. */
private val RailWidth = 80.dp

/** Where the TV interface can go; handed to each screen. */
class TvNavigator(private val nav: NavHostController) {
    fun openDetail(s: StreamEntity) = nav.navigate(TvRoutes.detail(s))
    fun addPlaylist() = nav.navigate(TvRoutes.edit(null))
    fun editPlaylist(id: Long) = nav.navigate(TvRoutes.edit(id))
    fun categories(id: Long) = nav.navigate(TvRoutes.categories(id))
    fun recordings(id: Long) = nav.navigate(TvRoutes.recordings(id))
    fun downloads() = nav.navigate(TvRoutes.DOWNLOADS)
    fun addFromPhone() = nav.navigate(TvRoutes.PHONE_SETUP)
    fun back() = nav.popBackStack()
}

@Composable
fun TvApp() {
    val nav = rememberNavController()
    val navigator = remember(nav) { TvNavigator(nav) }
    Surface(
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxSize(),
    ) {
        NavHost(nav, startDestination = TvRoutes.HOME) {
            composable(TvRoutes.HOME) { TvHome(navigator) }
            composable(
                TvRoutes.DETAIL,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("playlistId") { type = NavType.LongType },
                    navArgument("streamId") { type = NavType.StringType },
                ),
            ) { entry ->
                val args = entry.arguments!!
                TvDetailScreen(
                    kind = ContentKind.valueOf(args.getString("kind")!!),
                    playlistId = args.getLong("playlistId"),
                    streamId = args.getString("streamId")!!,
                    onOpen = navigator::openDetail,
                )
            }
            composable(
                TvRoutes.EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                FullScreen {
                    TvEditPlaylistScreen(
                        editId = entry.arguments?.getLong("id")?.takeIf { it > 0 },
                        onDone = { navigator.back() },
                        onManageCategories = navigator::categories,
                    )
                }
            }
            composable(TvRoutes.CATEGORIES, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                FullScreen { TvCategoryFilterScreen(playlistId = entry.arguments!!.getLong("id")) }
            }
            composable(TvRoutes.RECORDINGS, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                FullScreen { TvRecordingsScreen(playlistId = entry.arguments!!.getLong("id")) }
            }
            composable(TvRoutes.DOWNLOADS) { FullScreen { TvDownloadsScreen() } }
            composable(TvRoutes.PHONE_SETUP) { FullScreen { TvPhoneSetupScreen(onDone = { navigator.back() }) } }
        }
    }
}

/** Screens opened on top of the menu, kept clear of the left edge (which TVs can crop) like the others. */
@Composable
private fun FullScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(start = 32.dp)) { content() }
}

@Composable
private fun TvHome(navigator: TvNavigator) {
    val home = tellycineViewModel { c, _ -> HomeViewModel(c) }
    val session by home.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(TvTab.LIVE) }
    val stateHolder = rememberSaveableStateHolder()

    // Movies and Series disappear for playlists with VOD turned off.
    val vodEnabled = session.active?.vodEnabled != false
    val tabs = TvTab.entries.filter { vodEnabled || (it != TvTab.MOVIES && it != TvTab.SERIES) }
    LaunchedEffect(vodEnabled) {
        if (tab !in tabs) tab = TvTab.LIVE
    }
    // First run: go straight to playlist setup.
    LaunchedEffect(session.loaded, session.playlists.isEmpty()) {
        if (session.loaded && session.playlists.isEmpty()) tab = TvTab.PLAYLISTS
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val itemFocus = remember { TvTab.entries.associateWith { FocusRequester() } }
    val contentFocus = remember { FocusRequester() }
    // Bumped to move focus into the content once a newly chosen screen is on display.
    var focusContent by remember { mutableIntStateOf(1) }
    LaunchedEffect(focusContent) {
        repeat(4) {
            withFrameNanos { }
            if (runCatching { contentFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }

    // The guide uses the rail's width too: the rail is hidden (but still there for Back) while it shows.
    var fullWidth by remember { mutableStateOf(false) }
    val hideRail = fullWidth && tab == TvTab.LIVE

    // Back from a screen opens the menu; Back again (from the menu) leaves the app.
    BackHandler(enabled = drawerState.currentValue == DrawerValue.Closed) {
        runCatching { itemFocus.getValue(tab).requestFocus() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { value ->
            val open = value == DrawerValue.Open
            Column(
                Modifier
                    .fillMaxHeight()
                    .graphicsLayer { alpha = if (hideRail && !open) 0f else 1f }
                    .background(if (open) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp, vertical = 24.dp)
                    .selectableGroup()
                    // Entering the menu lands on the current screen's item.
                    .focusProperties { onEnter = { runCatching { itemFocus.getValue(tab).requestFocus() } } },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.height(48.dp).padding(start = 12.dp)) {
                    if (open) {
                        TellycineLogo(height = 30.dp)
                    } else {
                        Image(painterResource(R.drawable.tellycine_mark), contentDescription = "Tellycine", modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                tabs.forEach { t ->
                    if (t == TvTab.SETTINGS) Spacer(Modifier.weight(1f))
                    NavigationDrawerItem(
                        selected = t == tab,
                        onClick = {
                            tab = t
                            focusContent++
                        },
                        leadingContent = { Icon(t.icon, null) },
                        supportingContent = if (t == TvTab.PLAYLISTS) {
                            session.active?.name?.let { name -> { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .focusRequester(itemFocus.getValue(t))
                            // Right goes back to the screen, which may start under the menu (the guide does).
                            .focusProperties { right = contentFocus },
                    ) {
                        Text(t.label, maxLines = 1)
                    }
                }
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = if (hideRail) 0.dp else RailWidth)
                .focusRequester(contentFocus)
                .focusRestorer()
                .focusGroup(),
        ) {
            stateHolder.SaveableStateProvider(tab.name) {
                TvTabContent(
                    tab, session, navigator,
                    onSelectPlaylist = home::select,
                    onOpenTab = { tab = it; focusContent++ },
                    onFullWidth = { fullWidth = it },
                )
            }
        }
    }
}

@Composable
private fun TvTabContent(
    tab: TvTab,
    session: SessionState,
    navigator: TvNavigator,
    onSelectPlaylist: (Long) -> Unit,
    onOpenTab: (TvTab) -> Unit,
    onFullWidth: (Boolean) -> Unit,
) {
    when (tab) {
        TvTab.SEARCH -> TvSearchScreen(onOpen = navigator::openDetail)
        TvTab.LIVE -> TvLiveScreen(session, onAddPlaylist = { onOpenTab(TvTab.PLAYLISTS) }, onFullWidth = onFullWidth)
        TvTab.MOVIES -> TvVodScreen(ContentKind.MOVIE, session, onOpen = navigator::openDetail)
        TvTab.SERIES -> TvVodScreen(ContentKind.SERIES, session, onOpen = navigator::openDetail)
        TvTab.LIBRARY -> TvLibraryScreen(
            session = session,
            onOpen = navigator::openDetail,
            onOpenRecordings = navigator::recordings,
            onOpenDownloads = navigator::downloads,
            onConnectDvr = navigator::editPlaylist,
        )
        TvTab.PLAYLISTS -> TvPlaylistsScreen(
            session = session,
            onSelect = onSelectPlaylist,
            onAdd = navigator::addPlaylist,
            onAddFromPhone = navigator::addFromPhone,
            onEdit = navigator::editPlaylist,
            onCategories = navigator::categories,
        )
        TvTab.SETTINGS -> TvSettingsScreen(onOpenDownloads = navigator::downloads)
    }
}
