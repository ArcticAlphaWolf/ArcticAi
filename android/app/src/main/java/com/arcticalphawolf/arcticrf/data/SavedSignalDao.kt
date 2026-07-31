package com.arcticalphawolf.arcticrf.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSignalDao {
    @Query("SELECT * FROM saved_signals ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SavedSignal>>

    @Insert
    suspend fun insert(signal: SavedSignal): Long

    @Delete
    suspend fun delete(signal: SavedSignal)
}
