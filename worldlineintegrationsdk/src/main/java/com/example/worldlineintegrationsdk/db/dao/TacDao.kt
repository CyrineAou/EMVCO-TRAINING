package com.example.worldlineintegrationsdk.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.worldlineintegrationsdk.db.entity.TacEntity

@Dao
interface TacDao {
    @Query("SELECT * FROM tac WHERE aid = :aid")
    suspend fun tacFor(aid: String): TacEntity?

    @Query("DELETE FROM tac")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TacEntity>)
}
