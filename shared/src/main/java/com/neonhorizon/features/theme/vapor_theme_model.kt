package com.neonhorizon.features.theme

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

data class VaporTheme(
    val id: String,
    val name: String,
    @DrawableRes val backgroundRes: Int,
    @ColorInt val neonColorPrimary: Int,
    @ColorInt val neonColorSecondary: Int,
    val scanlineOpacity: Float
)
