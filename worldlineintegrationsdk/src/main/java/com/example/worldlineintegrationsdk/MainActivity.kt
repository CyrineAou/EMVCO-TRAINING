package com.example.worldlineintegrationsdk

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.tlp.TlpReferenceMapper
import com.example.worldlineintegrationsdk.tlp.TlpTableLoader
import com.example.worldlineintegrationsdk.tlp.TlpViewBuilder
import com.magellan.tapandgo.validatorsdk.T2UOpenPaymentSDK
import com.magellan.tapandgo.validatorsdk.ValidationSdkConfiguration
import com.magellan.tapandgo.validatorsdk.model.AcqFormat
import com.magellan.tapandgo.validatorsdk.responses.SDKResponse
import com.magellan.tapandgo.validatorsdk.responses.SDKResponseType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WorldlineTest"
        private const val CRASH_TAG = "WorldlineCrash"

        // Un seul format, utilisé dans la config ET dans le téléchargement.
        // POSIX et GNU ont tous deux échoué : le format n'est pas la cause.
        private val ACQ_FORMAT = AcqFormat.GNU

        // Attente max de l'archive après le téléchargement (il est asynchrone côté SDK).
        private const val ARCHIVE_WAIT_MS = 15_000L

        // Si le SDK ne dépose pas d'archive, copie assets/acq.tar.gz dans filesDir
        // pour tester TlpTableLoader / TlpReferenceMapper. À désactiver ensuite.
        private const val USE_ASSET_FALLBACK = true
        private const val ASSET_ARCHIVE_NAME = "data.tar.gz"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Journalise puis délègue au handler d'origine, sinon l'app ne plante plus
        // "proprement" et le crash est avalé.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(CRASH_TAG, "UNCAUGHT EXCEPTION on thread=${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        Log.d(TAG, "WORLDLINE TEST - POI=66660001 - Device=${Build.MODEL}")

        val configuration = configurationForDemoPlatform()

        lifecycleScope.launch {
            val sdkOk = try {
                startSdk(configuration)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "ERROR during SDK startup", e)
                false
            }

            try {
                loadTlpTables(sdkOk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "ERROR during TLP load", e)
            }
        }
    }

    // ------------------------------------------------------------------
    // SDK
    // ------------------------------------------------------------------

    /** @return true si le SDK est initialisé et le service démarré. */
    private suspend fun startSdk(configuration: ValidationSdkConfiguration): Boolean {
        val sdk = T2UOpenPaymentSDK
        Log.d(TAG, "BO URL = ${configuration.openPaymentBoUrl}")
        Log.d(TAG, "Terminal user = ${configuration.terminalUser}")
        Log.d(TAG, "Terminal password configured = ${configuration.terminalPassword.isNotEmpty()}")
        // Sur Android 10+ Build.SERIAL vaut "unknown" sans permission privilégiée.
        Log.d(TAG, "Serial envoyé au SDK = ${Build.SERIAL}")

        // 1. Initialisation
        val initResp = sdk.init(applicationContext, configuration)
        log("init", initResp)

        val ready = initResp.type == SDKResponseType.SDK_INITIALIZED ||
                initResp.type == SDKResponseType.ERROR_ALREADY_INITIALIZED
        if (!ready) return false

        Log.d(
            TAG, "controlRole=${sdk.userHasControlRole()} " +
                    "admin=${sdk.userHasControlAdminRole()} " +
                    "tokenType=${sdk.getCardTokenType()} " +
                    "locationData=${sdk.getEnableDownloadLocationData()}"
        )

        // 2. Téléchargement des paramètres distants (dépose acq.tar.gz dans filesDir)
        Log.d(TAG, "========== BEFORE DOWNLOAD ==========")
        dumpFiles(filesDir)
        val startResp = sdk.startService()
        log("startService", startResp)

        val dlResult = sdk.checkAndDownloadRemoteSettings(ACQ_FORMAT)
        // Ne lève pas d'exception en cas d'échec : on loggue ce qu'il renvoie.
        Log.d(TAG, "checkAndDownloadRemoteSettings($ACQ_FORMAT) -> $dlResult")

        Log.d(TAG, "========== AFTER DOWNLOAD ==========")
        dumpFiles(filesDir)

//        // 3. Démarrage du service
//        val startResp = sdk.startService()
//        log("startService", startResp)

        Log.d(TAG, "status=${sdk.status()} connected=${sdk.isConnected()} poi=${sdk.getPOI()}")
        return startResp.type == SDKResponseType.SERVICE_STARTED
    }

    // ------------------------------------------------------------------
    // Tables TLP
    // ------------------------------------------------------------------

    private fun isArchiveName(name: String) =
        name.endsWith(".tgz") || name.endsWith(".tar.gz") || name.endsWith(".tar")

    private fun findArchive(): File? = filesDir.listFiles()
        ?.filter { it.isFile && isArchiveName(it.name) }
        ?.maxByOrNull { it.lastModified() }

    /** Attend l'archive (téléchargement asynchrone) jusqu'à ARCHIVE_WAIT_MS. */
    private suspend fun waitForArchive(): File? {
        val deadline = SystemClock.elapsedRealtime() + ARCHIVE_WAIT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            findArchive()?.let { return it }
            delay(500)
        }
        return findArchive()
    }

    private fun copyArchiveFromAssets(): File? = try {
        val target = File(filesDir, ASSET_ARCHIVE_NAME)
        assets.open(ASSET_ARCHIVE_NAME).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        Log.w(TAG, "FALLBACK : archive copiée depuis assets (${target.length()} o)")
        target
    } catch (e: IOException) {
        Log.w(TAG, "Pas d'archive dans assets/$ASSET_ARCHIVE_NAME : ${e.message}")
        null
    }

    private suspend fun loadTlpTables(sdkOk: Boolean) {
        val archive = waitForArchive()
            ?: if (USE_ASSET_FALLBACK) withContext(Dispatchers.IO) { copyArchiveFromAssets() } else null

        withContext(Dispatchers.IO) {
            // Lu APRÈS l'attente : reflète le vrai résultat du téléchargement.
            File(filesDir, "app_logs.txt").takeIf { it.exists() }
                ?.readLines()?.takeLast(40)?.forEach { Log.d(TAG, "SDKLOG: $it") }
            Log.d(TAG, "filesDir = ${filesDir.listFiles()?.map { "${it.name} (${it.length()} o)" }}")

            if (archive == null) {
                Log.w(
                    TAG, "Aucune archive acq.tgz / acq.tar.gz (sdkOk=$sdkOk). " +
                            "Le téléchargement SDK a échoué : voir SDKLOG " +
                            "'Failed to download remote settings version'."
                )
                return@withContext
            }

            val db = AppDatabase.getInstance(applicationContext)
            val loader = TlpTableLoader(db)

            Log.d(TAG, "Source TLP = ${archive.name} (${archive.length()} o)")
            val report = loader.loadTarGz(archive)

            Log.d(
                TAG, "TLP loaded=${report.loaded.size} unchanged=${report.unchanged.size} " +
                        "missing=${report.missing} failed=${report.failed} rows=${report.totalRows}"
            )
            TlpViewBuilder.build(db)
            val dao = db.tlpDao()

            // Remplit floor_limits seulement si table27 est présente (sinon on viderait la table)
            if (dao.getTable("27") != null) {
                TlpReferenceMapper.fillFloorLimits(db)
                Log.d(TAG, "floor_limits remplie depuis table27")
            }

            if (dao.getTable("13") != null) {
                TlpReferenceMapper.fillCardAcceptance(db)
                val cad = db.cardAcceptanceDao()
                Log.d(TAG, "card_acceptance rows=${cad.count()}")
                // Test : à remplacer par un PAN de la table
                Log.d(TAG, "level(4561889605931590) = ${cad.levelFor("4561889605931590")}")
            }

            // --- Vérifications ---
            Log.d(TAG, "PAN blacklisted = ${dao.isPanBlacklisted("4972029840368612")}")

            val tables = dao.getAllTables()
            Log.d(TAG, "tables=${tables.size}")
            tables.forEach {
                Log.d(TAG, "table${it.id} tag=${it.tag} v=${it.version} records=${it.recordCount} fields=${it.fieldCount}")
            }

// Debug temporaire : un enregistrement de chaque table pour repérer les chemins
            tables.forEach { t ->
                db.openHelper.readableDatabase.query(
                    "SELECT recordIndex, path, value FROM tlp_field " +
                            "WHERE tableId='${t.id}' AND recordIndex=0 LIMIT 15"
                ).use { c ->
                    while (c.moveToNext())
                        Log.d(TAG, "table${t.id} rec=${c.getInt(0)} ${c.getString(1)} = ${c.getString(2)}")
                }
            }
        }
    }

    // ------------------------------------------------------------------

    private fun dumpFiles(dir: File, prefix: String = "") {
        dir.listFiles()?.forEach { file ->
            Log.d(
                TAG,
                "$prefix${file.name} dir=${file.isDirectory} " +
                        "size=${file.length()} path=${file.absolutePath}"
            )
            if (file.isDirectory) dumpFiles(file, "$prefix  ")
        }
    }

    private fun log(step: String, r: SDKResponse) {
        Log.d(TAG, "$step -> type=${r.type} message=${r.message} value=${r.value}")
    }

    private fun configurationForDemoPlatform(): ValidationSdkConfiguration {
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
            ACQ_FORMAT,
            "https://keycloak-t2u-demo.apps.dev.caas4noprd.worldline-solutions.com",
            "demoplatformAdmin",
            "control-app",
            "65d8abb8-fde4-40b0-90f4-d0030fefd6d1"
        )
    }
}