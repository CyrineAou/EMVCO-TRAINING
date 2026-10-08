package com.example.worldlineintegrationsdk.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.worldlineintegrationsdk.db.entity.FloorLimitEntity

@Dao
interface FloorLimitDao {
    @Query("SELECT floorLimit FROM floor_limits WHERE aid = :aid")
    suspend fun floorLimitFor(aid: String): Long?

    @Query("DELETE FROM floor_limits")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FloorLimitEntity>)
}
