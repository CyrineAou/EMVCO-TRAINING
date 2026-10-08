package com.example.worldlineintegrationsdk.handler

import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.db.entity.BlacklistEntity
import com.example.worldlineintegrationsdk.model.TableNames


class BlacklistHandler(
    db: AppDatabase
) : TypedTableHandler<BlacklistEntity>(
    TableNames.BLACKLIST,
    db
) {

    override fun parse(
        row: Map<String, String>
    ): BlacklistEntity? {

        val panHash = row["pan_hash"]
            ?: return null

        return BlacklistEntity(panHash)
    }

    override suspend fun replace(
        items: List<BlacklistEntity>
    ) {

        db.blacklistDao().clear()

        db.blacklistDao().insertAll(items)
    }
}