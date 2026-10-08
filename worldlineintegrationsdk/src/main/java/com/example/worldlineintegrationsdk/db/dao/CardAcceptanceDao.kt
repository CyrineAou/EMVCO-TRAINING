package com.example.worldlineintegrationsdk.db.dao

import androidx.room.*
import com.example.worldlineintegrationsdk.db.entity.CardAcceptanceEntity

data class CardAcceptanceRow(val id: Int, val pan: String, val acceptanceLevel: String)

@Dao
interface CardAcceptanceDao {

    @Query("""
        SELECT a.recordIndex AS id, a.value AS pan, b.value AS acceptanceLevel
        FROM tlp_field a
        JOIN tlp_field b
          ON b.tableId = a.tableId
         AND b.recordIndex = a.recordIndex
         AND b.path = '20009'
        WHERE a.tableId = :tableNumber AND a.path = '20008'
        ORDER BY a.recordIndex
    """)
    suspend fun readFromTlp(tableNumber: String): List<CardAcceptanceRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<CardAcceptanceEntity>)

//    @Query("DELETE FROM card_acceptance WHERE tableNumber = :tableNumber")
//    suspend fun clear(tableNumber: String)

    @Query("SELECT acceptanceLevel FROM card_acceptance WHERE pan = :pan LIMIT 1")
    suspend fun levelFor(pan: String): String?

    @Query("SELECT COUNT(*) FROM card_acceptance")
    suspend fun count(): Int
}