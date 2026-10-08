package com.example.worldlineintegrationsdk.handler

import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.db.entity.TacEntity
import com.example.worldlineintegrationsdk.model.TableNames

class TacHandler(override val db: AppDatabase) :
    TypedTableHandler<TacEntity>(TableNames.TAC, db) {

    override fun parse(row: Map<String, String>): TacEntity? {
        val aid = row["aid"] ?: return null
        return TacEntity(
            aid = aid,
            denial = row["denial"].orEmpty(),
            online = row["online"].orEmpty(),
            default = row["default"].orEmpty()
        )
    }

    override suspend fun replace(items: List<TacEntity>) {
        db.tacDao().clear()
        db.tacDao().insertAll(items)
    }
}
