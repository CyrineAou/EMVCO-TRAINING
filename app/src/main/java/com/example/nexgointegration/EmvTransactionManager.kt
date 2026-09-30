package com.example.nexgointegration

import android.content.Context
import android.util.Log
import com.example.nexgointegration.gateway.PaymentGateway
import com.nexgo.common.LogUtils
import com.nexgo.libpboc.ByteUtils
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.device.pinpad.WorkKeyTypeEnum
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.emv.AidEntity
import com.nexgo.oaf.apiv3.emv.CandidateAppInfoEntity
import com.nexgo.oaf.apiv3.emv.CapkEntity
import com.nexgo.oaf.apiv3.emv.EmvEntryModeEnum
import com.nexgo.oaf.apiv3.emv.EmvHandler2
import com.nexgo.oaf.apiv3.emv.EmvOnlineResultEntity
import com.nexgo.oaf.apiv3.emv.EmvProcessFlowEnum
import com.nexgo.oaf.apiv3.emv.EmvProcessResultEntity
import com.nexgo.oaf.apiv3.emv.EmvTransConfigurationEntity
import com.nexgo.oaf.apiv3.emv.OnEmvProcessListener2
import com.nexgo.oaf.apiv3.emv.PromptEnum
import org.json.JSONArray
import java.io.InputStream
import java.nio.charset.Charset
import java.util.LinkedHashMap

class EmvTransactionManager(
    private val context: Context,
    private val deviceEngine: DeviceEngine,
    private val listener: Listener,
    paymentGateway: PaymentGateway
) {

    companion object {

        private const val TAG = "EMV_TRANSACTION"

        private const val VISA_AID =
            "A0000000031010"

        private const val MASTERCARD_AID =
            "A0000000041010"

        private const val CONTACT_CAPABILITIES =
            "6060C8"

        private const val CONTACTLESS_CAPABILITIES =
            "E080C8"

        private const val CONTACTLESS_TTQ =
            "26C0C000"
    }

    interface Listener {

        fun onStatus(message: String)

        fun onFinished(
            result: Int,
            resultEntity: EmvProcessResultEntity?
        )

        fun onError(message: String)
    }

    private val emvHandler: EmvHandler2 =
        deviceEngine.getEmvHandler2("Main-Kernel2")

    @Volatile
    private var emvRunning = false

    init {

        emvHandler.emvDebugLog(true)

        LogUtils.setDebugEnable(true)

        configureAids()

        loadCapks()

        configurePinPad()
    }

    // ============================================================
    // PIN PAD
    // ============================================================

    private fun configurePinPad() {

        try {

            val pinPad = deviceEngine.getPinPad()

            /*
             * TEST KEYS ONLY.
             *
             * Ne jamais utiliser ces clés en production.
             */

            val masterKey =
                ByteUtils.hexString2ByteArray(
                    "111111111111111111111111111111"
                )

            val workingKey =
                ByteUtils.hexString2ByteArray(
                    "211111111111111111111111111111"
                )

            pinPad.writeMKey(
                0,
                masterKey,
                masterKey.size
            )

            pinPad.writeWKey(
                0,
                WorkKeyTypeEnum.PINKEY,
                workingKey,
                workingKey.size
            )

            Log.d(
                TAG,
                "PIN PAD configured"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "PIN PAD initialization error",
                e
            )
        }
    }

    // ============================================================
    // AID
    // ============================================================

    private fun configureAids() {

        try {

            val aidList =
                ArrayList<AidEntity>()

            val visa =
                AidEntity().apply {

                    aid = VISA_AID

                    asi = 0

                    /*
                     * 50000 cents = 500.00
                     * à adapter à ta devise et à ton paramétrage L3.
                     */
                    floorLimit = 50000L

                    threshold = 0L

                    tacDenial =
                        "0000000000"

                    tacOnline =
                        "DC4000A800"

                    tacDefault =
                        "DC4000A800"

                    appVerNum =
                        "0096"

                    contactlessFloorLimit =
                        50000L

                    contactlessCvmLimit =
                        50000L

                    contactlessTransLimit =
                        999999L
                }

            aidList.add(visa)

            val mastercard =
                AidEntity().apply {

                    aid = MASTERCARD_AID

                    asi = 0

                    floorLimit = 50000L

                    threshold = 0L

                    tacDenial =
                        "0000000000"

                    tacOnline =
                        "DC4000A800"

                    tacDefault =
                        "DC4000A800"

                    appVerNum =
                        "0033"

                    contactlessFloorLimit =
                        50000L

                    contactlessCvmLimit =
                        50000L

                    contactlessTransLimit =
                        999999L
                }

            aidList.add(mastercard)

            emvHandler.delAllAid()

            emvHandler.setAidParaList(
                aidList
            )

            Log.d(
                TAG,
                "AID configuration completed"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "AID configuration error",
                e
            )
        }
    }

    // ============================================================
    // CAPK
    // ============================================================

    private fun loadCapks() {

        try {

            val inputStream: InputStream =
                context.assets.open(
                    "emv_capk.json"
                )

            val size =
                inputStream.available()

            val buffer =
                ByteArray(size)

            inputStream.read(buffer)

            inputStream.close()

            val json =
                String(
                    buffer,
                    Charset.forName("UTF-8")
                )

            val jsonArray =
                JSONArray(json)

            emvHandler.delAllCapk()

            for (i in 0 until jsonArray.length()) {

                val obj =
                    jsonArray.getJSONObject(i)

                var expireDate =
                    obj.getString("expireDate")
                        .trim()

                if (expireDate.length == 8) {

                    expireDate =
                        expireDate.substring(2)
                }

                val capk =
                    CapkEntity().apply {

                        rid =
                            obj.getString("rid")
                                .replace(
                                    Regex("[^0-9A-Fa-f]"),
                                    ""
                                )
                                .uppercase()

                        capkIdx =
                            obj.getInt("capkIdx")

                        hashInd =
                            obj.getInt("hashInd")

                        arithInd =
                            obj.getInt("arithInd")

                        modulus =
                            obj.getString("modulus")
                                .replace(
                                    Regex("[^0-9A-Fa-f]"),
                                    ""
                                )
                                .uppercase()

                        exponent =
                            obj.getString("exponent")
                                .replace(
                                    Regex("[^0-9A-Fa-f]"),
                                    ""
                                )
                                .uppercase()

                        this.expireDate =
                            expireDate

                        checkSum =
                            obj.getString("checkSum")
                                .replace(
                                    Regex("[^0-9A-Fa-f]"),
                                    ""
                                )
                                .uppercase()
                    }

                emvHandler.setCAPKList(
                    arrayListOf(capk)
                )
            }

            Log.d(
                TAG,
                "CAPK configuration completed"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "CAPK configuration error",
                e
            )
        }
    }

    // ============================================================
    // START TRANSACTION
    // ============================================================

    fun startTransaction(
        amountInCents: String,
        entryMode: EmvEntryModeEnum
    ) {

        if (emvRunning) {

            Log.w(
                TAG,
                "EMV transaction already running"
            )

            return
        }

        emvRunning = true

        listener.onStatus(
            "Initialisation transaction..."
        )

        configureEntryMode(
            entryMode
        )

        val transactionConfig =
            EmvTransConfigurationEntity().apply {

                transAmount =
                    amountInCents

                emvTransType =
                    0x00.toByte()

                termId =
                    "NEXGO001"

                merId =
                    "MERCHANT123"

                /*
                 * IMPORTANT :
                 * Vérifier les codes réellement utilisés
                 * par ton environnement/certification.
                 */
                countryCode =
                    "0788"

                currencyCode =
                    "0788"

                emvEntryModeEnum =
                    entryMode

                emvProcessFlowEnum =
                    EmvProcessFlowEnum
                        .EMV_PROCESS_FLOW_STANDARD
            }

        emvHandler.emvProcess(
            transactionConfig,
            createEmvListener(entryMode)
        )
    }

    // ============================================================
    // ENTRY MODE
    // ============================================================

    private fun configureEntryMode(
        entryMode: EmvEntryModeEnum
    ) {

        try {

            when (entryMode) {

                EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACT -> {

                    emvHandler.setTlv(
                        ByteUtils.hexString2ByteArray("9F33"),
                        ByteUtils.hexString2ByteArray(
                            CONTACT_CAPABILITIES
                        )
                    )
                }

                EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACTLESS -> {

                    emvHandler.setTlv(
                        ByteUtils.hexString2ByteArray("9F33"),
                        ByteUtils.hexString2ByteArray(
                            CONTACTLESS_CAPABILITIES
                        )
                    )

                    emvHandler.setTlv(
                        ByteUtils.hexString2ByteArray("9F66"),
                        ByteUtils.hexString2ByteArray(
                            CONTACTLESS_TTQ
                        )
                    )
                }

                else -> {
                    Log.d(
                        TAG,
                        "Other entry mode"
                    )
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Entry mode configuration error",
                e
            )
        }
    }

    // ============================================================
    // EMV LISTENER
    // ============================================================

    private fun createEmvListener(
        entryMode: EmvEntryModeEnum
    ): OnEmvProcessListener2 {

        return object : OnEmvProcessListener2 {

            override fun onSelApp(
                appNameList: MutableList<String>?,
                candidateList: MutableList<CandidateAppInfoEntity>?,
                isMustSelect: Boolean
            ) {

                Log.d(
                    TAG,
                    "Application selection"
                )

                candidateList?.forEachIndexed {
                        index,
                        candidate ->

                    Log.d(
                        TAG,
                        "Candidate[$index] " +
                                "AID=${candidate.aid} " +
                                "LABEL=${candidate.appLabel} " +
                                "PRIORITY=${candidate.priority}"
                    )
                }

                if (!candidateList.isNullOrEmpty()) {

                    /*
                     * Pour un terminal de test :
                     * première application.
                     *
                     * En certification, la logique de
                     * sélection doit respecter le kernel
                     * et les règles du schéma.
                     */
                    emvHandler
                        .onSetSelAppResponse(0)

                } else {

                    emvHandler
                        .onSetSelAppResponse(-1)
                }
            }

            override fun onConfirmCardNo(
                cardInfo: CardInfoEntity?
            ) {

                emvHandler
                    .onSetConfirmCardNoResponse(
                        cardInfo != null
                    )
            }

            override fun onTransInitBeforeGPO() {

                if (
                    entryMode ==
                    EmvEntryModeEnum
                        .EMV_ENTRY_MODE_CONTACTLESS
                ) {

                    emvHandler.setTlv(
                        ByteUtils.hexString2ByteArray(
                            "9F66"
                        ),
                        ByteUtils.hexString2ByteArray(
                            CONTACTLESS_TTQ
                        )
                    )
                }

                emvHandler
                    .onSetTransInitBeforeGPOResponse(
                        true
                    )
            }

            override fun onCardHolderInputPin(
                isOnlinePin: Boolean,
                remainingTries: Int
            ) {

                Log.d(
                    TAG,
                    "PIN requested. online=$isOnlinePin"
                )

                /*
                 * IMPORTANT :
                 * Ici on ne doit pas automatiquement
                 * accepter un PIN dans une vraie L3.
                 *
                 * Ton application doit lancer le
                 * workflow PIN approprié du SDK.
                 */
                emvHandler.onSetPinInputResponse(
                    true,
                    false
                )
            }

            override fun onContactlessTapCardAgain() {

                listener.onStatus(
                    "Veuillez présenter à nouveau la carte"
                )

                emvHandler
                    .onSetContactlessTapCardResponse(
                        true
                    )
            }

            override fun onOnlineProc() {

                /*
                 * C'est le point critique du workflow L3.
                 *
                 * Ici le kernel demande une autorisation
                 * ONLINE.
                 *
                 * Pour l'instant nous ne faisons PAS une
                 * vraie connexion host.
                 *
                 * Le host doit normalement :
                 *
                 * 1. récupérer les données EMV
                 * 2. construire le message d'autorisation
                 * 3. envoyer vers l'acquéreur
                 * 4. récupérer la réponse
                 * 5. retourner le résultat au kernel.
                 */

                Log.d(
                    TAG,
                    "ONLINE PROCESSING REQUESTED"
                )

                listener.onStatus(
                    "Autorisation ONLINE requise"
                )

                /*
                 * TEST UNIQUEMENT
                 *
                 * Ne pas considérer ceci comme une
                 * implémentation L3 certifiée.
                 */

                val onlineResult =
                    EmvOnlineResultEntity().apply {

                        rejCode =
                            "00"

                        authCode =
                            "AUTH88"

                        recvField55 =
                            byteArrayOf(
                                0x8A.toByte(),
                                0x02,
                                0x30,
                                0x30
                            )
                    }

                emvHandler.onSetOnlineProcResponse(
                    0,
                    onlineResult
                )
            }

            override fun onPrompt(
                prompt: PromptEnum?
            ) {

                Log.d(
                    TAG,
                    "Prompt=$prompt"
                )

                emvHandler
                    .onSetPromptResponse(true)
            }

            override fun onRemoveCard() {

                listener.onStatus(
                    "Retirez la carte"
                )

                emvHandler
                    .onSetRemoveCardResponse()
            }

            override fun onFinish(
                result: Int,
                resultEntity: EmvProcessResultEntity?
            ) {

                emvRunning = false

                Log.d(
                    TAG,
                    "EMV FINISH result=$result"
                )

                debugEmvTags()

                listener.onFinished(
                    result,
                    resultEntity
                )
            }
        }
    }

    // ============================================================
    // DEBUG TAGS
    // ============================================================

    private fun debugEmvTags() {

        val tags =
            arrayOf(

                "9F26",
                "9F27",
                "9F10",
                "9F37",
                "9F36",

                "95",
                "9B",

                "9F1A",
                "5F2A",
                "9A",
                "9C",

                "9F02",
                "9F03",

                "57",
                "5F34",

                "8F",
                "4F",
                "5F24",

                "8E",
                "9F34",

                "9F66",
                "9F6C",

                "82",
                "94",

                "9F33",
                "9F35",
                "9F6E",

                "9F0A",
                "BF0C"
            )

        try {

            val raw =
                emvHandler.getTlvByTags(
                    tags
                )

            val parsed =
                parseTlvHexString(raw)

            Log.d(
                TAG,
                "================ EMV TAGS ================"
            )

            tags.forEach { tag ->

                Log.d(
                    TAG,
                    "$tag = ${parsed[tag] ?: "ABSENT"}"
                )
            }

            Log.d(
                TAG,
                "=========================================="
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to read EMV tags",
                e
            )
        }
    }

    // ============================================================
    // TLV PARSER
    // ============================================================

    private fun parseTlvHexString(
        hex: String?
    ): Map<String, String> {

        val result =
            LinkedHashMap<String, String>()

        if (hex.isNullOrBlank()) {
            return result
        }

        val bytes =
            ByteUtils.hexString2ByteArray(
                hex
                    .replace(
                        Regex("\\s+"),
                        ""
                    )
                    .uppercase()
            )

        var i = 0

        while (i < bytes.size) {

            val tagStart = i

            val firstByte =
                bytes[i].toInt() and 0xFF

            i++

            if (
                firstByte and 0x1F ==
                0x1F
            ) {

                while (
                    i < bytes.size &&
                    (bytes[i].toInt() and 0x80) != 0
                ) {
                    i++
                }

                if (i < bytes.size) {
                    i++
                }
            }

            if (i > bytes.size) {
                break
            }

            val tagHex =
                ByteUtils.byteArray2HexString(
                    bytes.copyOfRange(
                        tagStart,
                        i
                    )
                ).uppercase()

            if (i >= bytes.size) {
                break
            }

            val lenByte =
                bytes[i].toInt() and 0xFF

            i++

            val length =
                if (
                    lenByte and 0x80 != 0
                ) {

                    var len = 0

                    repeat(
                        lenByte and 0x7F
                    ) {

                        if (i >= bytes.size) {
                            return result
                        }

                        len =
                            (len shl 8) or
                                    (
                                            bytes[i++]
                                                .toInt() and
                                                    0xFF
                                            )
                    }

                    len

                } else {

                    lenByte
                }

            if (
                length < 0 ||
                i + length > bytes.size
            ) {
                break
            }

            result[tagHex] =
                ByteUtils.byteArray2HexString(
                    bytes.copyOfRange(
                        i,
                        i + length
                    )
                ).uppercase()

            i += length
        }

        return result
    }

    // ============================================================
    // CANCEL
    // ============================================================

    fun cancel() {

        try {

            if (emvRunning) {

                emvHandler
                    .emvProcessCancel()
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "EMV cancel error",
                e
            )
        }

        emvRunning = false
    }
}