package com.example.worldlineintegrationsdk.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.worldlineintegrationsdk.db.entity.BlacklistEntity

@Dao
interface BlacklistDao {
    @Query("SELECT EXISTS(SELECT 1 FROM blacklist WHERE panHash = :panHash)")
    suspend fun contains(panHash: String): Boolean

    @Query("DELETE FROM blacklist")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BlacklistEntity>)
}
