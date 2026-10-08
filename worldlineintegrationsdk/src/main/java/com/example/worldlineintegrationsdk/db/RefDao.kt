package com.example.worldlineintegrationsdk.db

import androidx.room.*
import com.example.worldlineintegrationsdk.db.entity.*

@Dao
interface RefDao {
    @Transaction @Query("SELECT * FROM aid WHERE aid = :aid")
    suspend fun aidDetails(aid: String): AidWithDetails?

    @Transaction @Query("SELECT * FROM aid")
    suspend fun allAidDetails(): List<AidWithDetails>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAids(l: List<AidEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapks(l: List<CapkEntity>)

    @Query("DELETE FROM aid") suspend fun clearAids()
    @Query("DELETE FROM capk") suspend fun clearCapks()
}