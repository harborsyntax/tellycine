package app.tellycine.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tellycine.AppContainer
import app.tellycine.core.ContentKind
import app.tellycine.core.xtream.XtreamUrls
import app.tellycine.data.db.DownloadEntity
import app.tellycine.data.db.HistoryEntity
import app.tellycine.data.db.PlaylistEntity
import app.tellycine.data.db.PlaylistType
import app.tellycine.data.db.StreamEntity
import app.tellycine.data.repo.ContentRepository
import app.tellycine.data.repo.DownloadRequest
import app.tellycine.player.PlayRequest
import app.tellycine.ui.components.formatDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpisodeUi(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val plot: String?,
    val image: String?,
    val durationSecs: Int?,
    val url: String,
)

data class DetailInfo(
    val loading: Boolean = true,
    val stream: StreamEntity? = null,
    val playlist: PlaylistEntity? = null,
    val title: String = "",
    val poster: String? = null,
    /** Wide artwork for behind the page, shown in turn when there's more than one. */
    val backdrops: List<String> = emptyList(),
    val plot: String? = null,
    val meta: List<String> = emptyList(),
    val rating: Double? = null,
    val cast: String? = null,
    val director: String? = null,
    val seasons: Map<Int, String> = emptyMap(),
    /** Season posters, for the seasons the provider has one for. */
    val seasonCovers: Map<Int, String> = emptyMap(),
    /** The trailer's YouTube address. */
    val trailer: String? = null,
    /** The playlist the title is from, when there's more than one. */
    val source: String? = null,
    val episodes: List<EpisodeUi> = emptyList(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    private val container: AppContainer,
    val kind: ContentKind,
    private val playlistId: Long,
    private val streamId: String,
) : ViewModel() {
    private val content = container.content

    private val _info = MutableStateFlow(DetailInfo())
    val info: StateFlow<DetailInfo> = _info.asStateFlow()

    private val _season = MutableStateFlow<Int?>(null)
    val selectedSeason: StateFlow<Int?> = _season.asStateFlow()

    val isFavorite: StateFlow<Boolean> = content.isFavorite(playlistId, kind, streamId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** History keys for this title: the movie, or each episode. */
    private val itemKeys = _info.map { info ->
        if (kind == ContentKind.MOVIE) listOf(ContentRepository.movieKey(streamId))
        else info.episodes.map { ContentRepository.episodeKey(it.id) }
    }.distinctUntilChanged()

    /** Watch progress keyed by history key (movie key or episode keys). */
    val history: StateFlow<Map<String, HistoryEntity>> = itemKeys
        .flatMapLatest { keys -> if (keys.isEmpty()) flowOf(emptyList()) else content.history(playlistId, keys) }
        .map { list -> list.associateBy { it.itemKey } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Downloads keyed by history key. */
    val downloads: StateFlow<Map<String, DownloadEntity>> = itemKeys
        .flatMapLatest { keys -> if (keys.isEmpty()) flowOf(emptyMap()) else container.downloads.observe(playlistId, keys) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** One-off messages, such as why a download couldn't start. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** The episode to offer on the main play button: the last one watched, else the first. */
    val nextEpisode: StateFlow<Pair<EpisodeUi, HistoryEntity?>?> = combine(_info, history) { info, hist ->
        if (info.episodes.isEmpty()) return@combine null
        val last = hist.values.maxByOrNull { it.lastWatchedAt }
        val lastEp = last?.let { h -> info.episodes.firstOrNull { ContentRepository.episodeKey(it.id) == h.itemKey } }
        if (last == null || lastEp == null) return@combine info.episodes.first() to null
        if (last.durationMs > 0 && last.positionMs > last.durationMs * 0.95) {
            val idx = info.episodes.indexOf(lastEp)
            info.episodes.getOrNull(idx + 1)?.let { it to null } ?: (lastEp to last)
        } else {
            lastEp to last
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _info.update { it.copy(loading = true, error = null) }
            val stream = content.getStream(playlistId, kind, streamId)
            val playlist = container.playlists.get(playlistId)
            if (stream == null || playlist == null) {
                _info.value = DetailInfo(loading = false, error = "This title is no longer in the playlist.")
                return@launch
            }
            val several = container.playlists.playlists.first().size > 1
            var info = DetailInfo(
                loading = true,
                stream = stream,
                playlist = playlist,
                source = playlist.name.takeIf { several },
                title = stream.name,
                poster = stream.logo,
                backdrops = listOfNotNull(stream.backdrop),
                plot = stream.plot,
                meta = listOfNotNull(stream.year, stream.genre),
                rating = stream.rating,
            )
            _info.value = info

            try {
                info = when {
                    playlist.type == PlaylistType.XTREAM && kind == ContentKind.MOVIE -> {
                        val vod = container.details.vodInfo(playlist, streamId)
                        info.copy(
                            poster = vod.cover ?: info.poster,
                            backdrops = vod.backdrops.ifEmpty { info.backdrops },
                            plot = vod.plot ?: info.plot,
                            meta = listOfNotNull(
                                vod.releaseDate?.take(4) ?: stream.year,
                                vod.durationSecs?.takeIf { it > 0 }?.let(::formatDuration),
                                vod.genre ?: stream.genre,
                            ),
                            rating = vod.rating?.takeIf { it > 0 } ?: info.rating,
                            cast = vod.cast,
                            director = vod.director,
                            trailer = vod.trailer,
                        )
                    }
                    playlist.type == PlaylistType.XTREAM && kind == ContentKind.SERIES -> {
                        val series = container.details.seriesInfo(playlist, streamId)
                        val urls = XtreamUrls(playlist.url, playlist.username, playlist.password)
                        info.copy(
                            poster = series.cover ?: info.poster,
                            backdrops = series.backdrops.ifEmpty { info.backdrops },
                            plot = series.plot ?: info.plot,
                            meta = listOfNotNull(series.releaseDate?.take(4) ?: stream.year, series.genre ?: stream.genre),
                            rating = series.rating?.takeIf { it > 0 } ?: info.rating,
                            cast = series.cast,
                            director = series.director,
                            trailer = series.trailer,
                            seasons = series.seasons.mapValues { (n, s) -> s.name?.takeIf { it.isNotBlank() } ?: "Season $n" },
                            seasonCovers = series.seasons.mapNotNull { (n, s) -> s.cover?.let { n to it } }.toMap(),
                            episodes = series.episodes.map { e ->
                                EpisodeUi(
                                    id = e.id,
                                    season = e.season,
                                    number = e.episodeNum,
                                    title = e.title,
                                    plot = e.plot,
                                    image = e.image,
                                    durationSecs = e.durationSecs,
                                    url = urls.episode(e.id, e.containerExtension),
                                )
                            },
                        )
                    }
                    kind == ContentKind.SERIES -> {
                        val episodes = content.episodes(playlistId, streamId)
                        info.copy(
                            seasons = episodes.map { it.season }.distinct().sorted().associateWith { "Season $it" },
                            episodes = episodes.map { e ->
                                EpisodeUi(e.episodeId, e.season, e.episode, e.title, null, e.logo, null, e.url)
                            },
                        )
                    }
                    else -> info
                }
                _info.value = info.copy(loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Basic details from the playlist are still shown; only the extras failed.
                _info.value = info.copy(loading = false, error = "Couldn't load details: ${e.message}")
            }
            if (_season.value == null) _season.value = _info.value.seasons.keys.firstOrNull()
        }
    }

    fun selectSeason(season: Int) {
        _season.value = season
    }

    fun toggleFavorite() {
        viewModelScope.launch { content.toggleFavorite(playlistId, kind, streamId) }
    }

    /** Downloads the movie, or [episode] of a series. */
    fun download(episode: EpisodeUi? = null) {
        val info = _info.value
        val stream = info.stream ?: return
        val request = if (episode == null) {
            DownloadRequest(
                playlistId = playlistId,
                kind = ContentKind.MOVIE,
                itemKey = ContentRepository.movieKey(streamId),
                streamId = streamId,
                title = info.title,
                subtitle = info.meta.firstOrNull(),
                artwork = info.poster,
                url = stream.url,
                userAgent = stream.userAgent ?: info.playlist?.userAgent,
                referrer = stream.referrer,
            )
        } else {
            DownloadRequest(
                playlistId = playlistId,
                kind = ContentKind.SERIES,
                itemKey = ContentRepository.episodeKey(episode.id),
                streamId = streamId,
                title = info.title,
                subtitle = episodeLabel(episode),
                artwork = info.poster,
                url = episode.url,
                userAgent = stream.userAgent ?: info.playlist?.userAgent,
                referrer = stream.referrer,
            )
        }
        viewModelScope.launch {
            container.downloads.start(request).onFailure { _messages.tryEmit(it.message ?: "Couldn't start the download.") }
        }
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch {
            container.downloads.retry(download).onFailure { _messages.tryEmit(it.message ?: "Couldn't start the download.") }
        }
    }

    fun removeDownload(download: DownloadEntity) {
        viewModelScope.launch { container.downloads.remove(download) }
    }

    fun isDownloadOk(download: DownloadEntity) = container.downloads.fileOk(download)

    fun moviePlayRequest(resumeMs: Long): PlayRequest? {
        val info = _info.value
        val stream = info.stream ?: return null
        return PlayRequest(
            playlistId = playlistId,
            kind = ContentKind.MOVIE,
            streamId = streamId,
            historyKey = ContentRepository.movieKey(streamId),
            url = stream.url,
            title = info.title,
            subtitle = info.meta.firstOrNull(),
            artwork = info.poster,
            userAgent = stream.userAgent ?: info.playlist?.userAgent,
            referrer = stream.referrer,
            startPositionMs = resumeMs,
        )
    }

    fun episodePlayRequest(episode: EpisodeUi, resumeMs: Long): PlayRequest {
        val info = _info.value
        return PlayRequest(
            playlistId = playlistId,
            kind = ContentKind.SERIES,
            streamId = streamId,
            historyKey = ContentRepository.episodeKey(episode.id),
            url = episode.url,
            title = info.title,
            subtitle = episodeLabel(episode),
            artwork = info.poster,
            userAgent = info.stream?.userAgent ?: info.playlist?.userAgent,
            referrer = info.stream?.referrer,
            startPositionMs = resumeMs,
        )
    }

    companion object {
        fun episodeLabel(e: EpisodeUi) = "S${e.season} · E${e.number} · ${e.title}"
    }
}
