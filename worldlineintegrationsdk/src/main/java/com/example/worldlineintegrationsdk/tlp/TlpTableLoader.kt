package com.example.worldlineintegrationsdk.tlp

import android.content.res.AssetManager
import android.util.Log
import androidx.room.withTransaction
import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.db.entity.TlpFieldEntity
import com.example.worldlineintegrationsdk.db.entity.TlpTableEntity
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

data class TlpLoadReport(
    val loaded: List<String>,        // ids chargés ou mis à jour
    val unchanged: List<String>,     // ids déjà à jour (même version)
    val missing: List<String>,       // ids 01..45 absents de la source
    val failed: Map<String, String>, // nom de fichier -> message d'erreur
    val totalRows: Int
) {
    val isComplete get() = missing.isEmpty() && failed.isEmpty()
}

class TlpTableLoader(private val db: AppDatabase) {

    private companion object {
        const val TAG = "TlpTableLoader"
        const val EXPECTED_TABLES = 45
        const val BATCH_SIZE = 5_000
        // table01, table13.xml, dossier/table45 ...
        val ENTRY_NAME = Regex("""(?i)(?:.*/)?table(\d{2})(?:\.xml)?$""")
    }

    private class Acc {
        val loaded = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        val seen = mutableSetOf<String>()
        var totalRows = 0
    }

    /**
     * Charge les fichiers déjà extraits depuis un dossier des assets
     * (ex. app/src/main/assets/tables/table01 ... table45).
     */
    suspend fun loadAssetsDir(
        assets: AssetManager,
        dir: String = "tables",
        force: Boolean = false
    ): TlpLoadReport {
        val acc = Acc()
        val names = assets.list(dir).orEmpty().sorted()
        if (names.isEmpty()) Log.w(TAG, "Aucun fichier dans assets/$dir")

        for (name in names) {
            if (!ENTRY_NAME.matches(name)) continue
            val bytes = try {
                assets.open("$dir/$name").use { it.readBytes() }
            } catch (e: Exception) {
                acc.failed[name] = e.message ?: e.javaClass.simpleName
                continue
            }
            importOne(name, bytes, force, acc)
        }
        return buildReport(acc)
    }

    private fun openArchiveStream(file: File): InputStream {
        val raw = BufferedInputStream(file.inputStream())
        raw.mark(2)
        val b0 = raw.read()
        val b1 = raw.read()
        raw.reset()
        return if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(raw) else raw
    }

    /**
     * Charge l'archive acq.tar.gz téléchargée par le SDK
     * (checkAndDownloadRemoteSettings) dans context.filesDir.
     */
    suspend fun loadTarGz(file: File, force: Boolean = false): TlpLoadReport {
        val acc = Acc()
        if (!file.exists()) {
            Log.w(TAG, "Archive introuvable : ${file.absolutePath}")
            return buildReport(acc)
        }
        openArchiveStream(file).use { stream ->
            for ((name, bytes) in TarReader.entries(stream)) {
                if (!ENTRY_NAME.matches(name)) continue
                importOne(name, bytes, force, acc)
            }
        }
        return buildReport(acc)
    }

    /** Charge toutes les tables d'un zip. */
    suspend fun loadZip(zip: InputStream, force: Boolean = false): TlpLoadReport {
        val acc = Acc()
        ZipInputStream(zip.buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory || !ENTRY_NAME.matches(entry.name)) continue
                importOne(entry.name, zis.readBytes(), force, acc)
            }
        }
        return buildReport(acc)
    }

    private suspend fun importOne(name: String, bytes: ByteArray, force: Boolean, acc: Acc) {
        val dao = db.tlpDao()
        try {
            val fileId = ENTRY_NAME.matchEntire(name)!!.groupValues[1]
            val table = TlpXmlParser.parse(ByteArrayInputStream(bytes))
            val id = table.id.ifEmpty { fileId }
            acc.seen += id

            if (!force && dao.getVersion(id) == table.version) {
                acc.unchanged += id
                return
            }

            db.withTransaction {
                dao.deleteFields(id)
                dao.insertTable(
                    TlpTableEntity(
                        id = id,
                        tag = table.tag,
                        version = table.version,
                        recordCount = table.recordCount,
                        fieldCount = table.fields.size,
                        loadedAt = System.currentTimeMillis()
                    )
                )
                table.fields.chunked(BATCH_SIZE).forEach { chunk ->
                    dao.insertFields(
                        chunk.map {
                            TlpFieldEntity(
                                tableId = id,
                                recordIndex = it.recordIndex,
                                path = it.path,
                                value = it.value
                            )
                        }
                    )
                }
            }
            acc.totalRows += table.fields.size
            acc.loaded += id
            Log.d(TAG, "table$id v${table.version}: ${table.recordCount} records, ${table.fields.size} fields")
        } catch (e: Exception) {
            Log.e(TAG, "Failed on $name", e)
            acc.failed[name] = e.message ?: e.javaClass.simpleName
        }
    }

    private fun buildReport(acc: Acc): TlpLoadReport {
        val missing = (1..EXPECTED_TABLES)
            .map { "%02d".format(it) }
            .filter { it !in acc.seen }
        return TlpLoadReport(acc.loaded, acc.unchanged, missing, acc.failed, acc.totalRows)
    }
}