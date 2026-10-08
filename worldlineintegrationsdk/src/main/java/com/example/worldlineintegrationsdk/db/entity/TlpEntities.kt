package com.example.worldlineintegrationsdk.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une ligne par table TLP (table01 .. table45).
 * id      = attribut id du XML ("01".."45")
 * tag     = attribut tag du XML (ex. "1042")
 * version = attribut version du XML (ex. "0106")
 */
@Entity(tableName = "tlp_table")
data class TlpTableEntity(
    @PrimaryKey val id: String,
    val tag: String,
    val version: String,
    val recordCount: Int,
    val fieldCount: Int,
    val loadedAt: Long
)

/**
 * Une ligne par champ feuille de chaque table.
 *
 * recordIndex : numéro d'enregistrement dans la table (0 pour les tables plates)
 * path        : tag du champ, ou chemin "parent/enfant" pour les champs imbriqués
 *               (ex. "20012/10307" dans table41)
 * value       : valeur sans les espaces de bourrage de fin
 */
@Entity(
    tableName = "tlp_field",
    foreignKeys = [
        ForeignKey(
            entity = TlpTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tableId", "recordIndex"]),
        Index(value = ["tableId", "path", "value"])
    ]
)
data class TlpFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: String,
    val recordIndex: Int,
    val path: String,
    val value: String
)