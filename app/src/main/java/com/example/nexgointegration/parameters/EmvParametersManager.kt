package com.example.nexgointegration.parameters

import android.content.Context
import android.util.Log
import com.example.nexgointegration.cleanHex
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

    fun apply(): Result<Int> = runCatching {
        val aids = loadAids()
        val capks = loadCapks()
        val terminal = loadTerminalParam()

        Log.d(TAG, "AID lus : ${aids.size}")
        Log.d(TAG, "CAPK lus : ${capks.size}")

        configurator.loadParameters(aids, capks, terminal).getOrThrow()

        Log.d(TAG, "Paramètres envoyés au kernel L2")
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

    fun loadCapks(): List<EmvCapkParam> {
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