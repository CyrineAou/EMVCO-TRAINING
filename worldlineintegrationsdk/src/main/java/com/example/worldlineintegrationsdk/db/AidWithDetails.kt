package com.example.worldlineintegrationsdk.db

import androidx.room.Embedded
import androidx.room.Relation
import com.example.worldlineintegrationsdk.db.entity.*

data class AidWithDetails(
    @Embedded val aid: AidEntity,
    @Relation(parentColumn = "aid", entityColumn = "aid") val tac: TacEntity?,
    @Relation(parentColumn = "aid", entityColumn = "aid") val floorLimit: FloorLimitEntity?,
    @Relation(parentColumn = "rid", entityColumn = "rid") val capks: List<CapkEntity>
)