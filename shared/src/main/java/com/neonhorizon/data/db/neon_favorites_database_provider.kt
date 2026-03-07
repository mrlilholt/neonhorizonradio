package com.neonhorizon.data.db

import android.content.Context
import androidx.room.Room

object NeonFavoritesDatabaseProvider {
    @Volatile
    private var cachedDatabase: NeonFavoritesDatabase? = null

    fun getDatabase(context: Context): NeonFavoritesDatabase {
        val existingDatabase = cachedDatabase
        if (existingDatabase != null) {
            return existingDatabase
        }

        return synchronized(this) {
            val cached = cachedDatabase
            if (cached != null) {
                cached
            } else {
                Room.databaseBuilder(
                    context.applicationContext,
                    NeonFavoritesDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { builtDatabase ->
                        cachedDatabase = builtDatabase
                    }
            }
        }
    }

    private const val DATABASE_NAME = "neon_horizon_favorites.db"
}
