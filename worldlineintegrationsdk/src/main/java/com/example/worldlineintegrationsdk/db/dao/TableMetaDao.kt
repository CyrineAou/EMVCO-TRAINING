package com.example.worldlineintegrationsdk.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.worldlineintegrationsdk.db.entity.TableMetaEntity

@Dao
interface TableMetaDao {
    @Query("SELECT * FROM reference_tables_meta WHERE tableName = :table")
    suspend fun get(table: String): TableMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: TableMetaEntity)
}
