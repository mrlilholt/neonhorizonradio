package com.neonhorizon.features.radio.model

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homepage: String,
    val favicon: String,
    val tags: String,
    val country: String
)

fun RadioStation.toMediaItem(): MediaItem {
    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(name)
        .setDisplayTitle(name)
        .setArtist("Neon Horizon Radio")
        .setAlbumTitle("$tags • $country")
        .setGenre(tags)
        .setIsPlayable(true)
        .setIsBrowsable(false)

    if (favicon.startsWith("http://") || favicon.startsWith("https://")) {
        metadataBuilder.setArtworkUri(Uri.parse(favicon))
    }

    val metadata = metadataBuilder.build()

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUrl)
        .setMediaMetadata(metadata)
        .build()
}
