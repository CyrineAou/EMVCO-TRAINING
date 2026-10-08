package com.example.worldlineintegrationsdk.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.worldlineintegrationsdk.db.entity.TlpFieldEntity
import com.example.worldlineintegrationsdk.db.entity.TlpTableEntity

@Dao
interface TlpDao {

    // ---------- écriture ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: TlpTableEntity)

    @Insert
    suspend fun insertFields(fields: List<TlpFieldEntity>)

    @Query("DELETE FROM tlp_field WHERE tableId = :tableId")
    suspend fun deleteFields(tableId: String)

    // ---------- lecture ----------

    @Query("SELECT * FROM tlp_table ORDER BY id")
    suspend fun getAllTables(): List<TlpTableEntity>

    @Query("SELECT * FROM tlp_table WHERE id = :tableId")
    suspend fun getTable(tableId: String): TlpTableEntity?

    @Query("SELECT COUNT(*) FROM tlp_table")
    suspend fun countTables(): Int

    @Query("SELECT version FROM tlp_table WHERE id = :tableId")
    suspend fun getVersion(tableId: String): String?

    @Query("SELECT * FROM tlp_field WHERE tableId = :tableId ORDER BY recordIndex, id")
    suspend fun getFields(tableId: String): List<TlpFieldEntity>

    @Query(
        "SELECT * FROM tlp_field WHERE tableId = :tableId AND recordIndex = :recordIndex ORDER BY id"
    )
    suspend fun getRecord(tableId: String, recordIndex: Int): List<TlpFieldEntity>

    /** Premier enregistrement dont le champ [path] vaut [value]. Utilise l'index. */
    @Query(
        "SELECT recordIndex FROM tlp_field " +
                "WHERE tableId = :tableId AND path = :path AND value = :value LIMIT 1"
    )
    suspend fun findRecordIndex(tableId: String, path: String, value: String): Int?

    /** Test blacklist : le PAN est-il présent dans table13 (tag 20008) ? */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM tlp_field " +
                "WHERE tableId = '13' AND path = '20008' AND value = :pan)"
    )
    suspend fun isPanBlacklisted(pan: String): Boolean
}