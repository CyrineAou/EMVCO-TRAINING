package com.example.worldlineintegrationsdk.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blacklist")
data class BlacklistEntity(
    @PrimaryKey val panHash: String
)
