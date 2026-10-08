package com.example.worldlineintegrationsdk

import android.util.Log
import com.example.worldlineintegrationsdk.model.SdkTable
import com.example.worldlineintegrationsdk.model.SdkTableSource
import com.example.worldlineintegrationsdk.model.TableNames

class FakeWorldlineSdk : SdkTableSource {

    override fun readTables(): List<SdkTable> {

        Log.d("FakeWorldlineSdk", "readTables() called")

        return listOf(
            SdkTable(
                name = TableNames.BLACKLIST,
                version = "1.0",
                rows = listOf(
                    mapOf("pan_hash" to "HASH_001"),
                    mapOf("pan_hash" to "HASH_002")
                )
            ),

            SdkTable(
                name = TableNames.FLOOR_LIMITS,
                version = "1.0",
                rows = listOf(
                    mapOf(
                        "aid" to "A0000000031010",
                        "floor_limit" to "50000"
                    ),
                    mapOf(
                        "aid" to "A0000000041010",
                        "floor_limit" to "100000"
                    )
                )
            )
        )
    }
}