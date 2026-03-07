package com.neonhorizon.features.theme

import com.neonhorizon.shared.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface VaporThemeProvider {
    val currentTheme: StateFlow<VaporTheme>

    fun availableThemes(): List<VaporTheme>

    fun setTheme(themeId: String): VaporTheme
}

class NeonVaporThemeProvider : VaporThemeProvider {

    private val themeCatalog: List<VaporTheme> = listOf(
        VaporTheme(
            id = "sunset_horizon",
            name = "Sunset Horizon",
            backgroundRes = R.drawable.bg_themes_sunset_horizon_car,
            neonColorPrimary = 0xFFFF4FB3.toInt(),
            neonColorSecondary = 0xFF3EF6FF.toInt(),
            scanlineOpacity = 0.10f
        )
    )

    private val currentThemeFlow = MutableStateFlow(themeCatalog.first())

    override val currentTheme: StateFlow<VaporTheme> = currentThemeFlow.asStateFlow()

    override fun availableThemes(): List<VaporTheme> = themeCatalog

    override fun setTheme(themeId: String): VaporTheme {
        val selectedTheme = themeCatalog.firstOrNull { it.id == themeId } ?: themeCatalog.first()
        currentThemeFlow.value = selectedTheme
        return selectedTheme
    }
}
