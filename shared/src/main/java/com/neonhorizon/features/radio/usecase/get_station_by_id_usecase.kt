package com.neonhorizon.features.radio.usecase

import com.neonhorizon.features.radio.model.RadioStation
import com.neonhorizon.features.radio.repository.RadioBrowserRepository

class GetStationByIdUseCase(
    private val radioBrowserRepository: RadioBrowserRepository
) {
    suspend operator fun invoke(stationId: String): Result<RadioStation?> {
        return radioBrowserRepository.getStationById(stationId = stationId)
    }
}
