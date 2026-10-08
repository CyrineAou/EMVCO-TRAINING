package com.example.worldlineintegrationsdk.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "floor_limits")
data class FloorLimitEntity(
    @PrimaryKey val aid: String,
    val floorLimit: Long
)
