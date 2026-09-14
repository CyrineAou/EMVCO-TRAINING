package com.example.nexgointegration

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity

import com.nexgo.common.LogUtils
import com.nexgo.libpboc.ByteUtils
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.SdkResult
import com.nexgo.oaf.apiv3.device.pinpad.OnPinPadInputListener
import com.nexgo.oaf.apiv3.device.pinpad.PinAlgorithmModeEnum
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener
import com.nexgo.oaf.apiv3.emv.*

import org.json.JSONArray

import java.io.InputStream
import java.nio.charset.Charset
import java.util.HashSet
import java.util.LinkedHashMap

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "APDU_CLEAN"
        private const val CONTACTLESS_TTQ = "2680C000"
        private const val CONTACT_CAPABILITIES = "6060C8"
        private const val CONTACTLESS_CAPABILITIES = "E080C8"
        private const val VISA_AID = "A0000000031010"
        private const val MASTERCARD_AID = "A0000000041010"
    }

    private lateinit var deviceEngine: DeviceEngine
    private lateinit var emvHandler: EmvHandler2

    private lateinit var statusTextView: TextView
    private lateinit var pinMaskTextView: TextView

    @Volatile
    private var emvInProgress = false

    @Volatile
    private var searchInProgress = false

    private val loadedCapkKeys = mutableSetOf<Pair<String, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        pinMaskTextView = findViewById(R.id.pinTextView)

        deviceEngine = APIProxy.getDeviceEngine(this)

        emvHandler = deviceEngine.getEmvHandler2("Main-Kernel2")

        /*
         * Enable Nexgo EMV debug traces.
         *
         * This is very important for checking:
         * SELECT
         * GPO
         * READ RECORD
         * GENERATE AC
         */
        emvHandler.emvDebugLog(true)

        LogUtils.setDebugEnable(true)

        Log.d(TAG, "================================")
        Log.d(TAG, "NEXGO EMV APPLICATION START")
        Log.d(TAG, "================================")

        initPinPadKeys()
        configureAids()
        injectCapkFromJson()

        statusTextView.post {
            startBancaireWorkflow("10000")
        }
    }

    // ================================================================
    // KEY MANAGEMENT
    // ================================================================

    private fun initPinPadKeys() {

        try {
            val pinPad = deviceEngine.getPinPad()

            val masterKey =
                ByteUtils.hexString2ByteArray(
                    "11111111111111111111111111111111"
                )

            val result = pinPad.writeMKey(
                1,
                masterKey,
                masterKey.size
            )

            Log.d(
                TAG,
                "writeMKey result = $result"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erreur initialisation Master Key",
                e
            )
        }
    }

    // ================================================================
    // AID CONFIGURATION
    // ================================================================

    private fun configureAids() {

        try {

            val aidList = ArrayList<AidEntity>()

            // --------------------------------------------------------
            // VISA
            // --------------------------------------------------------

            val visaAid = AidEntity().apply {

                aid = VISA_AID

                asi = 0

                floorLimit = 10000L

                threshold = 0L

                tacDenial = "B070E00000"

                tacOnline = "DC4000A800"

                tacDefault = "DC4000A800"

                appVerNum = "0096"

                contactlessFloorLimit = 0L

                contactlessCvmLimit = 5000L

                contactlessTransLimit = 50000L
            }

            aidList.add(visaAid)

            // --------------------------------------------------------
            // MASTERCARD
            // --------------------------------------------------------

            val mcAid = AidEntity().apply {

                aid = MASTERCARD_AID

                asi = 0

                appVerNum = "0002"

                transType = "00"

                onlinePinCap = 1

                tacDenial = "B070C00000"

                tacOnline = "FE50F8F800"

                tacDefault = "FE50F8F800"

                floorLimit = 0L

                threshold = 0L

                contactlessFloorLimit = 0L

                contactlessCvmLimit = 5000L

                contactlessTransLimit = 50000L
            }

            aidList.add(mcAid)

            emvHandler.delAllAid()

            val resultAid =
                emvHandler.setAidParaList(aidList)

            Log.d(
                TAG,
                "Résultat configuration AID : $resultAid"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erreur configuration AID",
                e
            )
        }
    }

    // ================================================================
    // CAPK
    // ================================================================

    private fun injectCapkFromJson() {

        try {

            val inputStream: InputStream =
                assets.open("emv_capk.json")

            val size = inputStream.available()

            val buffer = ByteArray(size)

            inputStream.read(buffer)

            inputStream.close()

            val jsonString =
                String(
                    buffer,
                    Charset.forName("UTF-8")
                )

            val jsonArray =
                JSONArray(jsonString)

            emvHandler.delAllCapk()

            loadedCapkKeys.clear()

            for (i in 0 until jsonArray.length()) {

                val jsonObject =
                    jsonArray.getJSONObject(i)

                var rawExpire =
                    jsonObject
                        .getString("expireDate")
                        .trim()

                /*
                 * Some CAPK JSON files contain YYYYMMDD.
                 * Nexgo expects YYMMDD.
                 */
                if (rawExpire.length == 8) {
                    rawExpire =
                        rawExpire.substring(2)
                }

                val cleanRid =
                    jsonObject
                        .getString("rid")
                        .replace(
                            Regex("[^0-9A-Fa-f]"),
                            ""
                        )
                        .uppercase()

                val cleanModulus =
                    jsonObject
                        .getString("modulus")
                        .replace(
                            Regex("[^0-9A-Fa-f]"),
                            ""
                        )
                        .uppercase()

                val cleanExponent =
                    jsonObject
                        .getString("exponent")
                        .replace(
                            Regex("[^0-9A-Fa-f]"),
                            ""
                        )
                        .uppercase()

                val cleanCheckSum =
                    jsonObject
                        .getString("checkSum")
                        .replace(
                            Regex("[^0-9A-Fa-f]"),
                            ""
                        )
                        .uppercase()

                val capk = CapkEntity().apply {

                    rid = cleanRid

                    capkIdx =
                        jsonObject.getInt("capkIdx")

                    hashInd =
                        jsonObject.getInt("hashInd")

                    arithInd =
                        jsonObject.getInt("arithInd")

                    modulus = cleanModulus

                    exponent = cleanExponent

                    expireDate = rawExpire

                    checkSum = cleanCheckSum
                }

                val result =
                    emvHandler.setCAPKList(
                        arrayListOf(capk)
                    )

                if (result == 0) {

                    loadedCapkKeys.add(
                        capk.rid to capk.capkIdx
                    )
                }
            }

            Log.d(
                TAG,
                "CAPK chargées avec succès : $loadedCapkKeys"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erreur lors du chargement des CAPK",
                e
            )
        }
    }

    // ================================================================
    // CARD SEARCH
    // ================================================================

    private fun startBancaireWorkflow(
        amountInCents: String
    ) {

        if (searchInProgress || emvInProgress) {

            Log.w(
                TAG,
                "Transaction déjà en cours"
            )

            return
        }

        runOnUiThread {

            statusTextView.text =
                "Veuillez insérer ou approcher votre carte..."
        }

        val cardReader =
            deviceEngine.getCardReader()

        try {
            cardReader.stopSearch()
        } catch (_: Exception) {
        }

        val slotTypes =
            HashSet<CardSlotTypeEnum>().apply {

                add(CardSlotTypeEnum.ICC1)

                add(CardSlotTypeEnum.RF)
            }

        searchInProgress = true

        val watchdog = Runnable {

            if (searchInProgress) {

                searchInProgress = false

                try {
                    cardReader.stopSearch()
                } catch (_: Exception) {
                }

                runOnUiThread {

                    statusTextView.text =
                        "Aucune carte détectée, réessayez"
                }
            }
        }

        statusTextView.postDelayed(
            watchdog,
            33_000L
        )

        cardReader.searchCard(
            slotTypes,
            30,
            object : OnCardInfoListener {

                override fun onCardInfo(
                    retCode: Int,
                    cardInfoEntity: CardInfoEntity?
                ) {

                    statusTextView.removeCallbacks(
                        watchdog
                    )

                    if (emvInProgress) {
                        return
                    }

                    try {
                        cardReader.stopSearch()
                    } catch (_: Exception) {
                    }

                    searchInProgress = false

                    if (
                        retCode == 0 &&
                        cardInfoEntity != null
                    ) {

                        val entryMode =
                            when (
                                cardInfoEntity.cardExistslot
                            ) {

                                CardSlotTypeEnum.RF ->
                                    EmvEntryModeEnum
                                        .EMV_ENTRY_MODE_CONTACTLESS

                                else ->
                                    EmvEntryModeEnum
                                        .EMV_ENTRY_MODE_CONTACT
                            }

                        Log.d(
                            TAG,
                            "Card detected - entryMode=$entryMode"
                        )

                        if (
                            entryMode ==
                            EmvEntryModeEnum
                                .EMV_ENTRY_MODE_CONTACTLESS
                        ) {

                            runOnUiThread {

                                statusTextView.text =
                                    "Carte détectée — NE RETIREZ PAS..."
                            }

                            try {
                                deviceEngine.beeper.beep(
                                    5,
                                    5
                                )
                            } catch (_: Exception) {
                            }
                        }

                        runEmvKernel(
                            amountInCents,
                            entryMode
                        )

                    } else {

                        runOnUiThread {

                            statusTextView.text =
                                "Carte illisible : $retCode"
                        }
                    }
                }

                override fun onSwipeIncorrect() {

                    statusTextView.removeCallbacks(
                        watchdog
                    )

                    searchInProgress = false

                    runOnUiThread {

                        statusTextView.text =
                            "Lecture incorrecte, réessayer"
                    }
                }

                override fun onMultipleCards() {

                    statusTextView.removeCallbacks(
                        watchdog
                    )

                    searchInProgress = false

                    runOnUiThread {

                        statusTextView.text =
                            "Plusieurs cartes détectées"
                    }
                }
            }
        )
    }

    // ================================================================
    // EMV KERNEL
    // ================================================================

    private fun runEmvKernel(
        amountInCents: String,
        entryMode: EmvEntryModeEnum
    ) {

        if (emvInProgress) {

            Log.w(
                TAG,
                "runEmvKernel ignoré : EMV déjà en cours"
            )

            return
        }

        // ------------------------------------------------------------
        // TERMINAL CAPABILITIES
        // ------------------------------------------------------------

        if (
            entryMode ==
            EmvEntryModeEnum
                .EMV_ENTRY_MODE_CONTACTLESS
        ) {

            /*
             * 9F33 = Terminal Capabilities
             */
            val result33 =
                emvHandler.setTlv(
                    ByteUtils.hexString2ByteArray(
                        "9F33"
                    ),
                    ByteUtils.hexString2ByteArray(
                        CONTACTLESS_CAPABILITIES
                    )
                )

            /*
             * 9F66 = TTQ
             *
             * This is TERMINAL configuration.
             */
            val result66 =
                emvHandler.setTlv(
                    ByteUtils.hexString2ByteArray(
                        "9F66"
                    ),
                    ByteUtils.hexString2ByteArray(
                        CONTACTLESS_TTQ
                    )
                )

            Log.d(
                TAG,
                "9F33 contactless = $CONTACTLESS_CAPABILITIES result=$result33"
            )

            Log.d(
                TAG,
                "TTQ 9F66 configuré = $CONTACTLESS_TTQ result=$result66"
            )

        } else {

            val result33 =
                emvHandler.setTlv(
                    ByteUtils.hexString2ByteArray(
                        "9F33"
                    ),
                    ByteUtils.hexString2ByteArray(
                        CONTACT_CAPABILITIES
                    )
                )

            Log.d(
                TAG,
                "9F33 contact = $CONTACT_CAPABILITIES result=$result33"
            )
        }

        // ------------------------------------------------------------
        // TRANSACTION CONFIGURATION
        // ------------------------------------------------------------

        val transParam =
            EmvTransConfigurationEntity().apply {

                transAmount = amountInCents

                emvTransType =
                    0x00.toByte()

                termId = "NEXGO001"

                merId = "MERCHANT123"

                /*
                 * Tunisia:
                 *
                 * Country numeric = 788
                 * Currency numeric = 788
                 *
                 * Nexgo uses the BCD representation here.
                 */
                countryCode = "0788"

                currencyCode = "0788"

                this.emvEntryModeEnum =
                    entryMode

                emvProcessFlowEnum =
                    EmvProcessFlowEnum
                        .EMV_PROCESS_FLOW_STANDARD
            }

        emvInProgress = true

        Log.d(TAG, "================================")
        Log.d(TAG, "START EMV TRANSACTION")
        Log.d(TAG, "Amount = $amountInCents")
        Log.d(TAG, "Entry mode = $entryMode")
        Log.d(TAG, "================================")

        emvHandler.emvProcess(
            transParam,
            object : OnEmvProcessListener2 {

                // ====================================================
                // APPLICATION SELECTION
                // ====================================================

                override fun onSelApp(
                    appNameList: MutableList<String>?,
                    candidateList:
                    MutableList<CandidateAppInfoEntity>?,
                    isMustSelect: Boolean
                ) {

                    Log.d(
                        TAG,
                        "=== onSelApp ==="
                    )

                    Log.d(
                        TAG,
                        "Candidates = ${candidateList?.size}"
                    )

                    if (!candidateList.isNullOrEmpty()) {

                        emvHandler
                            .onSetSelAppResponse(0)

                    } else {

                        emvHandler
                            .onSetSelAppResponse(-1)
                    }
                }

                // ====================================================
                // CARD NUMBER CONFIRMATION
                // ====================================================

                override fun onConfirmCardNo(
                    cardInfo: CardInfoEntity?
                ) {

                    Log.d(
                        TAG,
                        "=== onConfirmCardNo ==="
                    )

                    emvHandler
                        .onSetConfirmCardNoResponse(
                            cardInfo != null
                        )
                }

                // ====================================================
                // BEFORE GPO
                // ====================================================

                override fun onTransInitBeforeGPO() {

                    Log.d(
                        TAG,
                        "================================"
                    )

                    Log.d(
                        TAG,
                        "=== onTransInitBeforeGPO ==="
                    )

                    if (
                        entryMode ==
                        EmvEntryModeEnum
                            .EMV_ENTRY_MODE_CONTACTLESS
                    ) {

                        /*
                         * Re-apply TTQ immediately before GPO.
                         *
                         * This is correct because 9F66
                         * belongs to the terminal.
                         */
                        val result =
                            emvHandler.setTlv(
                                ByteUtils.hexString2ByteArray(
                                    "9F66"
                                ),
                                ByteUtils.hexString2ByteArray(
                                    CONTACTLESS_TTQ
                                )
                            )

                        Log.d(
                            TAG,
                            "TTQ 9F66 = $CONTACTLESS_TTQ result=$result"
                        )
                    }

                    /*
                     * IMPORTANT:
                     *
                     * Do NOT set 9F6C here.
                     *
                     * 9F6C is CTQ/card data.
                     */
                    Log.d(
                        TAG,
                        "CTQ 9F6C : aucune écriture côté terminal"
                    )

                    emvHandler
                        .onSetTransInitBeforeGPOResponse(
                            true
                        )
                }

                // ====================================================
                // PIN
                // ====================================================

                override fun onCardHolderInputPin(
                    isOnlinePin: Boolean,
                    remainingTries: Int
                ) {

                    runOnUiThread {

                        pinMaskTextView.text = ""

                        statusTextView.text =
                            "Veuillez saisir votre code PIN..."
                    }

                    val pinPad =
                        deviceEngine.getPinPad()

                    val listener =
                        object : OnPinPadInputListener {

                            override fun onInputResult(
                                result: Int,
                                pinBlock: ByteArray?
                            ) {

                                runOnUiThread {

                                    statusTextView.text =
                                        "Traitement en cours..."
                                }

                                when (result) {

                                    SdkResult.Success -> {

                                        emvHandler
                                            .onSetPinInputResponse(
                                                true,
                                                false
                                            )
                                    }

                                    else -> {

                                        emvHandler
                                            .onSetPinInputResponse(
                                                false,
                                                false
                                            )
                                    }
                                }
                            }

                            override fun onSendKey(
                                keyCode: Byte
                            ) {

                                runOnUiThread {

                                    when (
                                        keyCode.toInt()
                                    ) {

                                        0x0C,
                                        0x08 -> {

                                            if (
                                                pinMaskTextView
                                                    .text
                                                    .isNotEmpty()
                                            ) {

                                                pinMaskTextView
                                                    .text =
                                                    pinMaskTextView
                                                        .text
                                                        .dropLast(1)
                                            }
                                        }

                                        0x02 -> {

                                            pinMaskTextView
                                                .text = ""
                                        }

                                        0x00 -> {

                                            pinMaskTextView
                                                .append("*")
                                        }
                                    }

                                    try {

                                        deviceEngine.beeper
                                            .beep(
                                                10,
                                                10
                                            )

                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }

                    if (isOnlinePin) {

                        val keyIndexArray =
                            intArrayOf(1)

                        val cardData =
                            emvHandler
                                .getEmvCardDataInfo()

                        val pan =
                            cardData
                                ?.cardNo
                                ?: ""

                        /*
                         * The PIN API in your SDK expects
                         * the PAN bytes.
                         */
                        val panBytes =
                            pan.toByteArray()

                        pinPad.inputOnlinePin(
                            keyIndexArray,
                            keyIndexArray.size,
                            panBytes,
                            panBytes.size,
                            PinAlgorithmModeEnum
                                .ISO9564FMT0,
                            listener
                        )

                    } else {

                        pinPad.inputOfflinePin(
                            intArrayOf(
                                4,
                                5,
                                6
                            ),
                            60,
                            listener
                        )
                    }
                }

                // ====================================================
                // CONTACTLESS CARD AGAIN
                // ====================================================

                override fun onContactlessTapCardAgain() {

                    runOnUiThread {

                        statusTextView.text =
                            "Veuillez re-présenter la carte"
                    }

                    emvHandler
                        .onSetContactlessTapCardResponse(
                            true
                        )
                }

                // ====================================================
                // ONLINE PROCESS
                // ====================================================

                override fun onOnlineProc() {

                    Log.d(
                        TAG,
                        "================================"
                    )

                    Log.d(
                        TAG,
                        "=== onOnlineProc ==="
                    )

                    runOnUiThread {

                        statusTextView.text =
                            "Autorisation bancaire en cours..."
                    }

                    /*
                     * READ EMV DATA HERE
                     *
                     * Important:
                     * We read 9F6C BEFORE sending the online response.
                     */
                    debugEmvTags(
                        "ON_ONLINE_PROC"
                    )

                    /*
                     * Read individual values for a simple log.
                     */
                    val onlineTlvHex =
                        try {

                            emvHandler.getTlvByTags(
                                arrayOf(
                                    "9F27",
                                    "9F26",
                                    "9F10",
                                    "9F6C",
                                    "9F66",
                                    "9F34",
                                    "82",
                                    "95",
                                    "9B"
                                )
                            )

                        } catch (e: Exception) {

                            Log.e(
                                TAG,
                                "Erreur getTlvByTags dans onOnlineProc",
                                e
                            )

                            null
                        }

                    Log.d(
                        TAG,
                        "ONLINE RAW TLV = $onlineTlvHex"
                    )

                    val onlineMap =
                        parseTlvHexString(
                            onlineTlvHex
                        )

                    Log.d(
                        TAG,
                        "ONLINE CTQ 9F6C = ${
                            onlineMap["9F6C"] ?: "ABSENT"
                        }"
                    )

                    Log.d(
                        TAG,
                        "ONLINE TTQ 9F66 = ${
                            onlineMap["9F66"] ?: "ABSENT"
                        }"
                    )

                    Log.d(
                        TAG,
                        "ONLINE CID 9F27 = ${
                            onlineMap["9F27"] ?: "ABSENT"
                        }"
                    )

                    /*
                     * TEST HOST RESPONSE
                     *
                     * This is NOT a real issuer/acquirer authorization.
                     *
                     * 8A 02 30 30 = Authorization Response Code "00"
                     */
                    Thread {

                        try {

                            Thread.sleep(1500)

                        } catch (_: InterruptedException) {
                        }

                        val resultEntity =
                            EmvOnlineResultEntity().apply {

                                rejCode = "00"

                                authCode = "AUTH88"

                                recvField55 =
                                    byteArrayOf(
                                        0x8A.toByte(),
                                        0x02,
                                        0x30,
                                        0x30
                                    )
                            }

                        Log.d(
                            TAG,
                            "Sending simulated online approval"
                        )

                        emvHandler
                            .onSetOnlineProcResponse(
                                0,
                                resultEntity
                            )
                    }.start()
                }

                // ====================================================
                // PROMPT
                // ====================================================

                override fun onPrompt(
                    prompt: PromptEnum?
                ) {

                    Log.d(
                        TAG,
                        "onPrompt = $prompt"
                    )

                    emvHandler
                        .onSetPromptResponse(
                            true
                        )
                }

                // ====================================================
                // REMOVE CARD
                // ====================================================

                override fun onRemoveCard() {

                    runOnUiThread {

                        statusTextView.text =
                            "Veuillez retirer la carte"
                    }

                    emvHandler
                        .onSetRemoveCardResponse()
                }

                // ====================================================
                // FINISH
                // ====================================================

                override fun onFinish(
                    result: Int,
                    resultEntity:
                    EmvProcessResultEntity?
                ) {

                    emvInProgress = false

                    Log.d(
                        TAG,
                        "================================"
                    )

                    Log.d(
                        TAG,
                        "=== onFinish ==="
                    )

                    Log.d(
                        TAG,
                        "Result = $result"
                    )

                    /*
                     * Read everything AGAIN after EMV is finished.
                     *
                     * This lets us compare:
                     *
                     * ON_ONLINE_PROC
                     * versus
                     * ON_FINISH
                     */
                    debugEmvTags(
                        "ON_FINISH"
                    )

                    runOnUiThread {

                        val tagsToRead =
                            arrayOf(

                                // Cryptogram
                                "9F26",
                                "9F27",
                                "9F10",
                                "9F37",
                                "9F36",

                                // Verification
                                "95",
                                "9B",

                                // Terminal/currency/date
                                "9F1A",
                                "5F2A",
                                "9A",
                                "9C",
                                "9F02",

                                // Card
                                "57",
                                "5F34",
                                "4F",
                                "5F24",
                                "8E",

                                // CVM
                                "9F34",

                                // Contactless
                                "9F66",
                                "9F6C",
                                "9F6E",

                                // AIP
                                "82",

                                // Terminal capabilities
                                "9F33"
                            )

                        val rawTlvHex =
                            try {

                                emvHandler
                                    .getTlvByTags(
                                        tagsToRead
                                    )

                            } catch (e: Exception) {

                                Log.e(
                                    TAG,
                                    "Erreur lecture TLV finale",
                                    e
                                )

                                null
                            }

                        Log.d(
                            TAG,
                            "FINAL RAW TLV = $rawTlvHex"
                        )

                        val tlvMap =
                            parseTlvHexString(
                                rawTlvHex
                            )

                        Log.d(
                            TAG,
                            "================================"
                        )

                        Log.d(
                            TAG,
                            "FINAL EMV TAGS"
                        )

                        tlvMap.forEach { (tag, value) ->

                            /*
                             * Do not print sensitive PAN track data
                             * in production logs.
                             */
                            if (
                                tag == "57"
                            ) {

                                Log.d(
                                    TAG,
                                    "TAG 0x57 = [MASKED]"
                                )

                            } else {

                                Log.d(
                                    TAG,
                                    "TAG 0x$tag = $value"
                                )
                            }
                        }

                        val cidHex =
                            tlvMap["9F27"]
                                ?: ""

                        val ctqVal =
                            tlvMap["9F6C"]
                                ?: "ABSENT"

                        val ttqVal =
                            tlvMap["9F66"]
                                ?: "ABSENT"

                        val aipVal =
                            tlvMap["82"]
                                ?: "ABSENT"

                        val tvrVal =
                            tlvMap["95"]
                                ?: "ABSENT"

                        val tsiVal =
                            tlvMap["9B"]
                                ?: "ABSENT"

                        Log.d(
                            TAG,
                            "================================"
                        )

                        Log.d(
                            TAG,
                            "FIN TRANSACTION"
                        )

                        Log.d(
                            TAG,
                            "Code = $result"
                        )

                        Log.d(
                            TAG,
                            "CID  9F27 = $cidHex"
                        )

                        Log.d(
                            TAG,
                            "TTQ  9F66 = $ttqVal"
                        )

                        Log.d(
                            TAG,
                            "CTQ  9F6C = $ctqVal"
                        )

                        Log.d(
                            TAG,
                            "AIP  82   = $aipVal"
                        )

                        Log.d(
                            TAG,
                            "TVR  95   = $tvrVal"
                        )

                        Log.d(
                            TAG,
                            "TSI  9B   = $tsiVal"
                        )

                        Log.d(
                            TAG,
                            "CVM  9F34 = ${
                                tlvMap["9F34"]
                                    ?: "ABSENT"
                            }"
                        )

                        Log.d(
                            TAG,
                            "================================"
                        )

                        if (result == 0) {

                            statusTextView.text =
                                "PAIEMENT ACCEPTÉ"

                        } else {

                            statusTextView.text =
                                "TRANSACTION INTERROMPUE ($result)"
                        }
                    }
                }
            }
        )
    }

    // ================================================================
    // EMV DEBUG
    // ================================================================

    private fun debugEmvTags(
        stage: String
    ) {

        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "EMV DEBUG : $stage"
        )

        val requestedTags =
            arrayOf(
                "82",
                "95",
                "9B",
                "9F6C",
                "9F66",
                "9F34",
                "9F27",
                "9F26"
            )

        requestedTags.forEach {

            Log.d(
                TAG,
                "REQUESTED -> $it"
            )
        }

        try {

            val raw =
                emvHandler.getTlvByTags(
                    requestedTags
                )

            Log.d(
                TAG,
                "RAW RESPONSE:"
            )

            Log.d(
                TAG,
                raw ?: "NULL"
            )

            val parsed =
                parseTlvHexString(
                    raw
                )

            Log.d(
                TAG,
                "PARSED:"
            )

            requestedTags.forEach { tag ->

                val value =
                    parsed[tag]

                Log.d(
                    TAG,
                    "$tag = ${
                        value ?: "ABSENT"
                    }"
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erreur debug TLV",
                e
            )
        }

        Log.d(
            TAG,
            "================================"
        )
    }

    // ================================================================
    // TLV PARSER
    // ================================================================

    private fun parseTlvHexString(
        hex: String?
    ): Map<String, String> {

        val result =
            LinkedHashMap<String, String>()

        if (
            hex.isNullOrBlank()
        ) {
            return result
        }

        val cleanHex =
            hex
                .replace(
                    Regex("\\s+"),
                    ""
                )
                .uppercase()

        if (
            cleanHex.isEmpty() ||
            cleanHex.length % 2 != 0
        ) {

            Log.w(
                TAG,
                "TLV hex invalide : $hex"
            )

            return result
        }

        val bytes =
            try {

                ByteUtils.hexString2ByteArray(
                    cleanHex
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Impossible de convertir le TLV",
                    e
                )

                return result
            }

        var i = 0

        while (
            i < bytes.size
        ) {

            // --------------------------------------------------------
            // TAG
            // --------------------------------------------------------

            val tagStart = i

            val firstByte =
                bytes[i]
                    .toInt() and 0xFF

            i++

            /*
             * Multi-byte tag:
             *
             * Example:
             * 9F26
             * 9F6C
             * 5F34
             */
            if (
                firstByte and 0x1F == 0x1F
            ) {

                while (
                    i < bytes.size &&
                    (
                            bytes[i]
                                .toInt() and 0x80
                            ) != 0
                ) {

                    i++
                }

                if (
                    i < bytes.size
                ) {

                    i++
                }
            }

            if (
                i > bytes.size
            ) {
                break
            }

            val tagBytes =
                bytes.copyOfRange(
                    tagStart,
                    i
                )

            val tagHex =
                ByteUtils
                    .byteArray2HexString(
                        tagBytes
                    )
                    .uppercase()

            // --------------------------------------------------------
            // LENGTH
            // --------------------------------------------------------

            if (
                i >= bytes.size
            ) {
                break
            }

            val lenByte =
                bytes[i]
                    .toInt() and 0xFF

            i++

            val length: Int

            if (
                lenByte and 0x80 != 0
            ) {

                val numLenBytes =
                    lenByte and 0x7F

                /*
                 * Invalid BER length.
                 */
                if (
                    numLenBytes == 0 ||
                    numLenBytes > 4
                ) {

                    Log.w(
                        TAG,
                        "Longueur TLV invalide pour tag=$tagHex"
                    )

                    break
                }

                var tempLength = 0

                repeat(
                    numLenBytes
                ) {

                    if (
                        i >= bytes.size
                    ) {
                        return result
                    }

                    tempLength =
                        (
                                tempLength shl 8
                                ) or (
                                bytes[i]
                                    .toInt() and 0xFF
                                )

                    i++
                }

                length = tempLength

            } else {

                length = lenByte
            }

            // --------------------------------------------------------
            // VALUE
            // --------------------------------------------------------

            if (
                length < 0 ||
                i + length > bytes.size
            ) {

                Log.w(
                    TAG,
                    "Longueur TLV invalide : tag=$tagHex length=$length"
                )

                break
            }

            val valueBytes =
                bytes.copyOfRange(
                    i,
                    i + length
                )

            val valueHex =
                ByteUtils
                    .byteArray2HexString(
                        valueBytes
                    )
                    .uppercase()

            result[tagHex] =
                valueHex

            i += length
        }

        return result
    }

    // ================================================================
    // ACTIVITY CLEANUP
    // ================================================================

    override fun onDestroy() {

        try {

            deviceEngine
                .getCardReader()
                .stopSearch()

        } catch (_: Exception) {
        }

        try {

            if (emvInProgress) {

                emvHandler
                    .emvProcessCancel()
            }

        } catch (_: Exception) {
        }

        emvInProgress = false
        searchInProgress = false

        super.onDestroy()
    }
}
