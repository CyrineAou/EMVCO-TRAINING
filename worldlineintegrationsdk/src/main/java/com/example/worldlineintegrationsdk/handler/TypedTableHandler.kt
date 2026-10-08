package com.example.worldlineintegrationsdk.handler

import androidx.room.withTransaction
import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.db.entity.TableMetaEntity
import com.example.worldlineintegrationsdk.model.SdkTable

/**
 * Squelette commun : contrôle de version + transaction (clear + insert + meta).
 * Chaque table n'implémente que parse() et replace().
 */
abstract class TypedTableHandler<E>(
    override val tableName: String,
    open val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) : TableHandler {

    protected abstract fun parse(row: Map<String, String>): E?
    protected abstract suspend fun replace(items: List<E>)

    override suspend fun handle(table: SdkTable) {
        val metaDao = db.tableMetaDao()
        val current = metaDao.get(tableName)
        if (table.version != null && current?.version == table.version) return

        val items = table.rows.mapNotNull(::parse)

        db.withTransaction {
            replace(items)
            metaDao.upsert(TableMetaEntity(tableName, table.version, clock(), items.size))
        }
    }
}
