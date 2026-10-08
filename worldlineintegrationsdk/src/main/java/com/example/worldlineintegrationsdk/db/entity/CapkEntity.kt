package com.example.worldlineintegrationsdk.db.entity

import androidx.room.Entity

@Entity(tableName = "capk", primaryKeys = ["rid", "capkIndex"])
data class CapkEntity(
    val rid: String,
    val capkIndex: String,
    val modulus: String,
    val exponent: String,
    val hash: String? = null,
    val expiry: String? = null
)
