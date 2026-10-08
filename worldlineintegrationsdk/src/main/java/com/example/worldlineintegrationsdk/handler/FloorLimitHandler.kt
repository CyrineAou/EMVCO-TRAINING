package com.example.worldlineintegrationsdk.handler

import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.db.entity.FloorLimitEntity
import com.example.worldlineintegrationsdk.model.TableNames


class FloorLimitHandler(
    db: AppDatabase
) : TypedTableHandler<FloorLimitEntity>(
    TableNames.FLOOR_LIMITS,
    db
) {

    override fun parse(
        row: Map<String, String>
    ): FloorLimitEntity? {

        val aid = row["aid"]
            ?: return null

        val limit = row["floor_limit"]
            ?.toLongOrNull()
            ?: return null

        return FloorLimitEntity(
            aid,
            limit
        )
    }

    override suspend fun replace(
        items: List<FloorLimitEntity>
    ) {

        db.floorLimitDao().clear()

        db.floorLimitDao().insertAll(items)
    }
}
