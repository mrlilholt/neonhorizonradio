package com.neonhorizon.features.weather.repository

import com.neonhorizon.features.weather.api.OpenMeteoApi
import com.neonhorizon.features.weather.model.LocalWeatherForecast
import com.neonhorizon.features.weather.model.WeatherDayForecast
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

interface LocalWeatherRepository {
    suspend fun getLocalForecast(
        latitude: Double,
        longitude: Double
    ): Result<LocalWeatherForecast>
}

class LocalWeatherRepositoryImpl(
    private val openMeteoApi: OpenMeteoApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val locale: Locale = Locale.getDefault()
) : LocalWeatherRepository {
    override suspend fun getLocalForecast(
        latitude: Double,
        longitude: Double
    ): Result<LocalWeatherForecast> = withContext(ioDispatcher) {
        runCatching {
            val temperatureUnit = if (locale.country.equals("US", ignoreCase = true)) {
                "fahrenheit"
            } else {
                "celsius"
            }

            val forecast = openMeteoApi.getDailyForecast(
                latitude = latitude,
                longitude = longitude,
                temperatureUnit = temperatureUnit
            )

            require(forecast.daily.time.size >= 2) { "Weather forecast is incomplete." }
            require(forecast.daily.weather_code.size >= 2) { "Weather forecast is incomplete." }
            require(forecast.daily.temperature_2m_max.size >= 2) { "Weather forecast is incomplete." }
            require(forecast.daily.temperature_2m_min.size >= 2) { "Weather forecast is incomplete." }
            require(forecast.daily.precipitation_probability_max.size >= 2) { "Weather forecast is incomplete." }

            val temperatureUnitSymbol = forecast.daily_units?.temperature_2m_max
                ?.takeIf { unit -> unit.isNotBlank() }
                ?: if (temperatureUnit == "fahrenheit") "°F" else "°C"

            LocalWeatherForecast(
                today = buildWeatherDayForecast(
                    dayLabel = "Today",
                    dayIndex = 0,
                    forecast = forecast,
                    temperatureUnitSymbol = temperatureUnitSymbol
                ),
                tomorrow = buildWeatherDayForecast(
                    dayLabel = "Tomorrow",
                    dayIndex = 1,
                    forecast = forecast,
                    temperatureUnitSymbol = temperatureUnitSymbol
                )
            )
        }
    }

    private fun buildWeatherDayForecast(
        dayLabel: String,
        dayIndex: Int,
        forecast: com.neonhorizon.features.weather.api.OpenMeteoForecastDto,
        temperatureUnitSymbol: String
    ): WeatherDayForecast {
        return WeatherDayForecast(
            dayLabel = dayLabel,
            summary = mapWeatherCodeToSummary(forecast.daily.weather_code[dayIndex]),
            highTemperature = forecast.daily.temperature_2m_max[dayIndex].roundToInt(),
            lowTemperature = forecast.daily.temperature_2m_min[dayIndex].roundToInt(),
            precipitationChance = forecast.daily.precipitation_probability_max[dayIndex],
            temperatureUnitSymbol = temperatureUnitSymbol
        )
    }
}

private fun mapWeatherCodeToSummary(weatherCode: Int): String {
    return when (weatherCode) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing Drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow Showers"
        95 -> "Thunderstorm"
        96, 99 -> "Storm / Hail"
        else -> "Forecast"
    }
}
