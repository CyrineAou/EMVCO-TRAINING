package com.example.nexgointegration.parameters

import android.content.Context
import android.util.Log
import com.example.nexgointegration.capkChecksum
import com.example.nexgointegration.cleanHex
import com.example.terminalmodule.model.EmvAidParam
import com.example.terminalmodule.model.EmvCapkParam
import com.example.terminalmodule.model.EmvTerminalParam
import com.example.worldlineintegrationsdk.tlp.TlpEmvReader

class WorldlineEmvParametersProvider(context: Context) : EmvParametersProvider {

    private companion object {
        const val TAG = "EMV_PARAMS"
        const val HASH_SHA1 = 1
        const val ARITH_RSA = 1
        const val NO_EXPIRY = "491231"      // la table 19 ne fournit pas de date
    }

    private val reader = TlpEmvReader(context)

    override fun getAids(): List<EmvAidParam> = reader.aids().map { a ->
        EmvAidParam(
            aid = cleanHex(a.aid),
            appVersion = cleanHex(a.appVersion ?: "0001"),
            tacDenial = cleanHex(a.tacDenial ?: "0000000000"),
            tacOnline = cleanHex(a.tacOnline ?: "DC4000A800"),
            tacDefault = cleanHex(a.tacDefault ?: "DC4000A800"),
            floorLimit = a.floorLimit ?: 0L,
            contactlessFloorLimit = a.clFloorLimit ?: 0L,
            contactlessCvmLimit = a.clCvmLimit ?: 0L,
            contactlessTransLimit = a.clTransLimit ?: 0L
        ).also {
            Log.d(TAG, "AID ${it.aid} v${it.appVersion} TAC[den=${it.tacDenial} onl=${it.tacOnline} def=${it.tacDefault}] " +
                    "floor=${it.floorLimit} cl[floor=${it.contactlessFloorLimit} cvm=${it.contactlessCvmLimit} trans=${it.contactlessTransLimit}]")
        }
    }.also { Log.d(TAG, "AID lus depuis la base : ${it.size}") }

    override fun getCapks(): List<EmvCapkParam> = reader.capks().map { c ->
        val rid = cleanHex(c.rid)
        val index = cleanHex(c.index).toInt(16)
        val modulus = cleanHex(c.modulus)
        val exponent = cleanHex(c.exponent).trimStart('0').ifEmpty { "03" }.let { if (it.length % 2 == 1) "0$it" else it }
        c.declaredLength?.let {
            if (modulus.length / 2 != it) Log.w(TAG, "CAPK $rid/$index : modulus ${modulus.length / 2} o, attendu $it")
        }
        EmvCapkParam(
            rid = rid, index = index,
            hashInd = HASH_SHA1, arithInd = ARITH_RSA,
            modulus = modulus, exponent = exponent,
            expireDate = NO_EXPIRY,
            checkSum = capkChecksum(rid, index, modulus, exponent)
        )
    }.also { Log.d(TAG, "CAPK lues depuis la base : ${it.size}") }

    override fun getTerminalParam() = EmvTerminalParam(
        contactCapabilities = "6060C8",
        contactlessCapabilities = "E080C8",
        contactlessTtq = "26C0C000"
    )
}