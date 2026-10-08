package com.example.worldlineintegrationsdk.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_acceptance")
data class CardAcceptanceEntity(
    @PrimaryKey val id: Int,
    val pan: String,
    val acceptanceLevel: String
)