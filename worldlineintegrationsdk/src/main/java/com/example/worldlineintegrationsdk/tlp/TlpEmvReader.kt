package com.example.worldlineintegrationsdk.tlp

import android.content.Context
import com.example.worldlineintegrationsdk.db.AppDatabase

data class TlpAid(
    val aid: String,
    val tacDenial: String?, val tacOnline: String?, val tacDefault: String?,
    val appVersion: String?,
    val floorLimit: Long?,
    val clTransLimit: Long?, val clCvmLimit: Long?, val clFloorLimit: Long?
)

data class TlpCapk(
    val rid: String, val index: String, val exponent: String,
    val modulus: String, val declaredLength: Int?
)

/** Lit les AID / CAPK dans tlp_field. Appeler hors thread principal si possible. */
class TlpEmvReader(context: Context) {

    private val db = AppDatabase.getInstance(context.applicationContext)

    /** Enregistrements d'une table : liste de (path -> value). */
    private fun table(id: String): List<Map<String, String>> {
        val byRecord = sortedMapOf<Int, MutableMap<String, String>>()
        db.openHelper.readableDatabase.query(
            "SELECT recordIndex, path, value FROM tlp_field WHERE tableId = ?",
            arrayOf<Any?>(id)
        ).use { c ->
            while (c.moveToNext()) {
                byRecord.getOrPut(c.getInt(0)) { mutableMapOf() }[c.getString(1)] = c.getString(2) ?: ""
            }
        }
        return byRecord.values.toList()
    }

    private fun Map<String, String>.long(path: String) = this[path]?.trim()?.toLongOrNull()

    /** Choisit l'enregistrement de l'AID, de préférence en EUR (978). */
    private fun List<Map<String, String>>.forAid(aid: String): Map<String, String>? {
        val rows = filter { it["504"] == aid }
        return rows.firstOrNull { it["21"] == "978" } ?: rows.firstOrNull()
    }

    fun aids(): List<TlpAid> {
        val versions = table("20")
        val floors = table("27")
        val contactless = table("43")
        return table("16").mapNotNull { r ->
            val aid = r["504"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cl = contactless.forAid(aid)
            TlpAid(
                aid = aid,
                tacDenial = r["913"], tacOnline = r["914"], tacDefault = r["912"],
                appVersion = versions.firstOrNull { it["504"] == aid }?.get("511"),
                floorLimit = floors.forAid(aid)?.long("10000"),
                clTransLimit = cl?.long("10426"),
                clCvmLimit = cl?.long("10432"),
                clFloorLimit = cl?.long("10427")
            )
        }.distinctBy { it.aid }
    }

    fun capks(): List<TlpCapk> = table("19").mapNotNull { r ->
        val rid = r["10054"] ?: return@mapNotNull null
        val modulus = r["10058"] ?: return@mapNotNull null
        TlpCapk(
            rid = rid,
            index = r["10055"] ?: return@mapNotNull null,
            exponent = r["10056"] ?: return@mapNotNull null,
            modulus = modulus,
            declaredLength = r["10057"]?.trim()?.toIntOrNull(16)
        )
    }
}