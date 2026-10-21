package com.example.worldlineintegrationsdk

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.tlp.TlpReferenceMapper
import com.example.worldlineintegrationsdk.tlp.TlpTableLoader
import com.example.worldlineintegrationsdk.tlp.TlpViewBuilder
import com.magellan.tapandgo.validatorsdk.T2UOpenPaymentSDK
import com.magellan.tapandgo.validatorsdk.ValidationSdkConfiguration
import com.magellan.tapandgo.validatorsdk.model.AcqFormat
import com.magellan.tapandgo.validatorsdk.responses.SDKResponseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class WorldlineSync(private val context: Context) {

    private companion object {
        const val TAG = "WorldlineSync"
        val ACQ_FORMAT = AcqFormat.GNU
        const val ARCHIVE_WAIT_MS = 15_000L
        const val USE_ASSET_FALLBACK = true          // offline test: assets/data.tar.gz
        const val ASSET_ARCHIVE_NAME = "data.tar"
    }

    /** Downloads (or falls back to the asset archive) and loads the TLP tables into Room. */
    suspend fun run(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val sdkOk = startSdk()
            val archive = (if (sdkOk) waitForArchive() else findArchive())
                ?: if (USE_ASSET_FALLBACK) copyArchiveFromAssets() else null
            checkNotNull(archive) { "No TLP archive (sdkOk=$sdkOk), see SDK logs" }

            val db = AppDatabase.getInstance(context)
            val report = TlpTableLoader(db).loadTarGz(archive)
            Log.d(
                TAG,
                "TLP loaded=${report.loaded.size} failed=${report.failed} rows=${report.totalRows}"
            )

            TlpViewBuilder.build(db)

            val tables = db.tlpDao().getAllTables()
            tables.forEach {
                Log.d(TAG, "table${it.id} tag=${it.tag} v=${it.version} records=${it.recordCount} fields=${it.fieldCount}")
            }

            db.openHelper.readableDatabase.query(
                "SELECT tableId, path, value FROM tlp_field " +
                        "WHERE value LIKE 'A00000000%' OR upper(value) LIKE 'A00000000%' LIMIT 30"
            ).use { c ->
                while (c.moveToNext())
                    Log.d(TAG, "AIDHIT table${c.getString(0)} ${c.getString(1)} = ${c.getString(2)}")
            }

            tables.forEach { t ->
                db.openHelper.readableDatabase.query(
                    "SELECT recordIndex, path, value FROM tlp_field WHERE tableId='${t.id}' AND recordIndex=0 LIMIT 15"
                ).use { c ->
                    while (c.moveToNext())
                        Log.d(TAG, "table${t.id} rec=${c.getInt(0)} ${c.getString(1)} = ${c.getString(2)}")
                }
            }
            val dao = db.tlpDao()
            if (dao.getTable("27") != null) TlpReferenceMapper.fillFloorLimits(db)
            if (dao.getTable("13") != null) TlpReferenceMapper.fillCardAcceptance(db)
            // TODO: fill the aid / capk tables here once the TLP table ids are known
        }
    }

    private suspend fun startSdk(): Boolean {
        val sdk = T2UOpenPaymentSDK
        val init = sdk.init(context, configuration())
        Log.d(TAG, "init -> type=${init.type} message=${init.message}")
        val ready = init.type == SDKResponseType.SDK_INITIALIZED ||
                init.type == SDKResponseType.ERROR_ALREADY_INITIALIZED
        if (!ready) return false

        val start = sdk.startService()
        Log.d(TAG, "startService -> type=${start.type} message=${start.message}")
        val dl = sdk.checkAndDownloadRemoteSettings(ACQ_FORMAT)
        Log.d(TAG, "checkAndDownloadRemoteSettings -> $dl")

        File(context.filesDir, "app_logs.txt").takeIf { it.exists() }
            ?.readLines()?.takeLast(40)?.forEach { Log.d(TAG, "SDKLOG: $it") }
        Log.d(TAG, "Serial = ${Build.SERIAL}")   // "unknown" sur Android 9 sans permission
        return start.type == SDKResponseType.SERVICE_STARTED
    }

    private fun findArchive(): File? = context.filesDir.listFiles()
        ?.filter {
            it.isFile && (it.name.endsWith(".tgz") || it.name.endsWith(".tar.gz") || it.name.endsWith(
                ".tar"
            ))
        }
        ?.maxByOrNull { it.lastModified() }

    private suspend fun waitForArchive(): File? {
        val deadline = SystemClock.elapsedRealtime() + ARCHIVE_WAIT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            findArchive()?.let { return it }
            delay(500)
        }
        return findArchive()
    }

    private fun copyArchiveFromAssets(): File? = try {
        File(context.filesDir, ASSET_ARCHIVE_NAME).also { target ->
            context.assets.open(ASSET_ARCHIVE_NAME)
                .use { i -> target.outputStream().use { o -> i.copyTo(o) } }
            Log.w(TAG, "FALLBACK: archive copied from assets (${target.length()} o)")
        }
    } catch (e: Exception) {
        Log.e(TAG, "FALLBACK failed: assets/$ASSET_ARCHIVE_NAME", e)
        null
    }
    private fun configuration(): ValidationSdkConfiguration {
        return ValidationSdkConfiguration(
            "https://demoplatform-d-t2u-demo.apps.dev.caas4noprd.worldline-solutions.com/",
            "66660001",
            "deviceReference",
            Build.MODEL,
            "terminal",
            "bN5TY6dDE4ae84FVMbCkNRwTU2zWs2DS",
            "product",
            "1.0.0",
            "2.0.0",
            Build.SERIAL,
            "199671801915",
            MainActivityWorldLine.ACQ_FORMAT,
            "https://keycloak-t2u-demo.apps.dev.caas4noprd.worldline-solutions.com",
            "demoplatformAdmin",
            "control-app",
            "65d8abb8-fde4-40b0-90f4-d0030fefd6d1"
        )
    }
}