package com.example.worldlineintegrationsdk.sdk

import android.util.Log
import com.example.worldlineintegrationsdk.dispatcher.SdkTableDispatcher
import com.example.worldlineintegrationsdk.model.SdkTableSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SdkTableAdapter(
    private val sdk: SdkTableSource,
    private val dispatcher: SdkTableDispatcher,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "SdkTableAdapter"
    }

    fun start() {

        Log.d(TAG, "start() called")

        scope.launch {

            Log.d(TAG, "Coroutine started")

            try {

                Log.d(TAG, "Reading tables from SDK")

                val tables = sdk.readTables()

                Log.d(
                    TAG,
                    "SDK returned ${tables.size} table(s)"
                )

                dispatcher.dispatch(tables)

                Log.d(
                    TAG,
                    "Dispatch completed"
                )

            } catch (e: Throwable) {

                Log.e(
                    TAG,
                    "Table synchronization failed",
                    e
                )
            }
        }
    }
}