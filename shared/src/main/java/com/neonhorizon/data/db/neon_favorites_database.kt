package com.neonhorizon.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FavoriteStationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class NeonFavoritesDatabase : RoomDatabase() {
    abstract fun favoriteStationDao(): FavoriteStationDao
}
