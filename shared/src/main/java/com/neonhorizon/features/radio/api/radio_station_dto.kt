package com.neonhorizon.features.radio.api

import com.neonhorizon.features.radio.model.RadioStation
import com.squareup.moshi.Json

data class RadioStationDto(
    @Json(name = "stationuuid") val stationId: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "url_resolved") val streamUrl: String?,
    @Json(name = "homepage") val homepage: String?,
    @Json(name = "favicon") val favicon: String?,
    @Json(name = "tags") val tags: String?,
    @Json(name = "country") val country: String?
)

fun RadioStationDto.toModelOrNull(): RadioStation? {
    val safeId = stationId?.trim().orEmpty()
    val safeName = name?.trim().orEmpty()
    val safeStreamUrl = streamUrl?.trim().orEmpty()

    if (safeId.isBlank() || safeName.isBlank() || safeStreamUrl.isBlank()) {
        return null
    }

    return RadioStation(
        id = safeId,
        name = safeName,
        streamUrl = safeStreamUrl,
        homepage = homepage.orEmpty(),
        favicon = favicon.orEmpty(),
        tags = tags.orEmpty(),
        country = country.orEmpty()
    )
}
