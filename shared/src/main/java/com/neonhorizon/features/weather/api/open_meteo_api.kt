package com.neonhorizon.features.weather.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getDailyForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
        @Query("forecast_days") forecastDays: Int = 2,
        @Query("temperature_unit") temperatureUnit: String = "fahrenheit",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoForecastDto
}

data class OpenMeteoForecastDto(
    val daily: OpenMeteoDailyDto,
    val daily_units: OpenMeteoDailyUnitsDto? = null
)

data class OpenMeteoDailyDto(
    val time: List<String> = emptyList(),
    val weather_code: List<Int> = emptyList(),
    val temperature_2m_max: List<Double> = emptyList(),
    val temperature_2m_min: List<Double> = emptyList(),
    val precipitation_probability_max: List<Int> = emptyList()
)

data class OpenMeteoDailyUnitsDto(
    val temperature_2m_max: String? = null,
    val temperature_2m_min: String? = null
)

object OpenMeteoApiFactory {
    private const val BASE_URL = "https://api.open-meteo.com/"

    fun create(): OpenMeteoApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(OpenMeteoApi::class.java)
    }
}
