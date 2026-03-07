package com.neonhorizon.features.radio.usecase

import com.neonhorizon.features.radio.model.RadioStation
import com.neonhorizon.features.radio.repository.RadioBrowserRepository

class GetLofiStationListUseCase(
    private val radioBrowserRepository: RadioBrowserRepository
) {
    suspend operator fun invoke(limit: Int = 40): Result<List<RadioStation>> {
        return radioBrowserRepository.getStationsByGenre(
            genre = "lofi",
            limit = limit
        )
    }
}
