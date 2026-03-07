package com.neonhorizon.features.weather.model

data class LocalWeatherForecast(
    val today: WeatherDayForecast,
    val tomorrow: WeatherDayForecast
)

data class WeatherDayForecast(
    val dayLabel: String,
    val summary: String,
    val highTemperature: Int,
    val lowTemperature: Int,
    val precipitationChance: Int,
    val temperatureUnitSymbol: String
)
