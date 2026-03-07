package com.neonhorizon.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteStationDao {
    @Query("SELECT * FROM favorite_stations ORDER BY savedAtEpochMillis DESC")
    fun observeFavorites(): Flow<List<FavoriteStationEntity>>

    @Query("SELECT * FROM favorite_stations ORDER BY savedAtEpochMillis DESC")
    suspend fun getFavoritesOnce(): List<FavoriteStationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(station: FavoriteStationEntity)

    @Delete
    suspend fun deleteFavorite(station: FavoriteStationEntity)
}
