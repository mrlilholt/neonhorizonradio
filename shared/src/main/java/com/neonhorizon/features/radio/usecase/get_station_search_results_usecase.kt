package com.neonhorizon.features.radio.usecase

import com.neonhorizon.features.radio.model.RadioStation
import com.neonhorizon.features.radio.repository.RadioBrowserRepository

class GetStationSearchResultsUseCase(
    private val radioBrowserRepository: RadioBrowserRepository
) {
    suspend operator fun invoke(
        query: String,
        genre: String,
        limit: Int = 80
    ): Result<List<RadioStation>> {
        return radioBrowserRepository.searchStations(
            query = query,
            genre = genre,
            limit = limit
        )
    }
}
