package com.neonhorizon.mobile.features.radio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WebAsset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.neonhorizon.features.radio.model.RadioStation
import com.neonhorizon.features.theme.VaporTheme
import com.neonhorizon.features.weather.model.WeatherDayForecast
import com.neonhorizon.shared.R as SharedR
import com.neonhorizon.mobile.R as MobileR
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun RadioScreen(
    vaporTheme: VaporTheme,
    uiState: RadioScreenUiState,
    onApplySunsetHorizon: () -> Unit,
    onRetryLoad: () -> Unit,
    onSelectStation: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onSelectTab: (RadioMainTab) -> Unit,
    onTogglePlayback: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onStopPlayback: () -> Unit,
    onSetPlayerVolume: (Float) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onSelectGenre: (String) -> Unit,
    onShareStation: (RadioStation) -> Unit,
    onSetWeatherEnabled: (Boolean) -> Unit,
    onRequestGoogleSignIn: () -> Unit,
    onGoogleSignOut: () -> Unit
) {
    var isPlayerMaximized by rememberSaveable { mutableStateOf(false) }
    var isSettingsWindowOpen by rememberSaveable { mutableStateOf(false) }

    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            Color(vaporTheme.neonColorPrimary).copy(alpha = 0.34f),
            Color(0xFF090313),
            Color(vaporTheme.neonColorSecondary).copy(alpha = 0.28f)
        )
    )

    val activeStation = uiState.activeStationSnapshot ?: (uiState.stations + uiState.favoriteStations)
        .firstOrNull { station -> station.id == uiState.activeStationId }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = vaporTheme.backgroundRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .alpha(0.68f)
        )

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                ClassicTaskbar(
                    selectedTab = uiState.selectedTab,
                    onSelectTab = { selectedTab ->
                        if (selectedTab != RadioMainTab.PLAYER) {
                            isPlayerMaximized = false
                        }
                        onSelectTab(selectedTab)
                    },
                    onOpenSettings = { isSettingsWindowOpen = true }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(innerPadding)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                when (uiState.selectedTab) {
                    RadioMainTab.PLAYER -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (!isPlayerMaximized && uiState.isWeatherEnabled) {
                                ClassicWeatherWindow(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    uiState = uiState,
                                    onMinimize = { onSetWeatherEnabled(false) }
                                )
                            }

                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                ClassicPlayerWindow(
                                    modifier = if (isPlayerMaximized) {
                                        Modifier.fillMaxSize()
                                    } else {
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 84.dp)
                                    },
                                    uiState = uiState,
                                    activeStation = activeStation,
                                    isMaximized = isPlayerMaximized,
                                    onToggleMaximize = {
                                        isPlayerMaximized = !isPlayerMaximized
                                    },
                                    onTogglePlayback = onTogglePlayback,
                                    onPlayNext = onPlayNext,
                                    onPlayPrevious = onPlayPrevious,
                                    onStopPlayback = onStopPlayback,
                                    onToggleFavorite = {
                                        if (activeStation != null) {
                                            onToggleFavorite(activeStation)
                                        }
                                    },
                                    onShareStation = {
                                        if (activeStation != null) {
                                            onShareStation(activeStation)
                                        }
                                    },
                                    onSetPlayerVolume = onSetPlayerVolume,
                                    onSelectGenre = onSelectGenre,
                                    onSelectStation = onSelectStation
                                )
                            }
                        }
                    }

                    RadioMainTab.STATIONS -> {
                        ClassicStationListWindow(
                            title = "Neon Horizon Radio",
                            stations = uiState.stations,
                            activeStationId = uiState.activeStationId,
                            favoriteStationIds = uiState.favoriteStationIds,
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.loadErrorMessage,
                            emptyMessage = "No stations available.",
                            searchQuery = uiState.searchQuery,
                            selectedGenre = uiState.selectedGenre,
                            availableGenres = uiState.availableGenres,
                            onSearchQueryChange = onSearchQueryChange,
                            onSubmitSearch = onSubmitSearch,
                            onSelectGenre = onSelectGenre,
                            onSelectStation = onSelectStation,
                            onToggleFavorite = onToggleFavorite,
                            onRetryLoad = onRetryLoad
                        )
                    }

                    RadioMainTab.FAVORITES -> {
                        ClassicFavoritesWindow(
                            title = "Neon Horizon Radio",
                            stations = uiState.favoriteStations,
                            activeStationId = uiState.activeStationId,
                            favoriteStationIds = uiState.favoriteStationIds,
                            emptyMessage = "No favorites yet. Tap the heart on any station to save it here.",
                            onSelectStation = onSelectStation,
                            onToggleFavorite = onToggleFavorite
                        )
                    }

                }
            }
        }

        if (!uiState.signedInUserPhotoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 10.dp)
                    .border(2.dp, Color(0xFF8E8E8E))
                    .background(Color(0xFF101427))
                    .padding(2.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = uiState.signedInUserPhotoUrl),
                    contentDescription = "Signed in user avatar",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (isSettingsWindowOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 24.dp)
                ) {
                    ClassicSettingsWindow(
                        vaporTheme = vaporTheme,
                        uiState = uiState,
                        onApplySunsetHorizon = onApplySunsetHorizon,
                        onSetWeatherEnabled = onSetWeatherEnabled,
                        onRequestGoogleSignIn = onRequestGoogleSignIn,
                        onGoogleSignOut = onGoogleSignOut,
                        onClose = { isSettingsWindowOpen = false }
                    )
                }
            }
        }

        if (uiState.isSignalDiagnosticsVisible) {
            SignalDiagnosticsModal(uiState = uiState)
        }
    }
}

@Composable
private fun ClassicPlayerWindow(
    modifier: Modifier,
    uiState: RadioScreenUiState,
    activeStation: RadioStation?,
    isMaximized: Boolean,
    onToggleMaximize: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onStopPlayback: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShareStation: () -> Unit,
    onSetPlayerVolume: (Float) -> Unit,
    onSelectGenre: (String) -> Unit,
    onSelectStation: (RadioStation) -> Unit
) {
    val fallbackPainter = painterResource(id = SharedR.drawable.logo_neon_horizon)
    val albumArtModel = activeStation?.favicon?.takeIf { url ->
        url.startsWith("http://") || url.startsWith("https://")
    }

    val marqueeCountry = activeStation?.country
        ?.takeIf { it.isNotBlank() }
        ?: uiState.nowPlayingSubtitle
            .substringBefore("•")
            .trim()
            .takeIf { it.isNotBlank() && it != "Tap a station to begin streaming" }
    val marqueeText = buildString {
        val stationTitle = uiState.nowPlayingTitle.ifBlank { "No station selected" }
        append(stationTitle)
        if (stationTitle != "No station selected" && marqueeCountry != null) {
            append("   •   ")
            append(marqueeCountry)
        }
    }
    val discoveryStations = remember(
        uiState.stations,
        uiState.selectedGenre,
        uiState.activeStationId
    ) {
        pickDiscoveryStations(
            stations = uiState.stations,
            activeStationId = uiState.activeStationId,
            selectedGenre = uiState.selectedGenre
        )
    }

    ClassicWindowFrame(
        modifier = modifier,
        title = "Neon Horizon Radio",
        showMaximizeButton = true,
        isMaximized = isMaximized,
        onToggleMaximize = onToggleMaximize
    ) {
        if (isMaximized) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val coverWidth = if (maxWidth < 560.dp) maxWidth else 560.dp

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(coverWidth)
                            .height(300.dp)
                            .border(2.dp, Color(0xFF8E8E8E))
                            .background(Color(0xFF1B1D2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = albumArtModel,
                            contentDescription = "Station Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = fallbackPainter,
                            error = fallbackPainter,
                            fallback = fallbackPainter
                        )
                    }

                    Column(
                        modifier = Modifier.width(coverWidth),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StationMarquee(
                            text = marqueeText,
                            isPlaying = uiState.isPlaying
                        )

                        QueueSourceTab(label = uiState.queueSourceLabel)

                        PlayerControlsRow(
                            uiState = uiState,
                            activeStation = activeStation,
                            onToggleFavorite = onToggleFavorite,
                            onShareStation = onShareStation,
                            onPlayPrevious = onPlayPrevious,
                            onTogglePlayback = onTogglePlayback,
                            onPlayNext = onPlayNext,
                            onStopPlayback = onStopPlayback,
                            evenlySpaced = true
                        )

                        VolumeRow(
                            playerVolume = uiState.playerVolume,
                            onSetPlayerVolume = onSetPlayerVolume
                        )

                        if (discoveryStations.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                discoveryStations.forEach { station ->
                                    DiscoveryStationCard(
                                        station = station,
                                        onSelectStation = onSelectStation
                                    )
                                }
                                repeat(2 - discoveryStations.size) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(92.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, Color(0xFF9A9A9A))
                        .background(Color(0xFFE7E7E7))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Stations: ${uiState.stations.size}   Favorites: ${uiState.favoriteStations.size}   Status: ${uiState.playbackStatusLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }

                GenreSelectorBar(
                    genres = uiState.availableGenres,
                    selectedGenre = uiState.selectedGenre,
                    onSelectGenre = onSelectGenre
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .border(2.dp, Color(0xFF8E8E8E))
                            .background(Color(0xFF1B1D2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = albumArtModel,
                            contentDescription = "Station Cover",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                            placeholder = fallbackPainter,
                            error = fallbackPainter,
                            fallback = fallbackPainter
                        )
                    }

                    StationMarquee(
                        text = marqueeText,
                        isPlaying = uiState.isPlaying
                    )

                    QueueSourceTab(label = uiState.queueSourceLabel)

                    PlayerControlsRow(
                        uiState = uiState,
                        activeStation = activeStation,
                        onToggleFavorite = onToggleFavorite,
                        onShareStation = onShareStation,
                        onPlayPrevious = onPlayPrevious,
                        onTogglePlayback = onTogglePlayback,
                        onPlayNext = onPlayNext,
                        onStopPlayback = onStopPlayback,
                        showStopButton = false,
                        isCompact = true
                    )

                    VolumeRow(
                        playerVolume = uiState.playerVolume,
                        onSetPlayerVolume = onSetPlayerVolume
                    )
                }
            }
        }

        if (uiState.playbackErrorMessage != null) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = uiState.playbackErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8B1D1D)
            )
        }
    }
}

@Composable
private fun ClassicWeatherWindow(
    modifier: Modifier = Modifier,
    uiState: RadioScreenUiState,
    onMinimize: () -> Unit
) {
    ClassicWindowFrame(
        title = "Local Forecast",
        modifier = modifier,
        onMinimize = onMinimize
    ) {
        when {
            uiState.localWeatherForecast != null -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WeatherDayCard(
                            modifier = Modifier.weight(1f),
                            dayForecast = uiState.localWeatherForecast.today
                        )
                        WeatherDayCard(
                            modifier = Modifier.weight(1f),
                            dayForecast = uiState.localWeatherForecast.tomorrow
                        )
                    }

                    if (uiState.isWeatherLoading) {
                        Text(
                            text = "Refreshing local forecast...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.72f)
                        )
                    }
                }
            }

            uiState.isWeatherLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Loading local forecast...",
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            !uiState.weatherStatusMessage.isNullOrBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, Color(0xFF8E8E8E))
                        .background(Color(0xFFE7E7E7))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = uiState.weatherStatusMessage,
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, Color(0xFF8E8E8E))
                        .background(Color(0xFFE7E7E7))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Waiting for local forecast...",
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherDayCard(
    modifier: Modifier = Modifier,
    dayForecast: WeatherDayForecast
) {
    Box(
        modifier = modifier
            .border(2.dp, Color(0xFF8E8E8E))
            .background(Color(0xFFE7E7E7))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = dayForecast.dayLabel,
                color = Color.Black,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = dayForecast.summary,
                color = Color.Black,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "High ${dayForecast.highTemperature}${dayForecast.temperatureUnitSymbol}  Low ${dayForecast.lowTemperature}${dayForecast.temperatureUnitSymbol}",
                color = Color.Black.copy(alpha = 0.84f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Rain ${dayForecast.precipitationChance}%",
                color = Color.Black.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ClassicStationListWindow(
    title: String,
    stations: List<RadioStation>,
    activeStationId: String?,
    favoriteStationIds: Set<String>,
    isLoading: Boolean,
    errorMessage: String?,
    emptyMessage: String,
    searchQuery: String,
    selectedGenre: String,
    availableGenres: List<String>,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onSelectGenre: (String) -> Unit,
    onSelectStation: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onRetryLoad: () -> Unit
) {
    val stationsListState = rememberLazyListState()
    val shouldShowScrollCue = stations.size > 6

    ClassicWindowFrame(
        title = title,
        modifier = Modifier.heightIn(min = 260.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OldSchoolSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onSubmitSearch = onSubmitSearch
            )

            GenreSelectorBar(
                genres = availableGenres,
                selectedGenre = selectedGenre,
                onSelectGenre = onSelectGenre
            )

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Loading stations...", color = Color.Black)
                    }
                }

                errorMessage != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = errorMessage,
                            color = Color(0xFF8B1D1D),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = onRetryLoad) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Retry",
                                tint = Color.Black
                            )
                        }
                    }
                }

                stations.isEmpty() -> {
                    Text(text = emptyMessage, color = Color.Black)
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 460.dp)
                    ) {
                        LazyColumn(
                            state = stationsListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = if (shouldShowScrollCue) 14.dp else 0.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(stations, key = { station -> station.id }) { station ->
                                ClassicStationRow(
                                    station = station,
                                    isActive = activeStationId == station.id,
                                    isFavorite = favoriteStationIds.contains(station.id),
                                    onSelectStation = { onSelectStation(station) },
                                    onToggleFavorite = { onToggleFavorite(station) }
                                )
                            }
                        }

                        if (shouldShowScrollCue) {
                            ClassicVerticalScrollbar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 2.dp),
                                listState = stationsListState,
                                totalItems = stations.size
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicFavoritesWindow(
    title: String,
    stations: List<RadioStation>,
    activeStationId: String?,
    favoriteStationIds: Set<String>,
    emptyMessage: String,
    onSelectStation: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit
) {
    val favoritesListState = rememberLazyListState()
    val shouldShowScrollCue = stations.size > 6

    ClassicWindowFrame(title = title) {
        ClassicMenuStrip(items = listOf("Favorites", "Saved", "Queue"))

        Spacer(modifier = Modifier.height(8.dp))

        if (stations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFF8E8E8E))
                    .background(Color(0xFFEFEFEF))
                    .padding(12.dp)
            ) {
                Text(
                    text = emptyMessage,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = 140.dp,
                        max = if (shouldShowScrollCue) 330.dp else 420.dp
                    )
            ) {
                LazyColumn(
                    state = favoritesListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = if (shouldShowScrollCue) 14.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(stations, key = { station -> station.id }) { station ->
                        ClassicStationRow(
                            station = station,
                            isActive = activeStationId == station.id,
                            isFavorite = favoriteStationIds.contains(station.id),
                            onSelectStation = { onSelectStation(station) },
                            onToggleFavorite = { onToggleFavorite(station) }
                        )
                    }
                }

                if (shouldShowScrollCue) {
                    ClassicVerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp),
                        listState = favoritesListState,
                        totalItems = stations.size
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicVerticalScrollbar(
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState,
    totalItems: Int
) {
    val layoutInfo = listState.layoutInfo
    val visibleItemsCount = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val firstVisibleItemIndex = listState.firstVisibleItemIndex.coerceAtMost(totalItems - 1)
    val maxScrollableItems = (totalItems - visibleItemsCount).coerceAtLeast(1)
    val thumbFraction = (visibleItemsCount.toFloat() / totalItems.toFloat())
        .coerceIn(0.18f, 1f)
    val progressFraction = (firstVisibleItemIndex.toFloat() / maxScrollableItems.toFloat())
        .coerceIn(0f, 1f)
    var trackHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .width(10.dp)
            .fillMaxHeight()
            .onSizeChanged { size -> trackHeightPx = size.height }
            .border(2.dp, Color(0xFF8E8E8E))
            .background(Color(0xFFD7D7D7))
            .padding(1.dp)
    ) {
        val thumbHeightPx = (trackHeightPx * thumbFraction).roundToInt()
        val thumbTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(thumbFraction)
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(
                        x = 0,
                        y = (progressFraction * thumbTravelPx).roundToInt()
                    )
                }
                .border(2.dp, Color(0xFF7B7B7B))
                .background(Color(0xFFF0F0F0))
        )
    }
}

@Composable
private fun ClassicSettingsWindow(
    vaporTheme: VaporTheme,
    uiState: RadioScreenUiState,
    onApplySunsetHorizon: () -> Unit,
    onSetWeatherEnabled: (Boolean) -> Unit,
    onRequestGoogleSignIn: () -> Unit,
    onGoogleSignOut: () -> Unit,
    onClose: () -> Unit
) {
    ClassicWindowFrame(
        title = "Neon Horizon Radio Settings",
        onMinimize = onClose
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Account",
                style = MaterialTheme.typography.titleSmall,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            val signedInLabel = uiState.signedInUserEmail
                ?: uiState.signedInUserName
                ?: "Not signed in"

            Text(
                text = signedInLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.signedInUserEmail.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .border(2.dp, Color(0xFF8E8E8E))
                        .background(Color(0xFFF2F2F2))
                        .clickable(
                            enabled = !uiState.isAuthInProgress,
                            onClick = onRequestGoogleSignIn
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = MobileR.drawable.google_signin_wordmark),
                        contentDescription = "Sign in with Google",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .border(2.dp, Color(0xFF8E8E8E))
                        .background(Color(0xFFDADADA))
                        .clickable(
                            enabled = !uiState.isAuthInProgress,
                            onClick = onGoogleSignOut
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Sign out",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!uiState.authStatusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.authStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF303030)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Active Theme: ${vaporTheme.name}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Apply the Sunset Horizon palette for mobile and auto surfaces.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.84f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportIconButton(
                    icon = Icons.Filled.Palette,
                    contentDescription = "Apply Sunset Horizon",
                    onClick = onApplySunsetHorizon,
                    enabled = true,
                    isPrimary = true,
                    width = 64.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Apply Sunset Horizon",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFF8E8E8E))
                    .background(Color(0xFFEDEDED))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Weather",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Show the local two-day forecast above the player.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .border(
                                    2.dp,
                                    if (uiState.isWeatherEnabled) Color(0xFF5E5E5E) else Color(0xFF8E8E8E)
                                )
                                .background(if (uiState.isWeatherEnabled) Color(0xFFDCE8FF) else Color(0xFFDADADA))
                                .clickable { onSetWeatherEnabled(true) }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "On",
                                color = Color.Black,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (uiState.isWeatherEnabled) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .border(
                                    2.dp,
                                    if (!uiState.isWeatherEnabled) Color(0xFF5E5E5E) else Color(0xFF8E8E8E)
                                )
                                .background(if (!uiState.isWeatherEnabled) Color(0xFFDCE8FF) else Color(0xFFDADADA))
                                .clickable { onSetWeatherEnabled(false) }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Off",
                                color = Color.Black,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (!uiState.isWeatherEnabled) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFF8E8E8E))
                    .background(Color(0xFFEDEDED))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Coming Soon",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Audio quality presets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                    Text(
                        text = "Diagnostics and logging controls",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                    Text(
                        text = "Offline cache settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TransportIconButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = "Close Settings",
                    onClick = onClose,
                    enabled = true,
                    width = 64.dp
                )
            }
        }
    }
}

@Composable
private fun VolumeRow(
    playerVolume: Float,
    onSetPlayerVolume: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(22.dp)
        )
        Slider(
            modifier = Modifier.weight(1f),
            value = playerVolume,
            onValueChange = onSetPlayerVolume
        )
    }
}

@Composable
private fun PlayerControlsRow(
    uiState: RadioScreenUiState,
    activeStation: RadioStation?,
    onToggleFavorite: () -> Unit,
    onShareStation: () -> Unit,
    onPlayPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPlayNext: () -> Unit,
    onStopPlayback: () -> Unit,
    showStopButton: Boolean = true,
    isCompact: Boolean = false,
    evenlySpaced: Boolean = false
) {
    val buttonWidth = if (isCompact) 56.dp else 48.dp
    val primaryButtonWidth = if (isCompact) 56.dp else 76.dp
    val buttonHeight = if (isCompact) 52.dp else 48.dp
    val iconSize = if (isCompact) 27.dp else 24.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCompact || evenlySpaced) Arrangement.SpaceEvenly else Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportIconButton(
            icon = if (activeStation != null && uiState.favoriteStationIds.contains(activeStation.id)) {
                Icons.Filled.Favorite
            } else {
                Icons.Filled.FavoriteBorder
            },
            contentDescription = "Favorite",
            onClick = onToggleFavorite,
            enabled = activeStation != null,
            width = buttonWidth,
            height = buttonHeight,
            iconSize = iconSize
        )
        TransportIconButton(
            icon = Icons.Filled.Share,
            contentDescription = "Share",
            onClick = onShareStation,
            enabled = activeStation != null,
            width = buttonWidth,
            height = buttonHeight,
            iconSize = iconSize
        )
        TransportIconButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Previous",
            onClick = onPlayPrevious,
            enabled = uiState.isControllerConnected && uiState.canPlayPrevious,
            width = buttonWidth,
            height = buttonHeight,
            iconSize = iconSize
        )
        TransportIconButton(
            icon = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
            onClick = onTogglePlayback,
            enabled = uiState.isControllerConnected,
            isPrimary = true,
            width = primaryButtonWidth,
            height = buttonHeight,
            iconSize = iconSize
        )
        TransportIconButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Next",
            onClick = onPlayNext,
            enabled = uiState.isControllerConnected && uiState.canPlayNext,
            width = buttonWidth,
            height = buttonHeight,
            iconSize = iconSize
        )
        if (showStopButton) {
            TransportIconButton(
                icon = Icons.Filled.Stop,
                contentDescription = "Stop",
                onClick = onStopPlayback,
                enabled = uiState.isControllerConnected,
                width = buttonWidth,
                height = buttonHeight,
                iconSize = iconSize
            )
        }
    }
}

@Composable
private fun GenreSelectorBar(
    genres: List<String>,
    selectedGenre: String,
    onSelectGenre: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        genres.forEach { genre ->
            val selected = genre.equals(selectedGenre, ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .border(2.dp, if (selected) Color(0xFF5E5E5E) else Color(0xFF8E8E8E))
                    .background(if (selected) Color(0xFFDCE8FF) else Color(0xFFDADADA))
                    .clickable { onSelectGenre(genre) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = genre,
                    color = Color.Black,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun OldSchoolSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .border(2.dp, Color(0xFF8E8E8E))
                .background(Color(0xFFEFEFEF))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.Black,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    fontWeight = MaterialTheme.typography.bodyMedium.fontWeight
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onSubmitSearch()
                    }
                ),
                decorationBox = { innerTextField ->
                    if (query.isBlank()) {
                        Text(
                            text = "Search by genre, location, instrument, language...",
                            color = Color.Black.copy(alpha = 0.52f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    innerTextField()
                }
            )
        }

        TransportIconButton(
            icon = Icons.Filled.Search,
            contentDescription = "Search",
            onClick = {
                focusManager.clearFocus()
                onSubmitSearch()
            },
            enabled = true,
            width = 44.dp
        )
    }
}

@Composable
private fun QueueSourceTab(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color(0xFF8E8E8E))
            .background(Color(0xFFD9D9D9))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ClassicTaskbar(
    selectedTab: RadioMainTab,
    onSelectTab: (RadioMainTab) -> Unit,
    onOpenSettings: () -> Unit
) {
    val displayTime by produceState(initialValue = current24HourTime()) {
        while (true) {
            value = current24HourTime()
            delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .border(width = 2.dp, color = Color(0xFF8E8E8E))
                .background(Color(0xFFC8C8C8))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TaskbarTabButton(
                icon = Icons.Filled.WebAsset,
                label = "Player",
                selected = selectedTab == RadioMainTab.PLAYER,
                onClick = { onSelectTab(RadioMainTab.PLAYER) }
            )
            TaskbarTabButton(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "Stations",
                selected = selectedTab == RadioMainTab.STATIONS,
                onClick = { onSelectTab(RadioMainTab.STATIONS) }
            )
            TaskbarTabButton(
                icon = Icons.Filled.Favorite,
                label = "Favorites",
                selected = selectedTab == RadioMainTab.FAVORITES,
                onClick = { onSelectTab(RadioMainTab.FAVORITES) }
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .border(2.dp, Color(0xFF8E8E8E))
                    .background(Color(0xFFDADADA))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .width(2.dp)
                            .background(Color(0xFF8E8E8E))
                    )

                    Text(
                        text = displayTime,
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskbarTabButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val buttonColor = if (selected) Color(0xFFDCE8FF) else Color(0xFFD2D2D2)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .border(2.dp, if (selected) Color(0xFF5E5E5E) else Color(0xFF8E8E8E))
            .background(buttonColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
        Text(
            text = label,
            color = Color.Black,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ClassicWindowFrame(
    title: String,
    modifier: Modifier = Modifier,
    showMaximizeButton: Boolean = false,
    isMaximized: Boolean = false,
    onMinimize: (() -> Unit)? = null,
    onToggleMaximize: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, Color(0xFFEAEAEA))
            .background(Color(0xFFC8C8C8))
            .padding(5.dp)
            .border(2.dp, Color(0xFF8E8E8E))
            .background(Color(0xFFC8C8C8))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF0A2A8B), Color(0xFF1C78D3))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Radio,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))

            if (onMinimize != null) {
                IconButton(
                    modifier = Modifier.size(18.dp),
                    onClick = onMinimize
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Minimize",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }

            if (showMaximizeButton) {
                IconButton(
                    modifier = Modifier.size(18.dp),
                    onClick = { onToggleMaximize?.invoke() }
                ) {
                    Icon(
                        imageVector = if (isMaximized) {
                            Icons.Filled.CloseFullscreen
                        } else {
                            Icons.Filled.OpenInFull
                        },
                        contentDescription = "Maximize",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ClassicMenuStrip(items: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items.forEach { item ->
            Text(
                text = item,
                color = Color.Black,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ClassicStationRow(
    station: RadioStation,
    isActive: Boolean,
    isFavorite: Boolean,
    onSelectStation: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val rowColor = if (isActive) Color(0xFFDCE8FF) else Color(0xFFE6E6E6)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color(0xFF969696))
            .background(rowColor)
            .clickable(onClick = onSelectStation)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = station.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${station.country.ifBlank { "Unknown" }} • ${station.tags.ifBlank { "Lofi" }}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black.copy(alpha = 0.8f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (isFavorite) Color(0xFF9A123A) else Color.Black
                )
            }
            IconButton(onClick = onSelectStation) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play Station",
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isPrimary: Boolean = false,
    width: Dp = 48.dp,
    height: Dp = 48.dp,
    iconSize: Dp = 24.dp
) {
    val background = if (isPrimary) Color(0xFFDCE8FF) else Color(0xFFDADADA)

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .border(2.dp, Color(0xFF8E8E8E))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) Color.Black else Color(0xFF777777),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun RowScope.DiscoveryStationCard(
    station: RadioStation,
    onSelectStation: (RadioStation) -> Unit
) {
    val fallbackPainter = painterResource(id = SharedR.drawable.logo_neon_horizon)
    val artModel = station.favicon.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(92.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(2.dp, Color(0xFF8E8E8E))
            .background(Color(0xFF171A2A))
            .clickable { onSelectStation(station) }
    ) {
        AsyncImage(
            model = artModel,
            contentDescription = station.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            fallback = fallbackPainter
        )
    }
}

@Composable
private fun StationMarquee(
    text: String,
    isPlaying: Boolean
) {
    val marqueeText = text.ifBlank { "No station selected" }
    val textStyle = MaterialTheme.typography.bodyLarge
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var textWidthPx by remember(marqueeText) { mutableIntStateOf(0) }
    val offsetX = remember(marqueeText) { Animatable(0f) }
    var hasStartedScrolling by remember(marqueeText) { mutableStateOf(false) }
    val tickerGapPx = 32f
    val shouldAnimate = isPlaying &&
        marqueeText != "No station selected" &&
        textWidthPx > 0 &&
        containerWidthPx > 0
    val itemWidthPx = (textWidthPx.toFloat() + tickerGapPx).coerceAtLeast(1f)
    val repeatedCopies = remember(containerWidthPx, itemWidthPx) {
        if (containerWidthPx <= 0 || itemWidthPx <= 0f) {
            2
        } else {
            kotlin.math.max(
                2,
                kotlin.math.ceil((containerWidthPx * 2f) / itemWidthPx).toInt() + 1
            )
        }
    }

    LaunchedEffect(isPlaying, marqueeText, textWidthPx, containerWidthPx) {
        if (!shouldAnimate) {
            offsetX.stop()
            return@LaunchedEffect
        }

        hasStartedScrolling = true
        var lastFrameNanos = 0L

        while (isActive) {
            val frameNanos = withFrameNanos { it }
            if (lastFrameNanos != 0L) {
                val elapsedSeconds =
                    (frameNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000f
                var nextOffset = offsetX.value - (MARQUEE_SPEED_PX_PER_SECOND * elapsedSeconds)

                while (nextOffset <= -itemWidthPx) {
                    nextOffset += itemWidthPx
                }

                offsetX.snapTo(nextOffset)
            }
            lastFrameNanos = frameNanos
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color(0xFF8E8E8E))
            .background(Color(0xFFE5E5E5))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp)
                .clipToBounds()
                .onSizeChanged { size -> containerWidthPx = size.width }
        ) {
            if (textWidthPx > 0 && marqueeText != "No station selected" && (isPlaying || hasStartedScrolling)) {
                Row(
                    modifier = Modifier.offset {
                        IntOffset(
                            x = offsetX.value.roundToInt(),
                            y = 0
                        )
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = marqueeText,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = Color.Black,
                        style = textStyle,
                        modifier = Modifier.onSizeChanged { size -> textWidthPx = size.width }
                    )
                    repeat(repeatedCopies - 1) {
                        Spacer(modifier = Modifier.width(tickerGapPx.dp))
                        Text(
                            text = marqueeText,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            color = Color.Black,
                            style = textStyle
                        )
                    }
                }
            } else {
                Text(
                    text = marqueeText,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    color = Color.Black,
                    style = textStyle,
                    modifier = Modifier.onSizeChanged { size -> textWidthPx = size.width }
                )
            }
        }
    }
}

@Composable
private fun SignalDiagnosticsModal(uiState: RadioScreenUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .border(2.dp, Color(0xFFEAEAEA))
                .background(Color(0xFFC8C8C8))
                .padding(10.dp)
                .border(2.dp, Color(0xFF8E8E8E))
                .background(Color(0xFFC8C8C8))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Signal Diagnostics",
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Buffering station...", color = Color.Black)
            }
            uiState.playbackLogLines.take(3).forEach { line ->
                Text(
                    text = line,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black
                )
            }
        }
    }
}

private fun current24HourTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

private fun pickDiscoveryStations(
    stations: List<RadioStation>,
    activeStationId: String?,
    selectedGenre: String
): List<RadioStation> {
    if (stations.isEmpty()) {
        return emptyList()
    }

    val activeIndex = stations.indexOfFirst { it.id == activeStationId }
    val nextStationId = if (activeIndex >= 0 && stations.size > 1) {
        stations[(activeIndex + 1) % stations.size].id
    } else {
        null
    }

    val genreFilter = selectedGenre.lowercase(Locale.US)
    val pool = stations.filter { station ->
        station.id != activeStationId &&
            station.id != nextStationId &&
            (
                genreFilter == "all" ||
                    station.tags.lowercase(Locale.US).contains(genreFilter)
                )
    }

    if (pool.isEmpty()) {
        return emptyList()
    }

    val seed = (activeStationId ?: selectedGenre).hashCode()
    return pool.shuffled(Random(seed)).take(2)
}

private const val MARQUEE_SPEED_PX_PER_SECOND = 42f
