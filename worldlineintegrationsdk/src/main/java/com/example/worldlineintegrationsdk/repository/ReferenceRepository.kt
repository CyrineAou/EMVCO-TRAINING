package com.example.worldlineintegrationsdk.repository

import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.model.Tac

class ReferenceRepository(private val db: AppDatabase) {

    suspend fun isBlacklisted(panHash: String): Boolean =
        db.blacklistDao().contains(panHash)

    suspend fun floorLimitFor(aid: String): Long? =
        db.floorLimitDao().floorLimitFor(aid)

    suspend fun tacFor(aid: String): Tac? =
        db.tacDao().tacFor(aid)?.let { Tac(it.denial, it.online, it.default) }
}
