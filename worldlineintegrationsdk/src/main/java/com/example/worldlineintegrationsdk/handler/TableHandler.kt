package com.example.worldlineintegrationsdk.handler

import com.example.worldlineintegrationsdk.model.SdkTable

interface TableHandler {
    val tableName: String
    suspend fun handle(table: SdkTable)
}
