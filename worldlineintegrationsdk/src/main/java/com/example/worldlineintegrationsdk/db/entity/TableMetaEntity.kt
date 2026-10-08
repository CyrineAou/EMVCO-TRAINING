package com.example.worldlineintegrationsdk.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reference_tables_meta")
data class TableMetaEntity(
    @PrimaryKey val tableName: String,
    val version: String?,
    val updatedAt: Long,
    val rowCount: Int
)
