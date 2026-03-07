package com.neonhorizon.features.theme

import android.os.Bundle
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object ThemeSessionExtras {
    const val KEY_THEME_ID = "neon.theme.id"
    const val KEY_THEME_NAME = "neon.theme.name"
    const val KEY_THEME_BACKGROUND_RES = "neon.theme.background_res"
    const val KEY_THEME_NEON_PRIMARY = "neon.theme.primary"
    const val KEY_THEME_NEON_SECONDARY = "neon.theme.secondary"
    const val KEY_THEME_SCANLINE_OPACITY = "neon.theme.scanline_opacity"
}

class ThemeEngine(
    private val vaporThemeProvider: VaporThemeProvider
) {
    private var themeObserverJob: Job? = null

    fun bindToSession(mediaSession: MediaSession, scope: CoroutineScope) {
        themeObserverJob?.cancel()

        mediaSession.setSessionExtras(vaporThemeProvider.currentTheme.value.toSessionExtras())

        themeObserverJob = scope.launch {
            vaporThemeProvider.currentTheme.collectLatest { theme ->
                mediaSession.setSessionExtras(theme.toSessionExtras())
            }
        }
    }

    fun applyTheme(themeId: String): VaporTheme = vaporThemeProvider.setTheme(themeId)

    fun release() {
        themeObserverJob?.cancel()
        themeObserverJob = null
    }
}

fun VaporTheme.toSessionExtras(): Bundle = Bundle().apply {
    putString(ThemeSessionExtras.KEY_THEME_ID, id)
    putString(ThemeSessionExtras.KEY_THEME_NAME, name)
    putInt(ThemeSessionExtras.KEY_THEME_BACKGROUND_RES, backgroundRes)
    putInt(ThemeSessionExtras.KEY_THEME_NEON_PRIMARY, neonColorPrimary)
    putInt(ThemeSessionExtras.KEY_THEME_NEON_SECONDARY, neonColorSecondary)
    putFloat(ThemeSessionExtras.KEY_THEME_SCANLINE_OPACITY, scanlineOpacity)
}
