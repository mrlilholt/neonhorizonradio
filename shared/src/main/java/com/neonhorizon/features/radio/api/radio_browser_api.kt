package com.neonhorizon.features.radio.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface RadioBrowserApi {
    @GET("json/stations/byuuid/{uuid}")
    suspend fun getStationsByUuid(
        @Path("uuid") uuid: String,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<RadioStationDto>

    @GET("json/stations/bytag/{tag}")
    suspend fun getStationsByTag(
        @Path("tag") tag: String,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("limit") limit: Int = 40,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioStationDto>

    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String? = null,
        @Query("tag") tag: String? = null,
        @Query("country") country: String? = null,
        @Query("language") language: String? = null,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("limit") limit: Int = 80,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioStationDto>
}

object RadioBrowserApiFactory {
    private const val BASE_URL = "https://de1.api.radio-browser.info/"
    private val FALLBACK_BASE_URLS = listOf(
        "https://all.api.radio-browser.info/",
        "https://nl1.api.radio-browser.info/",
        "https://at1.api.radio-browser.info/",
        "https://fr1.api.radio-browser.info/"
    )

    fun create(baseUrl: String = BASE_URL): RadioBrowserApi {
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
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(RadioBrowserApi::class.java)
    }

    fun createFallbackApis(): List<RadioBrowserApi> {
        return FALLBACK_BASE_URLS.map { baseUrl -> create(baseUrl) }
    }
}
