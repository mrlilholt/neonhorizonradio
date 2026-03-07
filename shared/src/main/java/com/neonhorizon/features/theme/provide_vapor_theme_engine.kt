package com.neonhorizon.features.theme

object VaporThemeGraph {
    val vaporThemeProvider: VaporThemeProvider by lazy { NeonVaporThemeProvider() }
    val themeEngine: ThemeEngine by lazy { ThemeEngine(vaporThemeProvider) }
}
