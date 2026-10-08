package com.example.worldlineintegrationsdk.tlp

import android.util.Log
import com.example.worldlineintegrationsdk.db.AppDatabase

object TlpViewBuilder {

    private const val TAG = "TlpViewBuilder"

    // Noms de tables connus. Les autres s'appellent "tag<tag>".
    private val TABLE_NAMES = mapOf(
        "01" to "currencies",
        "02" to "Display_Messages",
        "03" to "refusal_messages",
        "04" to "merchant_identification",
        "05-06" to "receipt_texts",
        "12" to "amount limits",
        "13" to "cards / PAN and acceptance level",
        "16" to "tac",
        "18" to "date_ranges",
        "19" to "capk",
        "27" to "floor_limits"
    )


    // Paths with the same meaning in every table
    private val COMMON_COLUMNS = mapOf(
        "504" to "aid",
        "1022" to "organisation",
        "21" to "currencyNum"
    )

    // Column names specific to one table
    private val COLUMN_NAMES = mapOf(
        "01" to mapOf("10006" to "currencyAlpha", "1007" to "decimals"),
        "02" to mapOf(
            "10003" to "countryNum", "10015" to "language",
            "10017" to "code", "10013" to "message"
        ),
        "03" to mapOf(
            "10003" to "countryNum", "10015" to "language",
            "10017" to "code", "10013" to "message"
        ),
        "04" to mapOf("10034" to "merchantName", "97" to "merchantCategoryCode", "427" to "siret"),
        "13" to mapOf("20008" to "pan", "20009" to "acceptanceLevel"),
        "16" to mapOf("913" to "tacDenial", "912" to "tacOnline", "914" to "tacDefault"),
        "18" to mapOf("20004" to "startDate", "20005" to "endDate"),
        "19" to mapOf(
            "10054" to "rid", "10055" to "capkIndex", "10056" to "exponent",
            "10057" to "modulusLength", "10058" to "modulus"
        ),
        "20" to mapOf("511" to "applicationVersion"),
        "27" to mapOf("10000" to "floorLimit")
    )

    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    private fun lit(s: String) = "'" + s.replace("'", "''") + "'"

    fun build(db: AppDatabase) {
        val sdb = db.openHelper.writableDatabase

        val tables = mutableListOf<Pair<String, String>>()   // id, tag
        sdb.query("SELECT id, tag FROM tlp_table ORDER BY id").use { c ->
            while (c.moveToNext()) tables += c.getString(0) to c.getString(1)
        }

        for ((id, tag) in tables) {
            val paths = mutableListOf<String>()
            sdb.query(
                "SELECT DISTINCT path FROM tlp_field WHERE tableId = ? ORDER BY path",
                arrayOf<Any?>(id)
            ).use { c -> while (c.moveToNext()) paths += c.getString(0) }
            if (paths.isEmpty()) continue

            val colNames = COLUMN_NAMES[id].orEmpty()
            val cols = paths.joinToString(",\n       ") { p ->
                val col = colNames[p] ?: COMMON_COLUMNS[p] ?: p
                "MAX(CASE WHEN path = ${lit(p)} THEN value END) AS ${q(col)}"
            }
            val name = TABLE_NAMES[id] ?: "tag$tag"
            val view = "t${id}_$name"

            sdb.execSQL("DROP VIEW IF EXISTS ${q(view)}")
            sdb.execSQL(
                """
    CREATE VIEW ${q(view)} AS
    SELECT recordIndex AS id,
           $cols
    FROM tlp_field
    WHERE tableId = ${lit(id)}
    GROUP BY recordIndex
    """.trimIndent()
            )
            Log.d(TAG, "vue $view (${paths.size} colonnes)")
        }
    }
}