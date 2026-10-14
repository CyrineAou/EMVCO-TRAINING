package com.example.nexgointegration

import android.content.Context
import android.util.Log
import com.example.terminalmodule.api.EmvKernelConfigurator
import com.example.terminalmodule.model.EmvAidParam
import com.example.terminalmodule.model.EmvCapkParam
import com.example.terminalmodule.model.EmvTerminalParam
import org.json.JSONArray

class EmvParametersManager(
    private val context: Context,
    private val configurator: EmvKernelConfigurator
) {

    companion object {
        private const val TAG = "EMV_PARAMS"
        private const val CAPK_ASSET = "emv_capk.json"
    }

    /** CAPK écartées lors du dernier loadCapks() (checksum invalide ou doublon). */
    @Volatile
    var ignoredCapks: List<String> = emptyList()
        private set

    fun apply(): Result<Unit> = runCatching {
        val aids = loadAids()
        val capks = loadCapks()
        val terminal = loadTerminalParam()

        Log.d(TAG, "AID lus : ${aids.size}")
        Log.d(TAG, "CAPK valides : ${capks.size} (ignorées : ${ignoredCapks.size})")

        configurator.loadParameters(aids, capks, terminal).getOrThrow()

        Log.d(TAG, "Paramètres envoyés au kernel L2")

        Unit
    }.onFailure { Log.e(TAG, "Échec chargement paramètres", it) }

    fun loadTerminalParam() = EmvTerminalParam(
        contactCapabilities = "6060C8",
        contactlessCapabilities = "E080C8",
        contactlessTtq = "26C0C000"
    )

    // Plus tard : lecture depuis worldline_database.db ou le TMS
    fun loadAids(): List<EmvAidParam> = listOf(
        EmvAidParam(
            aid = "A0000000031010", appVersion = "0096",
            tacDenial = "0000000000", tacOnline = "DC4000A800", tacDefault = "DC4000A800",
            floorLimit = 50000L,
            contactlessFloorLimit = 50000L,
            contactlessCvmLimit = 50000L,
            contactlessTransLimit = 999999L
        ),
        EmvAidParam(
            aid = "A0000000041010", appVersion = "0033",
            tacDenial = "0000000000", tacOnline = "DC4000A800", tacDefault = "DC4000A800",
            floorLimit = 50000L,
            contactlessFloorLimit = 50000L,
            contactlessCvmLimit = 50000L,
            contactlessTransLimit = 999999L
        )
    )

    /** Lit le JSON, écarte les clés au checksum invalide et les doublons. */
    fun loadCapks(): List<EmvCapkParam> {
        val ignored = mutableListOf<String>()

        // 1) Checksum : SHA-1(RID + index + modulus + exposant) doit égaler le checksum fourni
        val valid = readCapksFromJson().filter { c ->
            val computed = runCatching {
                capkChecksum(c.rid, c.index, c.modulus, c.exponent)
            }.getOrNull()
            val ok = computed != null && computed.equals(c.checkSum, ignoreCase = true)
            if (!ok) {
                val info = "${c.rid}/${c.index} : checksum invalide (calculé=$computed, fourni=${c.checkSum})"
                ignored += info
                Log.w(TAG, "CAPK ignorée : $info")
            }
            ok
        }

        // 2) Doublons rid/index : on garde la date d'expiration la plus lointaine
        val unique = valid
            .groupBy { "${it.rid}/${it.index}" }
            .map { (key, group) ->
                if (group.size > 1) {
                    val info = "$key : doublon (${group.size} entrées), on garde la plus récente"
                    ignored += info
                    Log.w(TAG, "CAPK ignorée : $info")
                }
                group.maxByOrNull { it.expireDate }!!
            }

        ignoredCapks = ignored
        return unique
    }

    private fun readCapksFromJson(): List<EmvCapkParam> {
        val json = context.assets.open(CAPK_ASSET)
            .bufferedReader().use { it.readText() }
        val array = JSONArray(json)

        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            var expire = o.getString("expireDate").trim()
            if (expire.length == 8) expire = expire.substring(2)   // YYYYMMDD -> YYMMDD

            EmvCapkParam(
                rid = cleanHex(o.getString("rid")),
                index = o.getInt("capkIdx"),
                hashInd = o.getInt("hashInd"),
                arithInd = o.getInt("arithInd"),
                modulus = cleanHex(o.getString("modulus")),
                exponent = cleanHex(o.getString("exponent")),
                expireDate = expire,
                checkSum = cleanHex(o.getString("checkSum"))
            )
        }
    }
}