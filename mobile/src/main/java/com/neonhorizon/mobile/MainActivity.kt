package com.neonhorizon.mobile

import android.Manifest
import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.neonhorizon.features.radio.model.RadioStation
import com.neonhorizon.features.theme.VaporTheme
import com.neonhorizon.features.theme.VaporThemeGraph
import com.neonhorizon.mobile.features.radio.RadioScreen
import com.neonhorizon.mobile.features.radio.RadioScreenViewModel
import com.neonhorizon.mobile.features.radio.RadioScreenViewModelFactory

class MainActivity : ComponentActivity() {
    private val vaporThemeProvider = VaporThemeGraph.vaporThemeProvider
    private val themeEngine = VaporThemeGraph.themeEngine
    private val radioScreenViewModel: RadioScreenViewModel by viewModels {
        RadioScreenViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val radioUiState by radioScreenViewModel.uiState.collectAsState()
            val activeTheme by vaporThemeProvider.currentTheme.collectAsState()
            val composeContext = LocalContext.current

            val googleSignInClient = remember(composeContext) {
                buildGoogleSignInClient(composeContext)
            }
            val googleSignInLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { activityResult ->
                if (activityResult.resultCode != Activity.RESULT_OK) {
                    radioScreenViewModel.onGoogleSignInFailed("Google sign-in cancelled.")
                    return@rememberLauncherForActivityResult
                }

                runCatching {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
                    task.getResult(ApiException::class.java)
                }
                    .onSuccess { account ->
                        val idToken = account.idToken
                        if (idToken.isNullOrBlank()) {
                            radioScreenViewModel.onGoogleSignInFailed(
                                "Google ID token missing. Check Firebase Web client ID."
                            )
                        } else {
                            radioScreenViewModel.authenticateWithGoogleIdToken(idToken)
                        }
                    }
                    .onFailure { throwable ->
                        val errorMessage = when (throwable) {
                            is ApiException -> {
                                "Google sign-in failed (${throwable.statusCode})."
                            }
                            else -> {
                                throwable.message ?: "Google sign-in failed."
                            }
                        }
                        radioScreenViewModel.onGoogleSignInFailed(errorMessage)
                    }
            }
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    requestApproximateLocation(
                        context = composeContext,
                        onLocationResolved = radioScreenViewModel::loadLocalWeather,
                        onFailure = radioScreenViewModel::onWeatherUnavailable
                    )
                } else {
                    radioScreenViewModel.onWeatherUnavailable(
                        "Location access declined. Local forecast unavailable."
                    )
                }
            }

            LaunchedEffect(
                composeContext,
                radioUiState.isWeatherEnabled,
                radioUiState.localWeatherForecast,
                radioUiState.isWeatherLoading,
                radioUiState.weatherStatusMessage
            ) {
                if (!radioUiState.isWeatherEnabled) {
                    return@LaunchedEffect
                }

                if (radioUiState.localWeatherForecast != null ||
                    radioUiState.isWeatherLoading ||
                    !radioUiState.weatherStatusMessage.isNullOrBlank()
                ) {
                    return@LaunchedEffect
                }

                if (ContextCompat.checkSelfPermission(
                        composeContext,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    requestApproximateLocation(
                        context = composeContext,
                        onLocationResolved = radioScreenViewModel::loadLocalWeather,
                        onFailure = radioScreenViewModel::onWeatherUnavailable
                    )
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }

            NeonHorizonTheme(vaporTheme = activeTheme) {
                RadioScreen(
                    vaporTheme = activeTheme,
                    onApplySunsetHorizon = {
                        themeEngine.applyTheme(SUNSET_HORIZON_THEME_ID)
                    },
                    uiState = radioUiState,
                    onRetryLoad = radioScreenViewModel::loadLofiStations,
                    onSelectStation = radioScreenViewModel::playSelectedStation,
                    onToggleFavorite = radioScreenViewModel::toggleFavorite,
                    onSelectTab = radioScreenViewModel::selectTab,
                    onTogglePlayback = radioScreenViewModel::togglePlayback,
                    onPlayNext = radioScreenViewModel::playNextStation,
                    onPlayPrevious = radioScreenViewModel::playPreviousStation,
                    onStopPlayback = radioScreenViewModel::stopPlayback,
                    onSetPlayerVolume = radioScreenViewModel::setPlayerVolume,
                    onSearchQueryChange = radioScreenViewModel::updateSearchQuery,
                    onSubmitSearch = radioScreenViewModel::submitStationSearch,
                    onSelectGenre = radioScreenViewModel::selectGenre,
                    onShareStation = { station ->
                        shareStation(
                            context = composeContext,
                            station = station
                        )
                    },
                    onSetWeatherEnabled = radioScreenViewModel::setWeatherEnabled,
                    onRequestGoogleSignIn = {
                        val signInClient = googleSignInClient
                        if (signInClient == null) {
                            radioScreenViewModel.onGoogleSignInFailed(
                                "Google sign-in is not configured. Add Web client ID in Firebase."
                            )
                            return@RadioScreen
                        }
                        radioScreenViewModel.markGoogleSignInStarted()
                        googleSignInLauncher.launch(signInClient.signInIntent)
                    },
                    onGoogleSignOut = {
                        googleSignInClient
                            ?.signOut()
                            ?.addOnCompleteListener {
                                radioScreenViewModel.signOutFromGoogle()
                            }
                            ?: radioScreenViewModel.signOutFromGoogle()
                    }
                )
            }
        }

        handleDeepLinkIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    companion object {
        private const val SUNSET_HORIZON_THEME_ID = "sunset_horizon"
        private const val STATION_LINK_SCHEME = "neonhorizon"
        private const val STATION_LINK_HOST = "station"
        private const val STATION_LINK_WEB_HOST = "neonhorizonradio.netlify.app"
        private const val STATION_LINK_WEB_BASE = "https://$STATION_LINK_WEB_HOST/station"
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val stationId = parseStationIdFromUri(intent?.data) ?: return
        radioScreenViewModel.handleSharedStationLink(stationId)
    }

    private fun parseStationIdFromUri(uri: Uri?): String? {
        if (uri == null) {
            return null
        }

        if (uri.scheme.equals(STATION_LINK_SCHEME, ignoreCase = true) &&
            uri.host.equals(STATION_LINK_HOST, ignoreCase = true)
        ) {
            return uri.pathSegments.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        }

        if (uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(STATION_LINK_WEB_HOST, ignoreCase = true)
        ) {
            val pathSegments = uri.pathSegments
            if (pathSegments.firstOrNull().equals("station", ignoreCase = true)) {
                return pathSegments.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            }
        }

        return null
    }

    private fun shareStation(
        context: Context,
        station: RadioStation
    ) {
        val stationUrl = "$STATION_LINK_WEB_BASE/${station.id}"
        val stationLocation = station.country.ifBlank { "Unknown location" }
        val shareText = buildString {
            append("Tune into ${station.name} on Neon Horizon Radio")
            append(" • $stationLocation")
            append("\n\nOpen station:")
            append("\n")
            append(stationUrl)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Neon Horizon Radio: ${station.name}")
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_HTML_TEXT, "<p>${shareText.replace("\n", "<br/>")}</p>")
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "Share station")
        )
    }
}

private fun buildGoogleSignInClient(context: Context): GoogleSignInClient? {
    val webClientIdResId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )
    if (webClientIdResId == 0) {
        return null
    }

    val webClientId = context.getString(webClientIdResId)
    if (webClientId.isBlank()) {
        return null
    }

    val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestIdToken(webClientId)
        .build()

    return GoogleSignIn.getClient(context, signInOptions)
}

@SuppressLint("MissingPermission")
private fun requestApproximateLocation(
    context: Context,
    onLocationResolved: (Double, Double) -> Unit,
    onFailure: (String) -> Unit
) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: run {
            onFailure("Location service unavailable.")
            return
        }

    val providers = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
        LocationManager.GPS_PROVIDER
    )

    val lastKnownLocation = providers
        .asSequence()
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { location -> location.time }

    if (lastKnownLocation != null) {
        onLocationResolved(lastKnownLocation.latitude, lastKnownLocation.longitude)
        return
    }

    val currentProvider = when {
        runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
            .getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
        runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false) -> LocationManager.GPS_PROVIDER
        else -> null
    }

    if (currentProvider == null) {
        onFailure("Enable location services to load local forecast.")
        return
    }

    LocationManagerCompat.getCurrentLocation(
        locationManager,
        currentProvider,
        CancellationSignal(),
        ContextCompat.getMainExecutor(context)
    ) { location ->
        if (location != null) {
            onLocationResolved(location.latitude, location.longitude)
        } else {
            onFailure("Current location unavailable. Local forecast hidden.")
        }
    }
}

@Composable
private fun NeonHorizonTheme(
    vaporTheme: VaporTheme,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = Color(vaporTheme.neonColorPrimary),
        secondary = Color(vaporTheme.neonColorSecondary),
        background = Color(0xFF090313),
        surface = Color(0xFF140A22),
        onPrimary = Color(0xFF12061A),
        onSecondary = Color(0xFF03161A),
        onBackground = Color(0xFFF4F5FF),
        onSurface = Color(0xFFF4F5FF)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
