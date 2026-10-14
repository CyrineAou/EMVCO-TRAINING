package com.example.terminalmodule.internal

import android.util.Log
import com.example.terminalmodule.api.EmvKernelConfigurator
import com.example.terminalmodule.model.EmvAidParam
import com.example.terminalmodule.model.EmvCapkParam
import com.example.terminalmodule.model.EmvTerminalParam
import com.nexgo.libpboc.ByteUtils
import com.nexgo.oaf.apiv3.emv.AidEntity
import com.nexgo.oaf.apiv3.emv.CapkEntity
import com.nexgo.oaf.apiv3.emv.EmvHandler2

internal class NexgoEmvConfigurator(
     val emvHandler: EmvHandler2
) : EmvKernelConfigurator {

    companion object {
        private const val TAG = "EMV_PARAMS"
        private const val TAG_KERNEL = "EMV_KERNEL"
    }

    @Volatile
    var terminalParam: EmvTerminalParam? = null
        private set

    override fun loadParameters(
        aids: List<EmvAidParam>,
        capks: List<EmvCapkParam>,
        terminal: EmvTerminalParam
    ): Result<Unit> = runCatching {

        Log.d(TAG, "NexGo : AID à charger = ${aids.size}")
        Log.d(TAG, "NexGo : CAPK à charger = ${capks.size}")

        // ---------------- AID ----------------
        val aidList = ArrayList(aids.map { p ->
            AidEntity().apply {
                aid = p.aid
                asi = p.asi
                appVerNum = p.appVersion
                tacDenial = p.tacDenial
                tacOnline = p.tacOnline
                tacDefault = p.tacDefault
                floorLimit = p.floorLimit
                threshold = p.threshold
                contactlessFloorLimit = p.contactlessFloorLimit
                contactlessCvmLimit = p.contactlessCvmLimit
                contactlessTransLimit = p.contactlessTransLimit
            }
        })

        emvHandler.delAllAid()
        val aidRet = emvHandler.setAidParaList(aidList)
        Log.d(TAG_KERNEL, "setAidParaList -> code retour = $aidRet")
        check(aidRet == 0) { "setAidParaList a retourné $aidRet" }

        // ---------------- CAPK ----------------
        // Doublons rid/index : on garde la date d'expiration la plus lointaine
        val uniqueCapks = capks
            .groupBy { "${it.rid}/${it.index}" }
            .map { (key, group) ->
                if (group.size > 1) {
                    Log.w(TAG_KERNEL, "Doublon CAPK $key (${group.size} entrées) : on garde la plus récente")
                }
                group.maxByOrNull { it.expireDate }!!
            }

        emvHandler.delAllCapk()

        // Envoi clé par clé : une clé refusée n'empêche pas les autres
        var loaded = 0
        val rejected = mutableListOf<String>()

        uniqueCapks.forEach { p ->
            val capk = CapkEntity().apply {
                rid = p.rid
                capkIdx = p.index
                hashInd = p.hashInd
                arithInd = p.arithInd
                modulus = p.modulus
                exponent = p.exponent
                expireDate = p.expireDate
                checkSum = p.checkSum
            }
            val ret = emvHandler.setCAPKList(arrayListOf(capk))
            if (ret == 0) {
                loaded++
            } else {
                val info = "${p.rid}/${p.index} date=${p.expireDate} modLen=${p.modulus.length / 2}o exp=${p.exponent}"
                rejected += info
                Log.w(TAG_KERNEL, "CAPK refusée (code $ret) : $info")
            }
        }

        Log.d(TAG_KERNEL, "CAPK : ${uniqueCapks.size} envoyées, $loaded acceptées, ${rejected.size} refusées")
        check(loaded > 0) { "Aucune CAPK acceptée par le kernel" }

        // ---------------- Relecture ----------------
        readBackFromKernel(expectedAid = aidList.size, expectedCapk = loaded)

        terminalParam = terminal
        Log.d(TAG, "NexGo : chargement terminé ($loaded CAPK, ${rejected.size} refusées)")

        Unit
    }.onFailure { Log.e(TAG, "NexGo : échec chargement", it) }

    /** Pose 9F33 et 9F66 pour la transaction. */
    fun applyTerminalTlv(contactless: Boolean) {
        val p = terminalParam ?: return
        if (contactless) {
            emvHandler.setTlv(hex("9F33"), hex(p.contactlessCapabilities))
            emvHandler.setTlv(hex("9F66"), hex(p.contactlessTtq))
        } else {
            emvHandler.setTlv(hex("9F33"), hex(p.contactCapabilities))
        }
    }

    // ---------------- Relecture du kernel ----------------

    private fun readBackFromKernel(expectedAid: Int, expectedCapk: Int) {
        Log.d(TAG_KERNEL, "===== CONTENU RELU DANS LE KERNEL L2 =====")
        try {
            Log.d(TAG_KERNEL, "AID dans le kernel  : ${emvHandler.getAidListNum()} (envoyés : $expectedAid)")
            Log.d(TAG_KERNEL, "CAPK dans le kernel : ${emvHandler.getCapkListNum()} (acceptées : $expectedCapk)")

            val aids: List<*>? = emvHandler.getAidList()
            aids?.forEachIndexed { i, item ->
                val a = item as? AidEntity
                if (a != null) {
                    Log.d(
                        TAG_KERNEL,
                        "AID[$i] aid=${a.aid} ver=${a.appVerNum} " +
                                "TACden=${a.tacDenial} TAConl=${a.tacOnline} TACdef=${a.tacDefault} " +
                                "floor=${a.floorLimit} clFloor=${a.contactlessFloorLimit} " +
                                "clCvm=${a.contactlessCvmLimit} clTrans=${a.contactlessTransLimit}"
                    )
                } else {
                    Log.d(TAG_KERNEL, "AID[$i] = $item")
                }
            }

            val capks: List<*>? = emvHandler.getCapkList()
            capks?.forEachIndexed { i, item ->
                val c = item as? CapkEntity
                if (c != null) {
                    Log.d(
                        TAG_KERNEL,
                        "CAPK[$i] rid=${c.rid} idx=${c.capkIdx} modLen=${c.modulus.length / 2}o " +
                                "exp=${c.exponent} date=${c.expireDate}"
                    )
                } else {
                    Log.d(TAG_KERNEL, "CAPK[$i] = $item")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG_KERNEL, "Relecture du kernel impossible", e)
        }
        Log.d(TAG_KERNEL, "===== FIN RELECTURE =====")
    }

    private fun hex(s: String): ByteArray = ByteUtils.hexString2ByteArray(s)
}