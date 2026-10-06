package app.tube.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.tube.core.ContentKind
import app.tube.data.db.StreamEntity
import app.tube.ui.components.tubeViewModel
import app.tube.ui.detail.DetailScreen
import app.tube.ui.home.HomeViewModel
import app.tube.ui.home.SessionState
import app.tube.ui.library.LibraryScreen
import app.tube.ui.live.LiveScreen
import app.tube.ui.playlists.EditPlaylistScreen
import app.tube.ui.playlists.PlaylistsScreen
import app.tube.ui.search.SearchScreen
import app.tube.ui.settings.SettingsScreen
import app.tube.ui.vod.VodScreen

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    LIVE("Live TV", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    MOVIES("Movies", Icons.Outlined.Movie, Icons.Filled.Movie),
    SERIES("Series", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    LIBRARY("Library", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay, Icons.AutoMirrored.Filled.PlaylistPlay),
}

private object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{kind}/{playlistId}/{streamId}"
    const val EDIT = "playlist/edit?id={id}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    fun detail(s: StreamEntity) = "detail/${s.kind.name}/${s.playlistId}/${Uri.encode(s.streamId)}"
    fun edit(id: Long?) = "playlist/edit?id=${id ?: -1}"
}

@Composable
fun TubeNavHost(nav: NavHostController = rememberNavController()) {
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(nav)
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("playlistId") { type = NavType.LongType },
                navArgument("streamId") { type = NavType.StringType },
            ),
        ) { entry ->
            val args = entry.arguments!!
            DetailScreen(
                kind = ContentKind.valueOf(args.getString("kind")!!),
                playlistId = args.getLong("playlistId"),
                streamId = args.getString("streamId")!!,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            EditPlaylistScreen(editId = id, onDone = { nav.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.detail(it)) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun HomeScreen(nav: NavHostController) {
    val home = tubeViewModel { c, _ -> HomeViewModel(c) }
    val session by home.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.LIVE) }
    val stateHolder = rememberSaveableStateHolder()

    // First run: go straight to playlist setup.
    LaunchedEffect(session.loaded, session.playlists.isEmpty()) {
        if (session.loaded && session.playlists.isEmpty()) tab = Tab.PLAYLISTS
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { t ->
                item(
                    selected = t == tab,
                    onClick = { tab = t },
                    icon = { Icon(if (t == tab) t.selectedIcon else t.icon, null) },
                    label = { Text(t.label) },
                )
            }
        },
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            stateHolder.SaveableStateProvider(tab.name) {
                TabContent(tab, session, nav, onSelectPlaylist = home::select)
            }
        }
    }
}

@Composable
private fun TabContent(tab: Tab, session: SessionState, nav: NavHostController, onSelectPlaylist: (Long) -> Unit) {
    val onSearch = { nav.navigate(Routes.SEARCH) }
    val onOpen = { s: StreamEntity -> nav.navigate(Routes.detail(s)) }
    when (tab) {
        Tab.LIVE -> LiveScreen(session, onSelectPlaylist, onSearch)
        Tab.MOVIES -> VodScreen(ContentKind.MOVIE, session, onSelectPlaylist, onSearch, onOpen)
        Tab.SERIES -> VodScreen(ContentKind.SERIES, session, onSelectPlaylist, onSearch, onOpen)
        Tab.LIBRARY -> LibraryScreen(session, onSelectPlaylist, onSearch, onOpen)
        Tab.PLAYLISTS -> PlaylistsScreen(
            session = session,
            onAdd = { nav.navigate(Routes.edit(null)) },
            onEdit = { nav.navigate(Routes.edit(it)) },
            onSettings = { nav.navigate(Routes.SETTINGS) },
        )
    }
}
