package com.example.worldlineintegrationsdk.model


interface SdkTableSource {

    fun readTables(): List<SdkTable>
}