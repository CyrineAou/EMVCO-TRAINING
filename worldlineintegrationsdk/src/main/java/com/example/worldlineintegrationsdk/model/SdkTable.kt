package com.example.worldlineintegrationsdk.model

/** Snapshot neutre d'une table du SDK (aucune dépendance au SDK). */
data class SdkTable(
    val name: String,
    val version: String?,
    val rows: List<Map<String, String>>
)
