package com.example.worldlineintegrationsdk

import kotlinx.coroutines.CoroutineScope
import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.dispatcher.SdkTableDispatcher
import com.example.worldlineintegrationsdk.handler.BlacklistHandler
import com.example.worldlineintegrationsdk.handler.FloorLimitHandler
import com.example.worldlineintegrationsdk.handler.TacHandler
import com.example.worldlineintegrationsdk.model.SdkTableSource
import com.example.worldlineintegrationsdk.repository.ReferenceRepository
import com.example.worldlineintegrationsdk.sdk.SdkTableAdapter

class ReferenceModule(
    db: AppDatabase,
    sdk: SdkTableSource,
    scope: CoroutineScope
) {

    val repository = ReferenceRepository(db)

    private val dispatcher = SdkTableDispatcher(
        handlers = listOf(
            BlacklistHandler(db),
            FloorLimitHandler(db),
            TacHandler(db)
        )
    )

    val adapter = SdkTableAdapter(
        sdk = sdk,
        dispatcher = dispatcher,
        scope = scope
    )
}