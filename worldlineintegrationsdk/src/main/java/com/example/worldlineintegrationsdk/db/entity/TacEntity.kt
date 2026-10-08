package com.example.worldlineintegrationsdk.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tac")
data class TacEntity(
    @PrimaryKey val aid: String,
    val denial: String,
    val online: String,
    val default: String
)
