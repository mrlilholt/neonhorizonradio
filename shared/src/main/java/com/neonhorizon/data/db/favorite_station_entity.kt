package com.neonhorizon.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_stations")
data class FavoriteStationEntity(
    @PrimaryKey val stationId: String,
    val name: String,
    val streamUrl: String,
    val favicon: String,
    val tags: String,
    val country: String,
    val savedAtEpochMillis: Long
)
