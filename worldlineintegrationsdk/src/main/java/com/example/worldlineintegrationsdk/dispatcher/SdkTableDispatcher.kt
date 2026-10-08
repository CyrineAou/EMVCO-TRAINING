package com.example.worldlineintegrationsdk.dispatcher

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.worldlineintegrationsdk.handler.TableHandler
import com.example.worldlineintegrationsdk.model.SdkTable

class SdkTableDispatcher(
    handlers: List<TableHandler>
) {

    companion object {
        private const val TAG = "SdkTableDispatcher"
    }

    private val byName: Map<String, TableHandler> =
        handlers.associateBy { it.tableName }

    private val mutex = Mutex()

    suspend fun dispatch(
        tables: List<SdkTable>
    ) = mutex.withLock {

        Log.d(TAG, "Dispatching ${tables.size} table(s)")

        for (table in tables) {

            val handler = byName[table.name]

            if (handler == null) {

                Log.w(
                    TAG,
                    "Unknown table ignored: ${table.name}"
                )

                continue
            }

            try {

                Log.d(
                    TAG,
                    "Processing table=${table.name}, version=${table.version}"
                )

                handler.handle(table)

                Log.d(
                    TAG,
                    "Table processed successfully: ${table.name}"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to process table: ${table.name}",
                    e
                )

            }
        }
    }
}