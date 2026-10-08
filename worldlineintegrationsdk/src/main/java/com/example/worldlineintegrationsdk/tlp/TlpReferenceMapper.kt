package com.example.worldlineintegrationsdk.tlp

import androidx.room.withTransaction
import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.db.entity.BlacklistEntity
import com.example.worldlineintegrationsdk.db.entity.CardAcceptanceEntity
import com.example.worldlineintegrationsdk.db.entity.FloorLimitEntity

object TlpReferenceMapper {

    /** Remplit floor_limits depuis table27 (hypothèse : tag 504 = AID, tag 10000 = montant en centimes). */
    suspend fun fillFloorLimits(db: AppDatabase) {
        val items = db.tlpDao().getFields("27")
            .groupBy { it.recordIndex }
            .values
            .mapNotNull { rec ->
                val aid = rec.firstOrNull { it.path == "504" }?.value
                    ?: return@mapNotNull null
                val limit = rec.firstOrNull { it.path == "10000" }?.value?.toLongOrNull()
                    ?: return@mapNotNull null
                FloorLimitEntity(aid, limit)
            }
            .distinctBy { it.aid }

        db.floorLimitDao().clear()
        db.floorLimitDao().insertAll(items)
    }

    /** Remplit blacklist depuis table13. hashPan doit être le MÊME hash que celui utilisé à la recherche. */
    suspend fun fillBlacklist(db: AppDatabase, hashPan: (String) -> String) {
        val pans = db.tlpDao().getFields("13")
            .filter { it.path == "20008" }
            .map { it.value }

        db.blacklistDao().clear()
        pans.chunked(5_000).forEach { chunk ->
            db.blacklistDao().insertAll(chunk.map { BlacklistEntity(hashPan(it)) })
        }
    }

    suspend fun fillCardAcceptance(db: AppDatabase, tableNumber: String = "13") {
        val dao = db.cardAcceptanceDao()
        val rows = dao.readFromTlp(tableNumber).map {
            CardAcceptanceEntity(
                id = it.id,
                pan = it.pan.trim(),
                acceptanceLevel = it.acceptanceLevel.trim()
            )
        }
        db.withTransaction {

            rows.chunked(5_000).forEach { dao.insertAll(it) }
        }
    }
}