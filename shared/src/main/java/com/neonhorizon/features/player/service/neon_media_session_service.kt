package com.neonhorizon.features.player.service

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.neonhorizon.data.db.FavoriteStationEntity
import com.neonhorizon.data.db.NeonFavoritesDatabaseProvider
import com.neonhorizon.features.radio.api.RadioBrowserApiFactory
import com.neonhorizon.features.radio.model.RadioStation
import com.neonhorizon.features.radio.model.toMediaItem
import com.neonhorizon.features.radio.repository.RadioBrowserRepositoryImpl
import com.neonhorizon.features.radio.usecase.GetLofiStationListUseCase
import com.neonhorizon.features.radio.usecase.GetStationSearchResultsUseCase
import com.neonhorizon.features.theme.VaporThemeGraph
import com.neonhorizon.shared.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

@UnstableApi
class NeonMediaSessionService : MediaLibraryService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val logoArtworkUri: Uri by lazy {
        Uri.parse("android.resource://$packageName/${R.drawable.logo_neon_horizon_small}")
    }
    private val liveArtworkUri: Uri by lazy {
        Uri.parse("android.resource://$packageName/${R.drawable.ic_auto_live}")
    }
    private val favoritesArtworkUri: Uri by lazy {
        Uri.parse("android.resource://$packageName/${R.drawable.ic_auto_favorites}")
    }
    private val genresArtworkUri: Uri by lazy {
        Uri.parse("android.resource://$packageName/${R.drawable.ic_auto_genres}")
    }

    private val themeEngine = VaporThemeGraph.themeEngine

    private val radioBrowserRepository by lazy {
        RadioBrowserRepositoryImpl(
            radioBrowserApi = RadioBrowserApiFactory.create()
        )
    }

    private val getLofiStationListUseCase by lazy {
        GetLofiStationListUseCase(
            radioBrowserRepository = radioBrowserRepository
        )
    }

    private val getStationSearchResultsUseCase by lazy {
        GetStationSearchResultsUseCase(
            radioBrowserRepository = radioBrowserRepository
        )
    }

    private val favoriteStationDao by lazy {
        NeonFavoritesDatabaseProvider
            .getDatabase(applicationContext)
            .favoriteStationDao()
    }
    private val firebaseAuth: FirebaseAuth? by lazy {
        runCatching { FirebaseAuth.getInstance() }
            .onFailure { throwable ->
                Log.w(LOG_TAG, "FirebaseAuth unavailable in media service", throwable)
            }
            .getOrNull()
    }
    private val firestore: FirebaseFirestore? by lazy {
        runCatching { FirebaseFirestore.getInstance() }
            .onFailure { throwable ->
                Log.w(LOG_TAG, "FirebaseFirestore unavailable in media service", throwable)
            }
            .getOrNull()
    }

    private lateinit var player: ExoPlayer
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var activeUserId: String? = null

    private var lofiStations: List<RadioStation> = emptyList()
    private var lofiMediaItems: List<MediaItem> = emptyList()
    private var favoriteStations: List<RadioStation> = emptyList()
    private var favoriteMediaItems: List<MediaItem> = emptyList()
    private var startupSelectionApplied: Boolean = false
    private var favoritesLoadedOnce: Boolean = false
    private var consecutivePlaybackFailures: Int = 0
    private val searchResultsByQuery: MutableMap<String, List<MediaItem>> = mutableMapOf()
    private val searchStationsById: MutableMap<String, RadioStation> = mutableMapOf()
    private var activeGenreKey: String = normalizeGenreKey("Lofi")
    private val genreStationsByKey: MutableMap<String, List<RadioStation>> = mutableMapOf()
    private val genreMediaItemsByKey: MutableMap<String, List<MediaItem>> = mutableMapOf()
    private val genreFetchesInFlight: MutableSet<String> = mutableSetOf()
    private val toggleFavoriteCommand = SessionCommand(CUSTOM_COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY)
    private val nextGenreCommand = SessionCommand(CUSTOM_COMMAND_NEXT_GENRE, Bundle.EMPTY)
    private val randomStationCommand = SessionCommand(CUSTOM_COMMAND_RANDOM_STATION, Bundle.EMPTY)
    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            consecutivePlaybackFailures = 0
            refreshAutomotiveCommandButtons()
        }

        override fun onPlayerError(error: PlaybackException) {
            skipToNextStationAfterFailure(error)
        }
    }

    private val genreCategoryItems: List<MediaItem> by lazy {
        AUTO_GENRES.map { genre ->
            buildGenreCategoryItem(genre)
        }
    }
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        handleAuthStateChanged(auth.currentUser)
    }

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val baseConnection = super.onConnect(session, controller)
            if (!baseConnection.isAccepted) {
                return baseConnection
            }

            val sessionCommands = baseConnection.availableSessionCommands
                .buildUpon()
                .add(toggleFavoriteCommand)
                .add(randomStationCommand)
                .build()

            val mediaButtons = runCatching {
                buildAutomotiveCommandButtons(isFavorite = isCurrentStationFavorite())
            }.onFailure { throwable ->
                Log.w(LOG_TAG, "Failed building automotive command buttons on connect.", throwable)
            }.getOrDefault(emptyList())

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(baseConnection.availablePlayerCommands)
                .setMediaButtonPreferences(mediaButtons)
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onPostConnect(session, controller)
            if (session.isAutomotiveController(controller)) {
                runCatching {
                    session.setMediaButtonPreferences(
                        controller,
                        buildAutomotiveCommandButtons(isFavorite = isCurrentStationFavorite())
                    )
                }.onFailure { throwable ->
                    Log.w(LOG_TAG, "Failed setting automotive media buttons for controller.", throwable)
                }
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return runCatching {
                Futures.immediateFuture(
                    LibraryResult.ofItem(buildRootItem(), params)
                )
            }.getOrElse { throwable ->
                Log.e(LOG_TAG, "onGetLibraryRoot failed", throwable)
                Futures.immediateFuture(
                    LibraryResult.ofItem(buildRootItem(), null)
                )
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return runCatching {
                when {
                    parentId == MEDIA_ROOT_ID -> {
                        val rootChildren = listOf(
                            buildLiveLofiCategoryItem(),
                            buildFavoritesCategoryItem(),
                            buildGenresHubItem()
                        )
                        Futures.immediateFuture(
                            LibraryResult.ofItemList(
                                paginateMediaItems(rootChildren, page, pageSize),
                                params
                            )
                        )
                    }

                    parentId == SECTION_LIVE_LOFI_ID -> {
                        loadStationsForGenre(
                            genre = "Lofi",
                            page = page,
                            pageSize = pageSize,
                            params = params,
                            parentId = SECTION_LIVE_LOFI_ID
                        )
                    }

                    parentId == SECTION_FAVORITES_ID -> {
                        Futures.immediateFuture(
                            LibraryResult.ofItemList(
                                paginateMediaItems(favoriteMediaItems, page, pageSize),
                                params
                            )
                        )
                    }

                    parentId == SECTION_GENRES_ID -> {
                        Futures.immediateFuture(
                            LibraryResult.ofItemList(
                                paginateMediaItems(genreCategoryItems, page, pageSize),
                                params
                            )
                        )
                    }

                    parentId.startsWith(GENRE_NODE_PREFIX) -> {
                        val genre = genreFromNodeId(parentId)
                        if (genre == null) {
                            Futures.immediateFuture(
                                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                            )
                        } else {
                            loadStationsForGenre(
                                genre = genre,
                                page = page,
                                pageSize = pageSize,
                                params = params,
                                parentId = parentId
                            )
                        }
                    }

                    else -> {
                        Futures.immediateFuture(
                            LibraryResult.ofItemList(ImmutableList.of(), params)
                        )
                    }
                }
            }.getOrElse { throwable ->
                Log.e(LOG_TAG, "onGetChildren failed for parent=$parentId", throwable)
                Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                )
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return runCatching {
                val item = when (mediaId) {
                    SECTION_LIVE_LOFI_ID -> buildLiveLofiCategoryItem()
                    SECTION_FAVORITES_ID -> buildFavoritesCategoryItem()
                    SECTION_GENRES_ID -> buildGenresHubItem()
                    else -> {
                        if (mediaId.startsWith(GENRE_NODE_PREFIX)) {
                            genreFromNodeId(mediaId)?.let(::buildGenreCategoryItem)
                        } else {
                            knownMediaItems().firstOrNull { mediaItem -> mediaItem.mediaId == mediaId }
                        }
                    }
                }

                Futures.immediateFuture(
                    item?.let { LibraryResult.ofItem(it, null) }
                        ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            }.getOrElse { throwable ->
                Log.e(LOG_TAG, "onGetItem failed for mediaId=$mediaId", throwable)
                Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            return runCatching {
                prefetchSearchResults(
                    session = session,
                    browser = browser,
                    query = query,
                    params = params
                )
                Futures.immediateFuture(LibraryResult.ofVoid())
            }.getOrElse { throwable ->
                Log.e(LOG_TAG, "onSearch failed for query=$query", throwable)
                Futures.immediateFuture(LibraryResult.ofVoid())
            }
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val normalizedQuery = query.trim().lowercase()
            if (normalizedQuery.isBlank()) {
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(
                        paginateMediaItems(knownMediaItems(), page, pageSize),
                        params
                    )
                )
            }

            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    searchResultsByQuery[normalizedQuery]?.let { cachedResults ->
                        future.set(
                            LibraryResult.ofItemList(
                                paginateMediaItems(cachedResults, page, pageSize),
                                params
                            )
                        )
                        return@launch
                    }

                    val cachedMatches = knownStations()
                        .filter { station ->
                            station.name.contains(normalizedQuery, ignoreCase = true) ||
                            station.tags.contains(normalizedQuery, ignoreCase = true) ||
                            station.country.contains(normalizedQuery, ignoreCase = true)
                        }
                        .map { station -> station.toAutoMediaItem() }

                    if (cachedMatches.isNotEmpty()) {
                        future.set(
                            LibraryResult.ofItemList(
                                paginateMediaItems(cachedMatches, page, pageSize),
                                params
                            )
                        )
                        searchResultsByQuery[normalizedQuery] = cachedMatches
                        return@launch
                    }

                    val stations = fetchSearchStations(query = query)
                    stations.forEach { station -> searchStationsById[station.id] = station }

                    val mediaItems = stations.map { station -> station.toAutoMediaItem() }
                    searchResultsByQuery[normalizedQuery] = mediaItems
                    future.set(
                        LibraryResult.ofItemList(
                            paginateMediaItems(mediaItems, page, pageSize),
                            params
                        )
                    )
                } catch (throwable: Throwable) {
                    Log.w(LOG_TAG, "Search failed unexpectedly for query=$query", throwable)
                    future.set(
                        LibraryResult.ofItemList(
                            ImmutableList.of(),
                            params
                        )
                    )
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            return runCatching {
                val mediaItemMap = knownMediaItems().associateBy { mediaItem -> mediaItem.mediaId }
                val resolvedRequestedMediaItems = mediaItems.map { requestedMediaItem ->
                    mediaItemMap[requestedMediaItem.mediaId] ?: requestedMediaItem
                }
                val selectedMediaId = resolvedRequestedMediaItems.singleOrNull()?.mediaId
                val resolvedMediaItems = selectedMediaId?.let { mediaId ->
                    buildQueueAroundMediaId(mediaId)
                } ?: resolvedRequestedMediaItems
                updateActiveGenreFromMediaId(selectedMediaId)

                Futures.immediateFuture(resolvedMediaItems)
            }.getOrElse { throwable ->
                Log.e(LOG_TAG, "onAddMediaItems failed", throwable)
                Futures.immediateFuture(mediaItems)
            }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val queue = activeQueueMediaItems()
                .ifEmpty { lofiMediaItems }
                .ifEmpty { FALLBACK_LOFI_STATIONS.map { station -> station.toAutoMediaItem() } }

            if (queue.isEmpty()) {
                return super.onPlaybackResumption(mediaSession, controller)
            }

            val currentMediaId = player.currentMediaItem?.mediaId
            val startIndex = queue.indexOfFirst { item -> item.mediaId == currentMediaId }
                .takeIf { it >= 0 }
                ?: 0
            val startPositionMs = player.currentPosition.coerceAtLeast(0L)

            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(queue, startIndex, startPositionMs)
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                CUSTOM_COMMAND_TOGGLE_FAVORITE -> toggleFavoriteForCurrentStation()
                CUSTOM_COMMAND_NEXT_GENRE -> cycleToNextGenre()
                CUSTOM_COMMAND_RANDOM_STATION -> playRandomStationFromAll()
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .build()
            .apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build()

                setAudioAttributes(audioAttributes, true)
                playWhenReady = false
            }
        player.addListener(playerListener)

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, libraryCallback)
            .setId(SESSION_ID)
            .build()

        mediaLibrarySession?.let { session ->
            themeEngine.bindToSession(session, serviceScope)
        }

        seedFallbackLofiStations()
        firebaseAuth?.let { auth ->
            auth.addAuthStateListener(authStateListener)
            handleAuthStateChanged(auth.currentUser)
        } ?: Log.w(LOG_TAG, "Skipping cloud favorites sync: FirebaseAuth unavailable")
        observeFavorites()
        preloadLofiForAuto()
    }

    private fun observeFavorites() {
        serviceScope.launch {
            favoriteStationDao.observeFavorites().collectLatest { entities ->
                favoritesLoadedOnce = true
                favoriteStations = entities.map { entity -> entity.toRadioStation() }
                favoriteMediaItems = favoriteStations.map { station -> station.toAutoMediaItem() }
                mediaLibrarySession?.notifyChildrenChanged(
                    SECTION_FAVORITES_ID,
                    favoriteMediaItems.size,
                    null
                )
                maybeApplyStartupSelection()
                refreshAutomotiveCommandButtons()
            }
        }
    }

    private fun handleAuthStateChanged(user: FirebaseUser?) {
        activeUserId = user?.uid
        if (user == null) {
            return
        }
        syncFavoritesFromCloud(user)
    }

    private fun syncFavoritesFromCloud(user: FirebaseUser) {
        val firestoreInstance = firestore ?: run {
            Log.w(LOG_TAG, "Skipping cloud favorites sync: Firestore unavailable")
            return
        }

        firestoreInstance
            .collection(FIRESTORE_USERS_COLLECTION)
            .document(user.uid)
            .collection(FIRESTORE_FAVORITES_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                serviceScope.launch {
                    val cloudFavorites = snapshot.documents
                        .mapNotNull { document -> document.toFavoriteEntityOrNull() }
                        .distinctBy { favorite -> favorite.stationId }

                    cloudFavorites.forEach { favorite ->
                        favoriteStationDao.upsertFavorite(favorite)
                    }

                    Log.i(
                        LOG_TAG,
                        "Cloud favorites loaded for ${user.uid}: ${cloudFavorites.size}"
                    )
                }
            }
            .addOnFailureListener { throwable ->
                Log.w(
                    LOG_TAG,
                    "Failed loading cloud favorites for ${user.uid}",
                    throwable
                )
            }
    }

    private fun preloadLofiForAuto() {
        serviceScope.launch {
            getLofiStationListUseCase(limit = DEFAULT_GENRE_LIMIT)
                .onSuccess { stations ->
                    lofiStations = stations
                    lofiMediaItems = stations.map { station -> station.toAutoMediaItem() }
                    genreStationsByKey[normalizeGenreKey("Lofi")] = stations
                    genreMediaItemsByKey[normalizeGenreKey("Lofi")] = lofiMediaItems
                    if (favoritesLoadedOnce) {
                        maybeApplyStartupSelection()
                    }
                }
                .onFailure { throwable ->
                    Log.w(LOG_TAG, "Unable to preload lofi stations", throwable)
                }
        }
    }

    private fun seedFallbackLofiStations() {
        if (lofiMediaItems.isNotEmpty()) {
            return
        }

        lofiStations = FALLBACK_LOFI_STATIONS
        lofiMediaItems = lofiStations.map { station -> station.toAutoMediaItem() }
        genreStationsByKey[normalizeGenreKey("Lofi")] = lofiStations
        genreMediaItemsByKey[normalizeGenreKey("Lofi")] = lofiMediaItems

        if (favoritesLoadedOnce) {
            maybeApplyStartupSelection()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        firebaseAuth?.removeAuthStateListener(authStateListener)
        themeEngine.release()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player.removeListener(playerListener)
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun loadStationsForGenre(
        genre: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
        parentId: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val key = normalizeGenreKey(genre)
        val cachedItems = genreMediaItemsByKey[key]

        if (!cachedItems.isNullOrEmpty()) {
            return Futures.immediateFuture(
                LibraryResult.ofItemList(
                    paginateMediaItems(cachedItems, page, pageSize),
                    params
                )
            )
        }

        // For non-lofi genres, resolve from the selected genre in the full station index.
        // This avoids showing the same lofi fallback list across all genre pages.
        if (key != normalizeGenreKey("Lofi")) {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                getStationSearchResultsUseCase(
                    query = "",
                    genre = genre,
                    limit = DEFAULT_GENRE_LIMIT
                )
                    .onSuccess { stations ->
                        val mediaItems = stations.map { station -> station.toAutoMediaItem() }
                        genreStationsByKey[key] = stations
                        genreMediaItemsByKey[key] = mediaItems
                        mediaLibrarySession?.notifyChildrenChanged(parentId, mediaItems.size, null)
                        future.set(
                            LibraryResult.ofItemList(
                                paginateMediaItems(mediaItems, page, pageSize),
                                params
                            )
                        )
                    }
                    .onFailure { throwable ->
                        Log.w(LOG_TAG, "Failed loading genre stations (direct): $genre", throwable)
                        future.set(
                            LibraryResult.ofItemList(
                                ImmutableList.of(),
                                params
                            )
                        )
                    }
            }
            return future
        }

        val fallbackItems = fallbackBrowseItemsForGenre(genre)
        if (fallbackItems.isNotEmpty()) {
            maybeFetchGenreChildren(
                genre = genre,
                key = key,
                parentId = parentId
            )

            return Futures.immediateFuture(
                LibraryResult.ofItemList(
                    paginateMediaItems(fallbackItems, page, pageSize),
                    params
                )
            )
        }

        // Return immediately to avoid Android Auto browse timeouts, then refresh asynchronously.
        maybeFetchGenreChildren(
            genre = genre,
            key = key,
            parentId = parentId
        )

        return Futures.immediateFuture(
            LibraryResult.ofItemList(
                ImmutableList.of(),
                params
            )
        )
    }

    private fun fallbackBrowseItemsForGenre(genre: String): List<MediaItem> {
        val normalizedGenre = normalizeGenreKey(genre)
        val fallbackLofiItems = lofiMediaItems.ifEmpty {
            FALLBACK_LOFI_STATIONS.map { station -> station.toAutoMediaItem() }
        }

        return when (normalizedGenre) {
            normalizeGenreKey("Lofi") -> fallbackLofiItems
            else -> emptyList()
        }
    }

    private fun maybeFetchGenreChildren(
        genre: String,
        key: String,
        parentId: String
    ) {
        synchronized(genreFetchesInFlight) {
            if (!genreFetchesInFlight.add(key)) {
                return
            }
        }

        serviceScope.launch {
            try {
                getStationSearchResultsUseCase(
                    query = "",
                    genre = genre,
                    limit = DEFAULT_GENRE_LIMIT
                )
                    .onSuccess { stations ->
                        val mediaItems = stations.map { station -> station.toAutoMediaItem() }
                        genreStationsByKey[key] = stations
                        genreMediaItemsByKey[key] = mediaItems

                        if (key == normalizeGenreKey("Lofi")) {
                            lofiStations = stations
                            lofiMediaItems = mediaItems
                        }

                        mediaLibrarySession?.notifyChildrenChanged(parentId, mediaItems.size, null)
                    }
                    .onFailure { throwable ->
                        Log.w(LOG_TAG, "Failed loading genre stations: $genre", throwable)
                    }
            } finally {
                synchronized(genreFetchesInFlight) {
                    genreFetchesInFlight.remove(key)
                }
            }
        }
    }

    private fun knownStations(): List<RadioStation> {
        val fromGenres = genreStationsByKey.values.flatten()
        val fromSearch = searchStationsById.values.toList()
        return (lofiStations + favoriteStations + fromGenres + fromSearch)
            .distinctBy { station -> station.id }
    }

    private fun knownMediaItems(): List<MediaItem> {
        val fromGenres = genreMediaItemsByKey.values.flatten()
        val fromSearch = searchResultsByQuery.values.flatten()
        return (lofiMediaItems + favoriteMediaItems + fromGenres + fromSearch)
            .distinctBy { mediaItem -> mediaItem.mediaId }
    }

    private fun buildLiveLofiCategoryItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("Live: Lofi")
            .setGenre("Lofi")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(liveArtworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(SECTION_LIVE_LOFI_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildFavoritesCategoryItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("Favorites")
            .setGenre("Saved")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(favoritesArtworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(SECTION_FAVORITES_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildGenresHubItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("Genres")
            .setGenre("Discover")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(genresArtworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(SECTION_GENRES_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildGenreCategoryItem(genre: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(genre)
            .setGenre(genre)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(logoArtworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(genreToNodeId(genre))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildRootItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("Neon Horizon Radio")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(logoArtworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(MEDIA_ROOT_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun paginateMediaItems(
        mediaItems: List<MediaItem>,
        page: Int,
        pageSize: Int
    ): ImmutableList<MediaItem> {
        if (mediaItems.isEmpty()) {
            return ImmutableList.of()
        }

        // Some car hosts request unpaged content with negative/zero paging values.
        if (page < 0 || pageSize <= 0) {
            return ImmutableList.copyOf(mediaItems)
        }

        val safePage = page.coerceAtLeast(0)
        val safePageSize = pageSize.coerceAtLeast(1)
        val startIndex = safePage * safePageSize

        if (startIndex >= mediaItems.size) {
            return ImmutableList.of()
        }

        val endIndex = (startIndex + safePageSize).coerceAtMost(mediaItems.size)
        return ImmutableList.copyOf(mediaItems.subList(startIndex, endIndex))
    }

    private fun RadioStation.toAutoMediaItem(): MediaItem {
        val baseItem = toMediaItem()
        val metadataBuilder = baseItem.mediaMetadata
            .buildUpon()
            .setDisplayTitle(name)
            .setArtist("Neon Horizon Radio")
            .setAlbumTitle("${tags.ifBlank { "Lofi" }} • ${country.ifBlank { "Unknown" }}")
            .setIsPlayable(true)
            .setIsBrowsable(false)

        if (baseItem.mediaMetadata.artworkUri == null) {
            metadataBuilder.setArtworkUri(logoArtworkUri)
        }

        return baseItem.buildUpon()
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun buildQueueAroundMediaId(mediaId: String): List<MediaItem>? {
        val candidateQueues = buildList {
            if (lofiMediaItems.isNotEmpty()) add(lofiMediaItems)
            if (favoriteMediaItems.isNotEmpty()) add(favoriteMediaItems)
            genreMediaItemsByKey.values
                .filter { queue -> queue.isNotEmpty() }
                .forEach { queue -> add(queue) }
            searchResultsByQuery.values
                .filter { queue -> queue.isNotEmpty() }
                .forEach { queue -> add(queue) }
        }

        val queue = candidateQueues.firstOrNull { items ->
            items.any { item -> item.mediaId == mediaId }
        } ?: return null

        val selectedIndex = queue.indexOfFirst { item -> item.mediaId == mediaId }
        if (selectedIndex < 0) {
            return null
        }
        if (queue.size <= 1) {
            return queue
        }

        return (queue.drop(selectedIndex) + queue.take(selectedIndex))
            .distinctBy { item -> item.mediaId }
    }

    private fun updateActiveGenreFromMediaId(mediaId: String?) {
        if (mediaId.isNullOrBlank()) {
            return
        }
        val matchingEntry = genreMediaItemsByKey.entries.firstOrNull { (_, queue) ->
            queue.any { item -> item.mediaId == mediaId }
        }
        if (matchingEntry != null) {
            activeGenreKey = matchingEntry.key
            return
        }
        if (lofiMediaItems.any { item -> item.mediaId == mediaId }) {
            activeGenreKey = normalizeGenreKey("Lofi")
        }
    }

    private fun activeQueueMediaItems(): List<MediaItem> {
        return genreMediaItemsByKey[activeGenreKey]
            ?: if (activeGenreKey == normalizeGenreKey("Lofi")) lofiMediaItems else emptyList()
    }

    private fun isCurrentStationFavorite(): Boolean {
        val mediaId = player.currentMediaItem?.mediaId
        if (mediaId.isNullOrBlank()) {
            return false
        }
        return favoriteStations.any { station -> station.id == mediaId }
    }

    private fun currentPlayingStation(): RadioStation? {
        val mediaId = player.currentMediaItem?.mediaId
        return knownStations().firstOrNull { station -> station.id == mediaId }
    }

    private fun buildAutomotiveCommandButtons(isFavorite: Boolean): List<CommandButton> {
        val favoriteButton = CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setSessionCommand(toggleFavoriteCommand)
            .setDisplayName(if (isFavorite) "Unfavorite" else "Favorite")
            .setSlots(CommandButton.SLOT_BACK_SECONDARY)
            .build()

        val randomButton = CommandButton.Builder(CommandButton.ICON_RADIO)
            .setSessionCommand(randomStationCommand)
            .setDisplayName("Random All")
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
            .build()

        return listOf(favoriteButton, randomButton)
    }

    private fun refreshAutomotiveCommandButtons() {
        val session = mediaLibrarySession ?: return
        val buttons = buildAutomotiveCommandButtons(isFavorite = isCurrentStationFavorite())
        runCatching {
            session.setMediaButtonPreferences(buttons)
        }.onFailure { throwable ->
            Log.w(LOG_TAG, "Failed refreshing session-level automotive media buttons.", throwable)
        }
        session.getConnectedControllers()
            .filter { controller -> session.isAutomotiveController(controller) }
            .forEach { controller ->
                runCatching {
                    session.setMediaButtonPreferences(controller, buttons)
                }.onFailure { throwable ->
                    Log.w(LOG_TAG, "Failed refreshing automotive media buttons for controller.", throwable)
                }
            }
    }

    private fun toggleFavoriteForCurrentStation(): ListenableFuture<SessionResult> {
        val result = SettableFuture.create<SessionResult>()
        serviceScope.launch {
            val station = currentPlayingStation()
            if (station == null) {
                result.set(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                return@launch
            }

            val favoriteEntity = station.toFavoriteEntity()
            val isFavorite = favoriteStations.any { item -> item.id == station.id }

            runCatching {
                if (isFavorite) {
                    favoriteStationDao.deleteFavorite(favoriteEntity)
                    deleteFavoriteFromCloud(station.id)
                } else {
                    favoriteStationDao.upsertFavorite(favoriteEntity)
                    upsertFavoriteToCloud(favoriteEntity)
                }
            }.onSuccess {
                refreshAutomotiveCommandButtons()
                result.set(SessionResult(SessionResult.RESULT_SUCCESS))
            }.onFailure { throwable ->
                Log.e(LOG_TAG, "Failed toggling favorite for station=${station.id}", throwable)
                result.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return result
    }

    private fun cycleToNextGenre(): ListenableFuture<SessionResult> {
        val result = SettableFuture.create<SessionResult>()
        serviceScope.launch {
            val currentGenreIndex = AUTO_GENRES.indexOfFirst { genre ->
                normalizeGenreKey(genre) == activeGenreKey
            }
            val nextIndex = if (currentGenreIndex < 0) 0 else (currentGenreIndex + 1) % AUTO_GENRES.size
            val nextGenre = AUTO_GENRES[nextIndex]
            val nextGenreKey = normalizeGenreKey(nextGenre)

            val queue = genreMediaItemsByKey[nextGenreKey]
                ?: if (nextGenreKey == normalizeGenreKey("Lofi")) {
                    lofiMediaItems.ifEmpty {
                        FALLBACK_LOFI_STATIONS.map { station -> station.toAutoMediaItem() }
                    }
                } else {
                    emptyList()
                }

            if (queue.isEmpty()) {
                maybeFetchGenreChildren(
                    genre = nextGenre,
                    key = nextGenreKey,
                    parentId = genreToNodeId(nextGenre)
                )
                result.set(SessionResult(SessionResult.RESULT_INFO_SKIPPED))
                return@launch
            }

            activeGenreKey = nextGenreKey
            runCatching {
                player.setMediaItems(queue, 0, 0L)
                player.prepare()
                player.playWhenReady = true
                player.play()
            }.onSuccess {
                refreshAutomotiveCommandButtons()
                result.set(SessionResult(SessionResult.RESULT_SUCCESS))
            }.onFailure { throwable ->
                Log.e(LOG_TAG, "Failed switching to next genre=$nextGenre", throwable)
                result.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return result
    }

    private fun playRandomStationFromAll(): ListenableFuture<SessionResult> {
        val result = SettableFuture.create<SessionResult>()
        serviceScope.launch {
            val cachedStations = knownStations()
                .distinctBy { station -> station.id }
                .filter { station -> station.streamUrl.isNotBlank() }

            val fetchedStations = getStationSearchResultsUseCase(
                query = "",
                genre = "",
                limit = RANDOM_POOL_LIMIT
            ).getOrElse { throwable ->
                Log.w(LOG_TAG, "Random all fetch failed. Falling back to cache.", throwable)
                emptyList()
            }

            val pool = fetchedStations
                .ifEmpty { cachedStations }
                .distinctBy { station -> station.id }
                .filter { station -> station.streamUrl.isNotBlank() }

            if (pool.isEmpty()) {
                result.set(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                return@launch
            }

            val queue = pool.map { station -> station.toAutoMediaItem() }
            val startIndex = queue.indices.random()
            genreStationsByKey[RANDOM_ALL_QUEUE_KEY] = pool
            genreMediaItemsByKey[RANDOM_ALL_QUEUE_KEY] = queue
            activeGenreKey = RANDOM_ALL_QUEUE_KEY

            runCatching {
                player.setMediaItems(queue, startIndex, 0L)
                player.prepare()
                player.playWhenReady = true
                player.play()
            }.onSuccess {
                refreshAutomotiveCommandButtons()
                result.set(SessionResult(SessionResult.RESULT_SUCCESS))
            }.onFailure { throwable ->
                Log.e(LOG_TAG, "Failed to start random-all station playback.", throwable)
                result.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return result
    }

    private fun prefetchSearchResults(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            return
        }

        serviceScope.launch {
            val stations = fetchSearchStations(query = query)
            stations.forEach { station -> searchStationsById[station.id] = station }
            val mediaItems = stations.map { station -> station.toAutoMediaItem() }
            searchResultsByQuery[normalizedQuery] = mediaItems

            runCatching {
                session.notifySearchResultChanged(
                    browser,
                    query,
                    mediaItems.size,
                    params
                )
            }.onFailure { throwable ->
                Log.w(LOG_TAG, "Failed to notify search result change for query=$query", throwable)
            }
        }
    }

    private suspend fun fetchSearchStations(query: String): List<RadioStation> {
        val searchResult = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
            getStationSearchResultsUseCase(
                query = query,
                genre = "",
                limit = SEARCH_LIMIT
            )
        } ?: Result.failure(IllegalStateException("Search timed out"))

        return searchResult.getOrElse {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS / 2) {
                getStationSearchResultsUseCase(
                    query = "",
                    genre = query,
                    limit = SEARCH_LIMIT
                )
            }?.getOrElse { fallbackError ->
                Log.w(LOG_TAG, "Tag fallback search failed for query=$query", fallbackError)
                emptyList()
            } ?: emptyList()
        }
    }

    private fun maybeApplyStartupSelection() {
        if (startupSelectionApplied) {
            return
        }

        val startupFavoritesQueue = favoriteMediaItems
        val startupLofiQueue = lofiMediaItems.ifEmpty {
            FALLBACK_LOFI_STATIONS.map { station -> station.toAutoMediaItem() }
        }
        val startupQueue = if (startupFavoritesQueue.isNotEmpty()) {
            startupFavoritesQueue
        } else {
            startupLofiQueue
        }

        if (startupQueue.isEmpty()) {
            return
        }

        if (startupFavoritesQueue.isNotEmpty()) {
            genreStationsByKey[STARTUP_FAVORITES_QUEUE_KEY] = favoriteStations
            genreMediaItemsByKey[STARTUP_FAVORITES_QUEUE_KEY] = startupFavoritesQueue
            activeGenreKey = STARTUP_FAVORITES_QUEUE_KEY
        } else {
            activeGenreKey = normalizeGenreKey("Lofi")
        }

        val startupIndex = startupQueue.indices.random()
        runCatching {
            player.setMediaItems(startupQueue, startupIndex, 0L)
            player.prepare()
        }.onSuccess {
            startupSelectionApplied = true
        }.onFailure { throwable ->
            Log.w(LOG_TAG, "Failed to initialize startup station queue.", throwable)
        }
    }

    private fun skipToNextStationAfterFailure(error: PlaybackException) {
        val queueSize = player.mediaItemCount
        if (queueSize <= 1) {
            return
        }

        consecutivePlaybackFailures += 1
        if (consecutivePlaybackFailures >= queueSize) {
            Log.w(LOG_TAG, "Stopping auto-skip after $consecutivePlaybackFailures failures.", error)
            return
        }

        val targetIndex = if (player.hasNextMediaItem()) {
            player.nextMediaItemIndex
        } else {
            0
        }

        runCatching {
            player.seekTo(targetIndex, 0L)
            player.prepare()
            player.playWhenReady = true
            player.play()
        }.onFailure { throwable ->
            Log.w(LOG_TAG, "Failed auto-skipping dead station after playback error.", throwable)
        }
    }

    private fun upsertFavoriteToCloud(entity: FavoriteStationEntity) {
        val userId = activeUserId ?: firebaseAuth?.currentUser?.uid ?: return
        val firestoreInstance = firestore ?: return
        val payload = entity.toFirestoreMap()

        firestoreInstance
            .collection(FIRESTORE_USERS_COLLECTION)
            .document(userId)
            .collection(FIRESTORE_FAVORITES_COLLECTION)
            .document(entity.stationId)
            .set(payload)
            .addOnFailureListener { throwable ->
                Log.w(LOG_TAG, "Failed saving favorite to cloud for user=$userId", throwable)
            }
    }

    private fun deleteFavoriteFromCloud(stationId: String) {
        val userId = activeUserId ?: firebaseAuth?.currentUser?.uid ?: return
        val firestoreInstance = firestore ?: return

        firestoreInstance
            .collection(FIRESTORE_USERS_COLLECTION)
            .document(userId)
            .collection(FIRESTORE_FAVORITES_COLLECTION)
            .document(stationId)
            .delete()
            .addOnFailureListener { throwable ->
                Log.w(LOG_TAG, "Failed deleting favorite from cloud for user=$userId", throwable)
            }
    }

    private fun genreToNodeId(genre: String): String {
        val token = genre
            .trim()
            .lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')
        return "$GENRE_NODE_PREFIX$token"
    }

    private fun genreFromNodeId(nodeId: String): String? {
        return AUTO_GENRES.firstOrNull { genre ->
            genreToNodeId(genre) == nodeId
        }
    }

    private fun normalizeGenreKey(genre: String): String {
        return genre.trim().lowercase(Locale.US)
    }

    companion object {
        private const val LOG_TAG = "NeonMediaService"
        private const val SESSION_ID = "neon_horizon_media_session"
        private const val FIRESTORE_USERS_COLLECTION = "users"
        private const val FIRESTORE_FAVORITES_COLLECTION = "favorites"
        private const val MEDIA_ROOT_ID = "neon_root"
        private const val SECTION_LIVE_LOFI_ID = "live_lofi"
        private const val SECTION_FAVORITES_ID = "favorites"
        private const val SECTION_GENRES_ID = "genres"
        private const val GENRE_NODE_PREFIX = "genre_"
        private const val DEFAULT_GENRE_LIMIT = 40
        private const val SEARCH_LIMIT = 100
        private const val SEARCH_TIMEOUT_MS = 12_000L
        private const val CUSTOM_COMMAND_TOGGLE_FAVORITE = "com.neonhorizon.action.TOGGLE_FAVORITE"
        private const val CUSTOM_COMMAND_NEXT_GENRE = "com.neonhorizon.action.NEXT_GENRE"
        private const val CUSTOM_COMMAND_RANDOM_STATION = "com.neonhorizon.action.RANDOM_STATION"
        private const val RANDOM_POOL_LIMIT = 250
        private const val RANDOM_ALL_QUEUE_KEY = "__all_random__"
        private const val STARTUP_FAVORITES_QUEUE_KEY = "__startup_favorites__"
        private val FALLBACK_LOFI_STATIONS = listOf(
            RadioStation(
                id = "fallback_groovesalad",
                name = "SomaFM Groove Salad",
                streamUrl = "https://ice2.somafm.com/groovesalad-128-mp3",
                homepage = "https://somafm.com/groovesalad/",
                favicon = "",
                tags = "lofi,chillout,ambient,downtempo",
                country = "United States"
            ),
            RadioStation(
                id = "fallback_secretagent",
                name = "SomaFM Secret Agent",
                streamUrl = "https://ice2.somafm.com/secretagent-128-mp3",
                homepage = "https://somafm.com/secretagent/",
                favicon = "",
                tags = "chillout,downtempo,electronic",
                country = "United States"
            ),
            RadioStation(
                id = "fallback_dronezone",
                name = "SomaFM Drone Zone",
                streamUrl = "https://ice2.somafm.com/dronezone-128-mp3",
                homepage = "https://somafm.com/dronezone/",
                favicon = "",
                tags = "ambient,space,chillout",
                country = "United States"
            )
        )
        private val AUTO_GENRES = listOf(
            "Lofi",
            "Chillout",
            "Relax",
            "Study",
            "Focus",
            "Sleep",
            "Lounge",
            "Ambient",
            "Jazz",
            "Classical",
            "Piano",
            "Guitar",
            "Synthwave",
            "Vaporwave"
        )
    }
}

private fun FavoriteStationEntity.toRadioStation(): RadioStation {
    return RadioStation(
        id = stationId,
        name = name,
        streamUrl = streamUrl,
        homepage = "",
        favicon = favicon,
        tags = tags,
        country = country
    )
}

private fun RadioStation.toFavoriteEntity(): FavoriteStationEntity {
    return FavoriteStationEntity(
        stationId = id,
        name = name,
        streamUrl = streamUrl,
        favicon = favicon,
        tags = tags,
        country = country,
        savedAtEpochMillis = System.currentTimeMillis()
    )
}

private fun FavoriteStationEntity.toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "stationId" to stationId,
        "name" to name,
        "streamUrl" to streamUrl,
        "favicon" to favicon,
        "tags" to tags,
        "country" to country,
        "savedAtEpochMillis" to savedAtEpochMillis,
        "updatedAtEpochMillis" to System.currentTimeMillis()
    )
}

private fun DocumentSnapshot.toFavoriteEntityOrNull(): FavoriteStationEntity? {
    val stationId = getString("stationId")
        ?.takeIf { value -> value.isNotBlank() }
        ?: id.takeIf { value -> value.isNotBlank() }
        ?: return null
    val name = getString("name").orEmpty()
    val streamUrl = getString("streamUrl").orEmpty()

    if (name.isBlank() || streamUrl.isBlank()) {
        return null
    }

    return FavoriteStationEntity(
        stationId = stationId,
        name = name,
        streamUrl = streamUrl,
        favicon = getString("favicon").orEmpty(),
        tags = getString("tags").orEmpty(),
        country = getString("country").orEmpty(),
        savedAtEpochMillis = getLong("savedAtEpochMillis") ?: System.currentTimeMillis()
    )
}
