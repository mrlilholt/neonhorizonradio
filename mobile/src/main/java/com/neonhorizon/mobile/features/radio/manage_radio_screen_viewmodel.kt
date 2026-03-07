package com.neonhorizon.mobile.features.radio

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.neonhorizon.data.db.FavoriteStationEntity
import com.neonhorizon.data.db.NeonFavoritesDatabaseProvider
import com.neonhorizon.features.player.service.NeonMediaSessionService
import com.neonhorizon.features.radio.api.RadioBrowserApiFactory
import com.neonhorizon.features.radio.model.RadioStation
import com.neonhorizon.features.radio.model.toMediaItem
import com.neonhorizon.features.radio.repository.RadioBrowserRepositoryImpl
import com.neonhorizon.features.radio.usecase.GetStationByIdUseCase
import com.neonhorizon.features.radio.usecase.GetStationSearchResultsUseCase
import com.neonhorizon.features.weather.api.OpenMeteoApiFactory
import com.neonhorizon.features.weather.model.LocalWeatherForecast
import com.neonhorizon.features.weather.repository.LocalWeatherRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RadioMainTab {
    PLAYER,
    STATIONS,
    FAVORITES
}

private val DEFAULT_GENRES = listOf(
    "Lofi",
    "All",
    "Chillout",
    "Relax",
    "Study",
    "Focus",
    "Sleep",
    "Lounge",
    "Downtempo",
    "Jazz",
    "Ambient",
    "Classical",
    "Piano",
    "Guitar",
    "Synthwave",
    "Vaporwave",
    "Electronic",
    "House",
    "Deep House",
    "Techno",
    "Trance",
    "Drum and Bass",
    "Hip Hop",
    "Pop",
    "Rock",
    "Indie",
    "Metal",
    "Reggae",
    "Blues",
    "Soul",
    "Funk",
    "Disco",
    "R&B",
    "Country",
    "Latin",
    "Afrobeats"
)

data class RadioScreenUiState(
    val isLoading: Boolean = true,
    val stations: List<RadioStation> = emptyList(),
    val stationCountLabel: String = "0 stations",
    val queueSourceLabel: String = "Queue: Genre Lofi",
    val loadErrorMessage: String? = null,
    val playbackErrorMessage: String? = null,
    val nowPlayingTitle: String = "No station selected",
    val nowPlayingSubtitle: String = "Tap a station to begin streaming",
    val playbackStatusLabel: String = "Idle",
    val isPlaying: Boolean = false,
    val activeStationId: String? = null,
    val activeStationSnapshot: RadioStation? = null,
    val isControllerConnected: Boolean = false,
    val canPlayNext: Boolean = false,
    val canPlayPrevious: Boolean = false,
    val playerVolume: Float = 1f,
    val playbackLogLines: List<String> = listOf("[BOOT] Player interface ready."),
    val selectedTab: RadioMainTab = RadioMainTab.PLAYER,
    val searchQuery: String = "",
    val selectedGenre: String = "Lofi",
    val availableGenres: List<String> = DEFAULT_GENRES,
    val favoriteStationIds: Set<String> = emptySet(),
    val favoriteStations: List<RadioStation> = emptyList(),
    val isSignalDiagnosticsVisible: Boolean = false,
    val isWeatherEnabled: Boolean = true,
    val isWeatherLoading: Boolean = false,
    val localWeatherForecast: LocalWeatherForecast? = null,
    val weatherStatusMessage: String? = null,
    val isAuthInProgress: Boolean = false,
    val signedInUserId: String? = null,
    val signedInUserName: String? = null,
    val signedInUserEmail: String? = null,
    val signedInUserPhotoUrl: String? = null,
    val authStatusMessage: String? = null
)

class RadioScreenViewModel(
    private val appContext: Context
) : ViewModel() {
    private val radioBrowserRepository = RadioBrowserRepositoryImpl(
        radioBrowserApi = RadioBrowserApiFactory.create(),
        fallbackRadioBrowserApis = RadioBrowserApiFactory.createFallbackApis()
    )

    private val getStationSearchResultsUseCase = GetStationSearchResultsUseCase(
        radioBrowserRepository = radioBrowserRepository
    )
    private val getStationByIdUseCase = GetStationByIdUseCase(
        radioBrowserRepository = radioBrowserRepository
    )
    private val localWeatherRepository = LocalWeatherRepositoryImpl(
        openMeteoApi = OpenMeteoApiFactory.create()
    )

    private val favoriteStationDao = NeonFavoritesDatabaseProvider
        .getDatabase(appContext)
        .favoriteStationDao()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val uiStateFlow = MutableStateFlow(RadioScreenUiState())
    val uiState: StateFlow<RadioScreenUiState> = uiStateFlow.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var activeUserId: String? = null
    private var pendingSharedStationId: String? = null
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        handleAuthStateChanged(auth.currentUser)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            uiStateFlow.update { currentState ->
                currentState.copy(isPlaying = isPlaying)
            }
            syncPlaybackState()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            syncPlaybackState()
            val stationName = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
            if (stationName.isNotBlank()) {
                appendPlaybackLog("[TUNE] $stationName")
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            syncPlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlaybackState()
            maybeHideSignalDiagnostics(playbackState)
        }

        override fun onPlayerError(error: PlaybackException) {
            val errorMessage = error.message ?: "Playback error"
            uiStateFlow.update { currentState ->
                currentState.copy(
                    playbackErrorMessage = errorMessage,
                    isSignalDiagnosticsVisible = false
                )
            }
            appendPlaybackLog("[ERROR] $errorMessage")
        }
    }

    init {
        firebaseAuth.addAuthStateListener(authStateListener)
        handleAuthStateChanged(firebaseAuth.currentUser)
        observeFavorites()
        connectMediaController()
        loadLofiStations()
    }

    fun markGoogleSignInStarted() {
        uiStateFlow.update { currentState ->
            currentState.copy(
                isAuthInProgress = true,
                authStatusMessage = null
            )
        }
    }

    fun authenticateWithGoogleIdToken(idToken: String) {
        if (idToken.isBlank()) {
            onGoogleSignInFailed("Google sign-in did not return a valid token.")
            return
        }

        uiStateFlow.update { currentState ->
            currentState.copy(
                isAuthInProgress = true,
                authStatusMessage = null
            )
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                activeUserId = user?.uid
                val userLabel = user?.email ?: user?.displayName ?: "Google account"
                uiStateFlow.update { currentState ->
                    currentState.copy(
                        isAuthInProgress = false,
                        authStatusMessage = "Signed in as $userLabel"
                    )
                }
                appendPlaybackLog("[AUTH] Signed in: $userLabel")
                if (user != null) {
                    syncFavoritesFromCloud(user)
                }
            }
            .addOnFailureListener { throwable ->
                onGoogleSignInFailed(
                    throwable.message ?: "Google sign-in failed. Please try again."
                )
            }
    }

    fun onGoogleSignInFailed(message: String) {
        uiStateFlow.update { currentState ->
            currentState.copy(
                isAuthInProgress = false,
                authStatusMessage = message
            )
        }
        appendPlaybackLog("[AUTH] $message")
    }

    fun signOutFromGoogle() {
        firebaseAuth.signOut()
        activeUserId = null
        uiStateFlow.update { currentState ->
            currentState.copy(
                isAuthInProgress = false,
                authStatusMessage = "Signed out."
            )
        }
        appendPlaybackLog("[AUTH] Signed out")
    }

    fun selectTab(tab: RadioMainTab) {
        uiStateFlow.update { currentState ->
            currentState.copy(selectedTab = tab)
        }
    }

    fun updateSearchQuery(query: String) {
        uiStateFlow.update { currentState ->
            currentState.copy(searchQuery = query)
        }
    }

    fun setWeatherEnabled(enabled: Boolean) {
        uiStateFlow.update { currentState ->
            currentState.copy(
                isWeatherEnabled = enabled,
                isWeatherLoading = if (enabled) currentState.isWeatherLoading else false,
                weatherStatusMessage = if (enabled) null else currentState.weatherStatusMessage
            )
        }

        appendPlaybackLog(
            if (enabled) "[WX] Weather strip enabled" else "[WX] Weather strip hidden"
        )
    }

    fun loadLocalWeather(latitude: Double, longitude: Double) {
        if (!uiStateFlow.value.isWeatherEnabled) {
            return
        }

        viewModelScope.launch {
            uiStateFlow.update { currentState ->
                currentState.copy(
                    isWeatherLoading = true,
                    weatherStatusMessage = null
                )
            }

            localWeatherRepository.getLocalForecast(
                latitude = latitude,
                longitude = longitude
            )
                .onSuccess { forecast ->
                    uiStateFlow.update { currentState ->
                        currentState.copy(
                            isWeatherLoading = false,
                            localWeatherForecast = forecast,
                            weatherStatusMessage = null
                        )
                    }
                    appendPlaybackLog("[WX] Local forecast synced")
                }
                .onFailure { throwable ->
                    val errorMessage = throwable.message ?: "Local forecast unavailable."
                    uiStateFlow.update { currentState ->
                        currentState.copy(
                            isWeatherLoading = false,
                            weatherStatusMessage = errorMessage
                        )
                    }
                    appendPlaybackLog("[WX] $errorMessage")
                }
        }
    }

    fun onWeatherUnavailable(message: String) {
        if (!uiStateFlow.value.isWeatherEnabled) {
            return
        }

        uiStateFlow.update { currentState ->
            currentState.copy(
                isWeatherLoading = false,
                weatherStatusMessage = message
            )
        }
        appendPlaybackLog("[WX] $message")
    }

    fun submitStationSearch() {
        loadLofiStations()
    }

    fun selectGenre(genre: String) {
        if (genre.isBlank()) {
            return
        }

        uiStateFlow.update { currentState ->
            currentState.copy(
                selectedGenre = genre,
                searchQuery = ""
            )
        }
        appendPlaybackLog("[GENRE] Switched to $genre (tag mode)")
        loadLofiStations()
    }

    fun loadLofiStations() {
        val activeGenre = uiStateFlow.value.selectedGenre
        val normalizedGenre = normalizeGenreForApi(activeGenre)
        val activeQuery = uiStateFlow.value.searchQuery.trim()
        val effectiveGenre = if (activeQuery.isBlank()) normalizedGenre else ""
        val requestedLimit = when {
            activeQuery.isNotBlank() -> 100
            effectiveGenre.isNotBlank() -> 50
            else -> 80
        }

        viewModelScope.launch {
            uiStateFlow.update { currentState ->
                currentState.copy(
                    isLoading = true,
                    queueSourceLabel = buildQueueSourceLabel(
                        genre = effectiveGenre,
                        query = activeQuery
                    ),
                    loadErrorMessage = null,
                    playbackErrorMessage = null
                )
            }

            getStationSearchResultsUseCase(
                query = activeQuery,
                genre = effectiveGenre,
                limit = requestedLimit
            )
                .onSuccess { stations ->
                    val stationCountLabel = if (activeQuery.isBlank()) {
                        if (effectiveGenre.isBlank()) {
                            "${stations.size} stations online"
                        } else {
                            "${stations.size} ${effectiveGenre.lowercase(Locale.US)} stations online"
                        }
                    } else {
                        "${stations.size} results: \"$activeQuery\""
                    }

                    uiStateFlow.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            stations = stations,
                            stationCountLabel = stationCountLabel,
                            loadErrorMessage = if (stations.isEmpty()) {
                                if (activeQuery.isBlank()) {
                                    if (effectiveGenre.isBlank()) {
                                        "No stations available right now."
                                    } else {
                                        "No ${effectiveGenre.lowercase(Locale.US)} stations available right now."
                                    }
                                } else {
                                    "No stations matched \"$activeQuery\"."
                                }
                            } else {
                                null
                            }
                        )
                    }

                    if (stations.isNotEmpty()) {
                        val sourceLabel = if (activeQuery.isNotBlank()) {
                            "search: $activeQuery"
                        } else if (effectiveGenre.isBlank()) {
                            "all genres"
                        } else {
                            effectiveGenre.lowercase(Locale.US)
                        }
                        appendPlaybackLog("[SYNC] Pulled ${stations.size} stations ($sourceLabel).")
                    }
                    syncQueueWithActiveSource()
                }
                .onFailure { throwable ->
                    val errorMessage = throwable.message ?: "Unable to load stations right now."
                    uiStateFlow.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            loadErrorMessage = errorMessage
                        )
                    }
                    appendPlaybackLog("[ERROR] $errorMessage")
                }
        }
    }

    fun toggleFavorite(station: RadioStation) {
        viewModelScope.launch {
            val isAlreadyFavorite = uiStateFlow.value.favoriteStationIds.contains(station.id)
            val favoriteEntity = station.toFavoriteEntity()
            if (isAlreadyFavorite) {
                favoriteStationDao.deleteFavorite(favoriteEntity)
                appendPlaybackLog("[FAV] Removed ${station.name}")
                deleteFavoriteFromCloud(station.id)
            } else {
                favoriteStationDao.upsertFavorite(favoriteEntity)
                appendPlaybackLog("[FAV] Added ${station.name}")
                upsertFavoriteToCloud(favoriteEntity)
            }
        }
    }

    fun handleSharedStationLink(stationId: String) {
        val normalizedStationId = stationId.trim()
        if (normalizedStationId.isBlank()) {
            return
        }

        pendingSharedStationId = normalizedStationId
        uiStateFlow.update { currentState ->
            currentState.copy(
                selectedTab = RadioMainTab.PLAYER,
                playbackErrorMessage = null
            )
        }
        appendPlaybackLog("[SHARE] Opening station $normalizedStationId")
        resolveAndPlaySharedStation(normalizedStationId)
    }

    fun playSelectedStation(station: RadioStation) {
        val controller = mediaController ?: run {
            reportDisconnectedController()
            return
        }

        val queueStations = buildPlaybackQueue(station)
        val queueItems = queueStations.map { queueStation -> queueStation.toMediaItem() }
        val queueIndex = queueStations.indexOfFirst { queueStation -> queueStation.id == station.id }

        if (queueItems.isEmpty() || queueIndex < 0) {
            uiStateFlow.update { currentState ->
                currentState.copy(playbackErrorMessage = "No playable station in the queue.")
            }
            return
        }

        controller.setMediaItems(queueItems, queueIndex, 0L)
        controller.prepare()
        controller.playWhenReady = true
        controller.play()

        uiStateFlow.update { currentState ->
            currentState.copy(
                playbackErrorMessage = null,
                activeStationId = station.id,
                activeStationSnapshot = station,
                nowPlayingTitle = station.name,
                nowPlayingSubtitle = formatStationSubtitle(station),
                selectedTab = RadioMainTab.PLAYER
            )
        }

        showSignalDiagnostics("[SCAN] Buffering ${station.name}")
    }

    fun togglePlayback() {
        val controller = mediaController ?: run {
            reportDisconnectedController()
            return
        }

        if (controller.mediaItemCount == 0) {
            playFirstAvailableStation()
            return
        }

        if (controller.isPlaying) {
            controller.pause()
            appendPlaybackLog("[PAUSE] Stream paused")
        } else {
            controller.playWhenReady = true
            controller.play()
            appendPlaybackLog("[PLAY] Stream resumed")
        }
    }

    fun playNextStation() {
        val controller = mediaController ?: run {
            reportDisconnectedController()
            return
        }

        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.playWhenReady = true
            controller.play()
            syncPlaybackState()
            showSignalDiagnostics("[SCAN] Switching to next station")
        } else {
            appendPlaybackLog("[INFO] End of station queue.")
        }
    }

    fun playPreviousStation() {
        val controller = mediaController ?: run {
            reportDisconnectedController()
            return
        }

        if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.playWhenReady = true
            controller.play()
            syncPlaybackState()
            showSignalDiagnostics("[SCAN] Returning to previous station")
        } else {
            appendPlaybackLog("[INFO] Already at first station.")
        }
    }

    fun stopPlayback() {
        val controller = mediaController ?: run {
            reportDisconnectedController()
            return
        }

        controller.stop()
        uiStateFlow.update { currentState ->
            currentState.copy(isSignalDiagnosticsVisible = false)
        }
        appendPlaybackLog("[STOP] Playback stopped")
    }

    fun setPlayerVolume(volume: Float) {
        val controller = mediaController ?: run {
            reportDisconnectedController()
            return
        }

        val safeVolume = volume.coerceIn(0f, 1f)
        controller.volume = safeVolume
        uiStateFlow.update { currentState ->
            currentState.copy(playerVolume = safeVolume)
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoriteStationDao.observeFavorites().collectLatest { favorites ->
                val favoriteIds = favorites.map { entity -> entity.stationId }.toSet()
                val favoriteStations = favorites.map { entity -> entity.toRadioStation() }

                uiStateFlow.update { currentState ->
                    currentState.copy(
                        favoriteStationIds = favoriteIds,
                        favoriteStations = favoriteStations
                    )
                }
            }
        }
    }

    private fun handleAuthStateChanged(user: FirebaseUser?) {
        activeUserId = user?.uid
        uiStateFlow.update { currentState ->
            currentState.copy(
                isAuthInProgress = false,
                signedInUserId = user?.uid,
                signedInUserName = user?.displayName,
                signedInUserEmail = user?.email,
                signedInUserPhotoUrl = user?.photoUrl?.toString(),
                authStatusMessage = currentState.authStatusMessage
            )
        }

        if (user != null) {
            syncFavoritesFromCloud(user)
        }
    }

    private fun syncFavoritesFromCloud(user: FirebaseUser) {
        val favoritesCollection = firestore
            .collection(FIRESTORE_USERS_COLLECTION)
            .document(user.uid)
            .collection(FIRESTORE_FAVORITES_COLLECTION)

        favoritesCollection.get()
            .addOnSuccessListener { snapshot ->
                viewModelScope.launch {
                    val cloudFavorites = snapshot.documents
                        .mapNotNull { document -> document.toFavoriteEntityOrNull() }
                    val localFavorites = favoriteStationDao.getFavoritesOnce()
                    val mergedFavorites = (localFavorites + cloudFavorites)
                        .associateBy { entity -> entity.stationId }
                        .values
                        .sortedByDescending { entity -> entity.savedAtEpochMillis }

                    mergedFavorites.forEach { entity ->
                        favoriteStationDao.upsertFavorite(entity)
                    }
                    mergedFavorites.forEach { entity ->
                        favoritesCollection
                            .document(entity.stationId)
                            .set(entity.toFirestoreMap())
                    }

                    uiStateFlow.update { currentState ->
                        currentState.copy(
                            authStatusMessage = "Cloud favorites synced (${mergedFavorites.size})"
                        )
                    }
                    appendPlaybackLog("[SYNC] Cloud favorites synced (${mergedFavorites.size})")
                }
            }
            .addOnFailureListener { throwable ->
                val errorMessage = throwable.message ?: "Unable to sync cloud favorites."
                uiStateFlow.update { currentState ->
                    currentState.copy(authStatusMessage = errorMessage)
                }
                appendPlaybackLog("[SYNC] $errorMessage")
            }
    }

    private fun upsertFavoriteToCloud(entity: FavoriteStationEntity) {
        val userId = activeUserId ?: firebaseAuth.currentUser?.uid
        if (userId == null) {
            uiStateFlow.update { currentState ->
                currentState.copy(
                    authStatusMessage = "Saved locally. Sign in to sync favorites to cloud."
                )
            }
            appendPlaybackLog("[SYNC] No signed-in user. Saved locally only.")
            return
        }

        val payload = entity.toFirestoreMap() + mapOf(
            "userId" to userId,
            "updatedAtEpochMillis" to System.currentTimeMillis()
        )
        val mirrorDocId = buildUserFavoriteMirrorDocId(userId = userId, stationId = entity.stationId)

        firestore
            .collection(FIRESTORE_USERS_COLLECTION)
            .document(userId)
            .collection(FIRESTORE_FAVORITES_COLLECTION)
            .document(entity.stationId)
            .set(payload)
            .addOnSuccessListener {
                firestore
                    .collection(FIRESTORE_USER_FAVORITES_COLLECTION)
                    .document(mirrorDocId)
                    .set(payload)
                    .addOnSuccessListener {
                        uiStateFlow.update { currentState ->
                            currentState.copy(authStatusMessage = "Favorite synced to cloud.")
                        }
                        appendPlaybackLog("[SYNC] Favorite synced for user $userId")
                    }
                    .addOnFailureListener { throwable ->
                        uiStateFlow.update { currentState ->
                            currentState.copy(
                                authStatusMessage = "Partial sync: nested saved, mirror failed."
                            )
                        }
                        appendPlaybackLog(
                            "[SYNC] Mirror write failed: ${throwable.message ?: "Unknown error"}"
                        )
                    }
            }
            .addOnFailureListener { throwable ->
                val message = throwable.message ?: "Unknown error"
                uiStateFlow.update { currentState ->
                    currentState.copy(authStatusMessage = "Cloud sync failed: $message")
                }
                appendPlaybackLog("[SYNC] Failed to save favorite: $message")
            }
    }

    private fun deleteFavoriteFromCloud(stationId: String) {
        val userId = activeUserId ?: firebaseAuth.currentUser?.uid ?: return
        val mirrorDocId = buildUserFavoriteMirrorDocId(userId = userId, stationId = stationId)
        firestore
            .collection(FIRESTORE_USERS_COLLECTION)
            .document(userId)
            .collection(FIRESTORE_FAVORITES_COLLECTION)
            .document(stationId)
            .delete()
            .addOnSuccessListener {
                firestore
                    .collection(FIRESTORE_USER_FAVORITES_COLLECTION)
                    .document(mirrorDocId)
                    .delete()
                uiStateFlow.update { currentState ->
                    currentState.copy(authStatusMessage = "Favorite removed from cloud.")
                }
            }
            .addOnFailureListener { throwable ->
                appendPlaybackLog(
                    "[SYNC] Failed to remove favorite: ${throwable.message ?: "Unknown error"}"
                )
            }
    }

    private fun resolveAndPlaySharedStation(stationId: String) {
        val controller = mediaController
        if (controller == null) {
            appendPlaybackLog("[SHARE] Waiting for player connection")
            return
        }

        val knownStation = findKnownStationById(stationId)
        if (knownStation != null) {
            pendingSharedStationId = null
            playSelectedStation(knownStation)
            uiStateFlow.update { currentState ->
                currentState.copy(queueSourceLabel = "Queue: Shared Station")
            }
            return
        }

        viewModelScope.launch {
            getStationByIdUseCase(stationId)
                .onSuccess { station ->
                    if (station == null) {
                        uiStateFlow.update { currentState ->
                            currentState.copy(
                                playbackErrorMessage = "Shared station could not be found."
                            )
                        }
                        appendPlaybackLog("[SHARE] Station not found: $stationId")
                        return@onSuccess
                    }

                    pendingSharedStationId = null
                    playSelectedStation(station)
                    uiStateFlow.update { currentState ->
                        currentState.copy(
                            activeStationSnapshot = station,
                            queueSourceLabel = "Queue: Shared Station"
                        )
                    }
                    appendPlaybackLog("[SHARE] Loaded ${station.name}")
                }
                .onFailure { throwable ->
                    val message = throwable.message ?: "Unable to open shared station."
                    uiStateFlow.update { currentState ->
                        currentState.copy(playbackErrorMessage = message)
                    }
                    appendPlaybackLog("[SHARE] $message")
                }
        }
    }

    private fun buildPlaybackQueue(selectedStation: RadioStation): List<RadioStation> {
        val availableStations = uiStateFlow.value.stations
        return if (availableStations.any { station -> station.id == selectedStation.id }) {
            availableStations
        } else {
            listOf(selectedStation) + availableStations
        }
    }

    private fun playFirstAvailableStation() {
        val firstStation = uiStateFlow.value.stations.firstOrNull()
            ?: uiStateFlow.value.favoriteStations.firstOrNull()

        if (firstStation != null) {
            playSelectedStation(firstStation)
        } else {
            uiStateFlow.update { currentState ->
                currentState.copy(playbackErrorMessage = "No station available to play.")
            }
        }
    }

    private fun connectMediaController() {
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, NeonMediaSessionService::class.java)
        )

        val mainExecutor = ContextCompat.getMainExecutor(appContext)
        controllerFuture = MediaController.Builder(appContext, sessionToken)
            .buildAsync()
            .also { future ->
                future.addListener(
                    {
                        runCatching { future.get() }
                            .onSuccess { controller ->
                                mediaController = controller
                                controller.addListener(playerListener)
                                syncQueueWithActiveSource()
                                syncPlaybackState()
                                pendingSharedStationId?.let(::resolveAndPlaySharedStation)
                                uiStateFlow.update { currentState ->
                                    currentState.copy(
                                        isControllerConnected = true,
                                        playbackErrorMessage = null
                                    )
                                }
                                appendPlaybackLog("[LINK] Connected to media session")
                            }
                            .onFailure { throwable ->
                                val errorMessage = throwable.message
                                    ?: "Failed to connect to playback service."
                                uiStateFlow.update { currentState ->
                                    currentState.copy(
                                        isControllerConnected = false,
                                        playbackErrorMessage = errorMessage
                                    )
                                }
                                appendPlaybackLog("[ERROR] $errorMessage")
                            }
                    },
                    mainExecutor
                )
            }
    }

    private fun syncQueueWithActiveSource() {
        val controller = mediaController ?: return
        val stations = uiStateFlow.value.stations

        if (stations.isEmpty()) {
            return
        }

        val currentMediaId = controller.currentMediaItem?.mediaId
        val currentKnownStation = findKnownStationById(currentMediaId)

        val queueStations = if (
            currentKnownStation != null &&
            stations.none { station -> station.id == currentKnownStation.id }
        ) {
            listOf(currentKnownStation) + stations
        } else {
            stations
        }

        val queueItems = queueStations.map { station -> station.toMediaItem() }
        val queueIndex = queueStations.indexOfFirst { station -> station.id == currentMediaId }
            .takeIf { it >= 0 }
            ?: 0
        val queuePositionMs = if (queueStations.getOrNull(queueIndex)?.id == currentMediaId) {
            controller.currentPosition.coerceAtLeast(0L)
        } else {
            0L
        }
        val shouldResume = controller.playWhenReady || controller.isPlaying

        controller.setMediaItems(queueItems, queueIndex, queuePositionMs)
        controller.prepare()
        controller.playWhenReady = shouldResume
        if (shouldResume) {
            controller.play()
        }

        if (controller.mediaItemCount <= 1) {
            appendPlaybackLog("[QUEUE] Queue primed for playback")
        } else {
            appendPlaybackLog("[QUEUE] Queue synced to active source")
        }
    }

    private fun syncPlaybackState() {
        val controller = mediaController ?: return
        val playbackStateLabel = when (controller.playbackState) {
            Player.STATE_IDLE -> "Idle"
            Player.STATE_BUFFERING -> "Buffering"
            Player.STATE_READY -> if (controller.isPlaying) "Playing" else "Ready"
            Player.STATE_ENDED -> "Ended"
            else -> "Unknown"
        }

        val mediaId = controller.currentMediaItem?.mediaId
        val station = findKnownStationById(mediaId)
            ?: uiStateFlow.value.activeStationSnapshot?.takeIf { activeStation ->
                activeStation.id == mediaId
            }

        val nowPlayingTitle = station?.name
            ?: controller.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()

        uiStateFlow.update { currentState ->
            currentState.copy(
                nowPlayingTitle = nowPlayingTitle.ifBlank { "No station selected" },
                nowPlayingSubtitle = station?.let(::formatStationSubtitle)
                    ?: "Tap a station to begin streaming",
                activeStationId = mediaId,
                activeStationSnapshot = station,
                isPlaying = controller.isPlaying,
                playbackStatusLabel = playbackStateLabel,
                canPlayNext = controller.hasNextMediaItem(),
                canPlayPrevious = controller.hasPreviousMediaItem(),
                playerVolume = controller.volume.coerceIn(0f, 1f)
            )
        }
    }

    private fun findKnownStationById(stationId: String?): RadioStation? {
        if (stationId.isNullOrBlank()) {
            return null
        }

        val stationsById = (
            listOfNotNull(uiStateFlow.value.activeStationSnapshot) +
                uiStateFlow.value.stations +
                uiStateFlow.value.favoriteStations
            )
            .associateBy { station -> station.id }

        return stationsById[stationId]
    }

    private fun showSignalDiagnostics(message: String) {
        uiStateFlow.update { currentState ->
            currentState.copy(
                isSignalDiagnosticsVisible = true,
                playbackErrorMessage = null
            )
        }
        appendPlaybackLog(message)
    }

    private fun maybeHideSignalDiagnostics(playbackState: Int) {
        val shouldHide = when (playbackState) {
            Player.STATE_READY -> uiStateFlow.value.isPlaying
            Player.STATE_IDLE,
            Player.STATE_ENDED -> true
            else -> false
        }

        if (shouldHide) {
            uiStateFlow.update { currentState ->
                currentState.copy(isSignalDiagnosticsVisible = false)
            }
        }
    }

    private fun formatStationSubtitle(station: RadioStation): String {
        val location = station.country.ifBlank { "Unknown Country" }
        val tags = station.tags.ifBlank { "Lofi" }
        return "$location • $tags"
    }

    private fun reportDisconnectedController() {
        val message = "Player is still connecting."
        uiStateFlow.update { currentState ->
            currentState.copy(playbackErrorMessage = message)
        }
        appendPlaybackLog("[WARN] $message")
    }

    private fun appendPlaybackLog(message: String) {
        val timestamp = timestampFormatter.format(Date())
        val line = "$timestamp $message"

        uiStateFlow.update { currentState ->
            currentState.copy(
                playbackLogLines = (listOf(line) + currentState.playbackLogLines)
                    .take(MAX_LOG_LINES)
            )
        }
    }

    override fun onCleared() {
        firebaseAuth.removeAuthStateListener(authStateListener)
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        controllerFuture = null
        mediaController = null
        super.onCleared()
    }

    companion object {
        private const val MAX_LOG_LINES = 8
        private const val FIRESTORE_USERS_COLLECTION = "users"
        private const val FIRESTORE_FAVORITES_COLLECTION = "favorites"
        private const val FIRESTORE_USER_FAVORITES_COLLECTION = "user_favorites"
        private val timestampFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}

private fun buildQueueSourceLabel(
    genre: String,
    query: String
): String {
    val normalizedGenre = genre.ifBlank { "All Genres" }
    val trimmedQuery = query.trim()
    return if (trimmedQuery.isNotBlank()) {
        "Queue: Search \"$trimmedQuery\""
    } else if (normalizedGenre == "All Genres") {
        "Queue: All Genres"
    } else {
        "Queue: Genre $normalizedGenre"
    }
}

private fun normalizeGenreForApi(genre: String): String {
    val trimmed = genre.trim()
    return if (trimmed.equals("All", ignoreCase = true)) {
        ""
    } else {
        trimmed
    }
}

class RadioScreenViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RadioScreenViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RadioScreenViewModel(appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
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

private fun FavoriteStationEntity.toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "stationId" to stationId,
        "name" to name,
        "streamUrl" to streamUrl,
        "favicon" to favicon,
        "tags" to tags,
        "country" to country,
        "savedAtEpochMillis" to savedAtEpochMillis
    )
}

private fun buildUserFavoriteMirrorDocId(userId: String, stationId: String): String {
    val safeStationId = stationId.replace("/", "_")
    return "${userId}_$safeStationId"
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
