package com.example.worldlineintegrationsdk.db.entity


import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "aid", indices = [Index("rid")])
data class AidEntity(
    @PrimaryKey val aid: String,
    val rid: String,
    val label: String? = null
)