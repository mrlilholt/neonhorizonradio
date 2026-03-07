package com.neonhorizon.features.radio.repository

import com.neonhorizon.features.radio.api.RadioBrowserApi
import com.neonhorizon.features.radio.api.toModelOrNull
import com.neonhorizon.features.radio.model.RadioStation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

interface RadioBrowserRepository {
    suspend fun getStationById(stationId: String): Result<RadioStation?>
    suspend fun getStationsByGenre(genre: String, limit: Int = 40): Result<List<RadioStation>>
    suspend fun searchStations(
        query: String,
        genre: String,
        limit: Int = 80
    ): Result<List<RadioStation>>
}

class RadioBrowserRepositoryImpl(
    private val radioBrowserApi: RadioBrowserApi,
    private val fallbackRadioBrowserApis: List<RadioBrowserApi> = emptyList(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RadioBrowserRepository {
    private val radioBrowserApis: List<RadioBrowserApi> =
        (listOf(radioBrowserApi) + fallbackRadioBrowserApis).distinct()

    override suspend fun getStationById(stationId: String): Result<RadioStation?> =
        withContext(ioDispatcher) {
            runCatching {
                executeWithApiFallback { api ->
                    api.getStationsByUuid(uuid = stationId)
                        .mapNotNull { stationDto -> stationDto.toModelOrNull() }
                        .firstOrNull()
                }
            }
        }

    override suspend fun getStationsByGenre(genre: String, limit: Int): Result<List<RadioStation>> =
        withContext(ioDispatcher) {
            runCatching {
                executeWithApiFallback { api ->
                    api.getStationsByTag(
                        tag = genre,
                        limit = limit
                    ).mapNotNull { stationDto ->
                        stationDto.toModelOrNull()
                    }
                }
            }
        }

    override suspend fun searchStations(
        query: String,
        genre: String,
        limit: Int
    ): Result<List<RadioStation>> = withContext(ioDispatcher) {
        runCatching {
            executeWithApiFallback { api ->
                val normalizedQuery = query.trim()
                val normalizedGenre = genre.trim()

                if (normalizedQuery.isBlank()) {
                    val poolLimit = if (normalizedGenre.isNotBlank()) {
                        maxOf(limit * 4, 200)
                    } else {
                        maxOf(limit * 2, 120)
                    }

                val sourceDtos = if (normalizedGenre.isNotBlank()) {
                    val genreToken = normalizedGenre.lowercase(Locale.US)
                    (
                        api.getStationsByTag(
                            tag = genreToken,
                                limit = poolLimit
                            ) +
                                api.searchStations(
                                    tag = genreToken,
                                    limit = poolLimit
                                )
                        )
                    } else {
                        api.searchStations(
                            limit = poolLimit,
                            order = "random",
                            reverse = false
                        )
                    }

                    sourceDtos
                        .mapNotNull { stationDto -> stationDto.toModelOrNull() }
                        .distinctBy { station -> station.id }
                        .filter { station ->
                            if (normalizedGenre.isBlank()) {
                                true
                            } else {
                                val genreToken = normalizedGenre.lowercase(Locale.US)
                                station.tags.contains(genreToken, ignoreCase = true) ||
                                    station.name.contains(genreToken, ignoreCase = true)
                            }
                        }
                        .shuffled()
                        .take(limit)
                } else {
                    val searchTerms = buildSearchTerms(normalizedQuery)
                    val bucketLimit = maxOf(limit, 60)
                    val dtoBuckets = mutableListOf<List<com.neonhorizon.features.radio.api.RadioStationDto>>()
                    if (normalizedGenre.isNotBlank()) {
                        dtoBuckets += api.getStationsByTag(
                            tag = normalizedGenre,
                            limit = bucketLimit
                        )
                    }

                    searchTerms.forEach { term ->
                        dtoBuckets += api.searchStations(tag = term, limit = bucketLimit)
                        dtoBuckets += api.getStationsByTag(tag = term, limit = bucketLimit)
                        dtoBuckets += api.searchStations(name = term, limit = bucketLimit)
                        dtoBuckets += api.searchStations(country = term, limit = bucketLimit)
                    }

                    dtoBuckets
                        .flatten()
                        .mapNotNull { stationDto -> stationDto.toModelOrNull() }
                        .filter { station ->
                            stationMatchesSearch(
                                station = station,
                                normalizedQuery = normalizedQuery,
                                normalizedGenre = normalizedGenre
                            )
                        }
                        .distinctBy { station -> station.id }
                        .sortedWith(
                            compareByDescending<RadioStation> { station ->
                                searchRelevanceScore(
                                    station = station,
                                    normalizedQuery = normalizedQuery
                                )
                            }.thenBy { station -> station.name.lowercase(Locale.US) }
                        )
                        .take(limit)
                }
            }
        }
    }

    private suspend fun <T> executeWithApiFallback(
        block: suspend (RadioBrowserApi) -> T
    ): T {
        var lastError: Throwable? = null
        radioBrowserApis.forEach { api ->
            try {
                return block(api)
            } catch (throwable: Throwable) {
                lastError = throwable
            }
        }
        throw (lastError ?: IllegalStateException("No Radio Browser API clients configured."))
    }
}

private fun buildSearchTerms(query: String): List<String> {
    val normalizedQuery = query.trim().lowercase(Locale.US)
    val tokens = normalizedQuery
        .split(Regex("[^a-z0-9]+"))
        .map { token -> token.trim() }
        .filter { token -> token.length >= 2 }

    return listOf(normalizedQuery) + tokens
        .distinct()
        .take(3)
}

private fun stationMatchesSearch(
    station: RadioStation,
    normalizedQuery: String,
    normalizedGenre: String
): Boolean {
    val name = station.name.lowercase(Locale.US)
    val tags = station.tags.lowercase(Locale.US)
    val country = station.country.lowercase(Locale.US)
    val tokens = buildSearchTerms(normalizedQuery)

    val matchesGenre = normalizedGenre.isBlank() ||
        tags.contains(normalizedGenre.lowercase(Locale.US)) ||
        name.contains(normalizedGenre.lowercase(Locale.US))

    if (!matchesGenre) {
        return false
    }

    if (name.contains(normalizedQuery) || tags.contains(normalizedQuery) || country.contains(normalizedQuery)) {
        return true
    }

    return tokens.any { token ->
        name.contains(token) || tags.contains(token) || country.contains(token)
    }
}

private fun searchRelevanceScore(
    station: RadioStation,
    normalizedQuery: String
): Int {
    val name = station.name.lowercase(Locale.US)
    val tags = station.tags.lowercase(Locale.US)
    val country = station.country.lowercase(Locale.US)
    val tokens = buildSearchTerms(normalizedQuery)

    var score = 0
    if (tags.contains(normalizedQuery)) score += 300
    if (country.contains(normalizedQuery)) score += 240
    if (name.contains(normalizedQuery)) score += 220

    tokens.forEach { token ->
        if (tags.contains(token)) score += 60
        if (country.contains(token)) score += 45
        if (name.contains(token)) score += 40
    }

    return score
}
