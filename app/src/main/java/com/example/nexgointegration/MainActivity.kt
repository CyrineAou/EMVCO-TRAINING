//package com.example.nexgointegration
//
//import android.os.Bundle
//import android.util.Log
//import android.widget.TextView
//import androidx.activity.ComponentActivity
//
//import com.nexgo.common.LogUtils
//import com.nexgo.libpboc.ByteUtils
//import com.nexgo.oaf.apiv3.APIProxy
//import com.nexgo.oaf.apiv3.DeviceEngine
//import com.nexgo.oaf.apiv3.device.pinpad.WorkKeyTypeEnum
//import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
//import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
//import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener
//import com.nexgo.oaf.apiv3.emv.*
//
//import org.json.JSONArray
//
//import java.io.InputStream
//import java.nio.charset.Charset
//import java.util.HashSet
//import java.util.LinkedHashMap
//
//class MainActivity : ComponentActivity() {
//
//    companion object {
//        private const val TAG = "APDU_CLEAN"
//        private const val CONTACTLESS_TTQ = "26C0C000"
//        private const val CONTACT_CAPABILITIES = "6060C8"
//        private const val CONTACTLESS_CAPABILITIES = "E080C8"
//        private const val VISA_AID = "A0000000031010"
//        private const val MASTERCARD_AID = "A0000000041010"
//    }
//
//    private lateinit var deviceEngine: DeviceEngine
//    private lateinit var emvHandler: EmvHandler2
//
//    private lateinit var statusTextView: TextView
//    private lateinit var pinMaskTextView: TextView
//
//    @Volatile
//    private var emvInProgress = false
//
//    @Volatile
//    private var searchInProgress = false
//
//    private val loadedCapkKeys = mutableSetOf<Pair<String, Int>>()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        statusTextView = findViewById(R.id.statusTextView)
//        pinMaskTextView = findViewById(R.id.pinTextView)
//
//        deviceEngine = APIProxy.getDeviceEngine(this)
//        emvHandler = deviceEngine.getEmvHandler2("Main-Kernel2")
//
//        emvHandler.emvDebugLog(true)
//        LogUtils.setDebugEnable(true)
//
//        initPinPadKeys()
//        configureAids()
//        injectCapkFromJson()
//
//        statusTextView.post {
//            startBancaireWorkflow("10000")
//        }
//    }
//
//    private fun initPinPadKeys() {
//        try {
//            val pinPad = deviceEngine.getPinPad()
//            val masterKey = ByteUtils.hexString2ByteArray("111111111111111111111111111111")
//            val workerKey = ByteUtils.hexString2ByteArray("211111111111111111111111111111")
//            pinPad.writeMKey(0, masterKey, masterKey.size)
//            pinPad.writeWKey(0, WorkKeyTypeEnum.PINKEY, workerKey, workerKey.size)
//        } catch (e: Exception) {
//            Log.e(TAG, "Erreur initialisation Master Key", e)
//        }
//    }
//
//    private fun configureAids() {
//        try {
//            val aidList = ArrayList<AidEntity>()
//
//            // VISA configuré pour autoriser l'offline (TAC à 0)
//            val visaAid = AidEntity().apply {
//                aid = VISA_AID
//                asi = 0
//                floorLimit = 50000L
//                threshold = 0L
//                tacDenial = "0000000000"
//                tacOnline = "DC4000A800"
//                tacDefault = "DC4000A800"
//                appVerNum = "0096"
//                contactlessFloorLimit = 50000L
//                contactlessCvmLimit = 50000L
//                contactlessTransLimit = 999999L
//            }
//            aidList.add(visaAid)
//
//            // MASTERCARD configuré
//            val mastercardAid = AidEntity().apply {
//                aid = MASTERCARD_AID
//                asi = 0
//                floorLimit = 50000L
//                threshold = 0L
//                tacDenial = "0000000000"
//                tacOnline = "DC4000A800"
//                tacDefault = "DC4000A800"
//                appVerNum = "0033"
//                contactlessFloorLimit = 50000L
//                contactlessCvmLimit = 50000L
//                contactlessTransLimit = 999999L
//            }
//            aidList.add(mastercardAid)
//
//            emvHandler.delAllAid()
//            emvHandler.setAidParaList(aidList)
//        } catch (e: Exception) {
//            Log.e(TAG, "Erreur configuration AID", e)
//        }
//    }
//
//    private fun injectCapkFromJson() {
//        try {
//            val inputStream: InputStream = assets.open("emv_capk.json")
//            val size = inputStream.available()
//            val buffer = ByteArray(size)
//            inputStream.read(buffer)
//            inputStream.close()
//
//            val jsonString = String(buffer, Charset.forName("UTF-8"))
//            val jsonArray = JSONArray(jsonString)
//
//            emvHandler.delAllCapk()
//            loadedCapkKeys.clear()
//
//            for (i in 0 until jsonArray.length()) {
//                val jsonObject = jsonArray.getJSONObject(i)
//                var rawExpire = jsonObject.getString("expireDate").trim()
//                if (rawExpire.length == 8) rawExpire = rawExpire.substring(2)
//
//                val capk = CapkEntity().apply {
//                    rid = jsonObject.getString("rid").replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
//                    capkIdx = jsonObject.getInt("capkIdx")
//                    hashInd = jsonObject.getInt("hashInd")
//                    arithInd = jsonObject.getInt("arithInd")
//                    modulus = jsonObject.getString("modulus").replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
//                    exponent = jsonObject.getString("exponent").replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
//                    expireDate = rawExpire
//                    checkSum = jsonObject.getString("checkSum").replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
//                }
//                emvHandler.setCAPKList(arrayListOf(capk))
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Erreur CAPK", e)
//        }
//    }
//
//    private fun startBancaireWorkflow(amountInCents: String) {
//        if (searchInProgress || emvInProgress) return
//
//        runOnUiThread { statusTextView.text = "Veuillez approcher votre carte..." }
//
//        val cardReader = deviceEngine.getCardReader()
//        try { cardReader.stopSearch() } catch (_: Exception) {}
//
//        val slotTypes = HashSet<CardSlotTypeEnum>().apply {
//            add(CardSlotTypeEnum.ICC1)
//            add(CardSlotTypeEnum.RF)
//        }
//
//        searchInProgress = true
//        cardReader.searchCard(slotTypes, 30, object : OnCardInfoListener {
//            override fun onCardInfo(retCode: Int, cardInfoEntity: CardInfoEntity?) {
//                if (emvInProgress) return
//                try { cardReader.stopSearch() } catch (_: Exception) {}
//                searchInProgress = false
//
//                if (retCode == 0 && cardInfoEntity != null) {
//                    val entryMode = if (cardInfoEntity.cardExistslot == CardSlotTypeEnum.RF)
//                        EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACTLESS
//                    else
//                        EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACT
//
//                    if (entryMode == EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACTLESS) {
//                        try { deviceEngine.beeper.beep(5, 5) } catch (_: Exception) {}
//                    }
//                    runEmvKernel(amountInCents, entryMode)
//                }
//            }
//            override fun onSwipeIncorrect() { searchInProgress = false }
//            override fun onMultipleCards() { searchInProgress = false }
//        })
//    }
//
//    private fun runEmvKernel(amountInCents: String, entryMode: EmvEntryModeEnum) {
//        if (emvInProgress) return
//
//        if (entryMode == EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACTLESS) {
//            emvHandler.setTlv(ByteUtils.hexString2ByteArray("9F33"), ByteUtils.hexString2ByteArray(CONTACTLESS_CAPABILITIES))
//            emvHandler.setTlv(ByteUtils.hexString2ByteArray("9F66"), ByteUtils.hexString2ByteArray(CONTACTLESS_TTQ))
//        }
//
//        val transParam = EmvTransConfigurationEntity().apply {
//            transAmount = amountInCents
//            emvTransType = 0x00.toByte()
//            termId = "NEXGO001"
//            merId = "MERCHANT123"
//            countryCode = "0788"
//            currencyCode = "0788"
//            this.emvEntryModeEnum = entryMode
//            emvProcessFlowEnum = EmvProcessFlowEnum.EMV_PROCESS_FLOW_STANDARD
//        }
//
//        emvInProgress = true
//
//        emvHandler.emvProcess(transParam, object : OnEmvProcessListener2 {
//            override fun onSelApp(
//                appNameList: MutableList<String>?,
//                candidateList: MutableList<CandidateAppInfoEntity>?,
//                isMustSelect: Boolean
//            ) {
//                val nbCandidates = candidateList?.size ?: 0
//                Log.d("APDU_CLEAN", "=== onSelApp : isMustSelect=$isMustSelect | nb candidats=$nbCandidates ===")
//
//                val displayText = StringBuilder("Applications trouvées ($nbCandidates) :\n")
//                candidateList?.forEachIndexed { index, candidate ->
//                    val aidDecoded = candidate.aid ?: "N/A"
//                    val label = candidate.appLabel ?: "Sans nom"
//                    Log.d("APDU_CLEAN", "[$index] AID=$aidDecoded | Label=$label | Priorité=${candidate.priority}")
//                    displayText.append("[$index] $label (${candidate.priority})\n     AID: $aidDecoded\n")
//                }
//
//                runOnUiThread { statusTextView.text = displayText.toString() }
//
//                // Un seul appel de réponse propre :
//                if (!candidateList.isNullOrEmpty()) {
//                    emvHandler.onSetSelAppResponse(0)
//                } else {
//                    emvHandler.onSetSelAppResponse(-1)
//                }
//            }
//
//            override fun onConfirmCardNo(cardInfo: CardInfoEntity?) {
//                emvHandler.onSetConfirmCardNoResponse(cardInfo != null)
//            }
//
//            override fun onTransInitBeforeGPO() {
//                if (entryMode == EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACTLESS) {
//                    emvHandler.setTlv(ByteUtils.hexString2ByteArray("9F66"), ByteUtils.hexString2ByteArray(CONTACTLESS_TTQ))
//                }
//                emvHandler.onSetTransInitBeforeGPOResponse(true)
//            }
//
//            override fun onCardHolderInputPin(isOnlinePin: Boolean, remainingTries: Int) {
//                // Gestion PIN si nécessaire
//                emvHandler.onSetPinInputResponse(true, false)
//            }
//
//            override fun onContactlessTapCardAgain() {
//                emvHandler.onSetContactlessTapCardResponse(true)
//            }
//
//            override fun onOnlineProc() {
//                // Ne devrait plus être appelé si l'offline est totalement autorisé par les TAC
//                Log.d(TAG, "=== onOnlineProc (appelé par défaut si la carte force l'online) ===")
//                val resultEntity = EmvOnlineResultEntity().apply {
//                    rejCode = "00"
//                    authCode = "AUTH88"
//                    recvField55 = byteArrayOf(0x8A.toByte(), 0x02, 0x30, 0x30)
//                }
//                emvHandler.onSetOnlineProcResponse(0, resultEntity)
//            }
//
//            override fun onPrompt(prompt: PromptEnum?) {
//                emvHandler.onSetPromptResponse(true)
//            }
//
//            override fun onRemoveCard() {
//                emvHandler.onSetRemoveCardResponse()
//            }
//
//            override fun onFinish(result: Int, resultEntity: EmvProcessResultEntity?) {
//                emvInProgress = false
//                Log.d(TAG, "=== onFinish Result = $result ===")
//                debugEmvTags("ON_FINISH")
//
//                runOnUiThread {
//                    statusTextView.text = if (result == 0) "PAIEMENT ACCEPTÉ (OFFLINE)" else "INTERROMPU"
//                }
//            }
//        })
//    }
//
//    private fun debugEmvTags(stage: String) {
//        val requestedTags  = arrayOf(
//            "9F26", "9F27", "9F10", "9F37", "9F36",
//            "95", "9B", "9F1A", "5F2A", "9A", "9C",
//            "9F02", "57", "5F34", "9F0F", "8F", "4F", "5F24", "8E",
//            "9F34", "9F66", "9F6C", "82", "9F33", "9F6E",
//            "9F10","9F3E", "9F0A", "BF0C"
//
//        )
//        try {
//            val raw = emvHandler.getTlvByTags(requestedTags)
//            val parsed = parseTlvHexString(raw)
//            requestedTags.forEach { tag ->
//                Log.d(TAG, "DEBUG [$stage] $tag = ${parsed[tag] ?: "ABSENT"}")
//            }
//        } catch (_: Exception) {}
//    }
//
//    private fun parseTlvHexString(hex: String?): Map<String, String> {
//        val result = LinkedHashMap<String, String>()
//        if (hex.isNullOrBlank()) return result
//        val bytes = ByteUtils.hexString2ByteArray(hex.replace(Regex("\\s+"), "").uppercase())
//        var i = 0
//        while (i < bytes.size) {
//            val tagStart = i
//            val firstByte = bytes[i].toInt() and 0xFF
//            i++
//            if (firstByte and 0x1F == 0x1F) {
//                while (i < bytes.size && (bytes[i].toInt() and 0x80) != 0) i++
//                if (i < bytes.size) i++
//            }
//            if (i > bytes.size) break
//            val tagHex = ByteUtils.byteArray2HexString(bytes.copyOfRange(tagStart, i)).uppercase()
//            if (i >= bytes.size) break
//            val lenByte = bytes[i].toInt() and 0xFF
//            i++
//            val length = if (lenByte and 0x80 != 0) {
//                var len = 0
//                repeat(lenByte and 0x7F) { len = (len shl 8) or (bytes[i++].toInt() and 0xFF) }
//                len
//            } else lenByte
//            if (length < 0 || i + length > bytes.size) break
//            result[tagHex] = ByteUtils.byteArray2HexString(bytes.copyOfRange(i, i + length)).uppercase()
//            i += length
//        }
//        return result
//    }
//
//    override fun onDestroy() {
//        try { deviceEngine.getCardReader().stopSearch() } catch (_: Exception) {}
//        try { if (emvInProgress) emvHandler.emvProcessCancel() } catch (_: Exception) {}
//        super.onDestroy()
//    }
//
//    // Cette fonction prend juste les codes pays sous forme de texte et dit true ou false
//    fun isDomesticTransaction(issuerCountryCode: String?, terminalCountryCode: String?): Boolean {
//        return issuerCountryCode != null && terminalCountryCode != null && issuerCountryCode == terminalCountryCode
//    }
//}
//
package com.example.nexgointegration

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.nexgointegration.gateway.FakePaymentGateway
import com.example.nexgointegration.gateway.PaymentGatewayImpl
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.emv.EmvProcessResultEntity

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "EMV_TRANSACTION"
    }

    private lateinit var paymentManager: PaymentManager

    private lateinit var statusTextView: TextView
    private lateinit var amountTextView: TextView
    private lateinit var startButton: Button
    private lateinit var cancelButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        log("==================================================")
        log("APPLICATION START")
        log("==================================================")

        statusTextView = findViewById(R.id.statusTextView)
        amountTextView = findViewById(R.id.amountTextView)
        startButton = findViewById(R.id.startPaymentButton)
        cancelButton = findViewById(R.id.cancelPaymentButton)

        log("UI initialized")

        val gateway = FakePaymentGateway()
        paymentManager = PaymentManager(
            context = this,
            paymentGateway = gateway,
            listener = object : PaymentManager.Listener {

                override fun onWaitingForCard() {

                    log("--------------------------------------------------")
                    log("[CARD] Waiting for card")
                    log("--------------------------------------------------")

                    runOnUiThread {

                        statusTextView.text =
                            "Veuillez insérer ou présenter votre carte..."

                        startButton.isEnabled = false
                        cancelButton.isEnabled = true
                    }
                }

                override fun onCardDetected(
                    cardInfo: CardInfoEntity
                ) {

                    log("--------------------------------------------------")
                    log("[CARD] CARD DETECTED")
                    log("[CARD] Slot = ${cardInfo.cardExistslot}")
                    log("--------------------------------------------------")

                    runOnUiThread {

                        statusTextView.text =
                            "Carte détectée..."
                    }
                }

                override fun onTransactionFinished(
                    result: Int,
                    resultEntity: EmvProcessResultEntity?
                ) {

                    log("==================================================")
                    log("[FINISH] EMV TRANSACTION FINISHED")
                    log("[FINISH] Result code = $result")

                    log("[FINISH] Result entity = ${resultEntity != null}")

                    if (result == 0) {

                        log("[FINISH] STATUS = SUCCESS")
                        log("[FINISH] TRANSACTION ACCEPTED")

                    } else {

                        log("[FINISH] STATUS = FAILED")
                        log("[FINISH] TRANSACTION REJECTED")
                    }

                    log("==================================================")

                    /*
                     * IMPORTANT :
                     * onTransactionFinished() peut être appelé
                     * depuis EmvThread2.
                     *
                     * Toutes les modifications UI doivent donc
                     * être effectuées sur le Main Thread.
                     */

                    runOnUiThread {

                        startButton.isEnabled = true
                        cancelButton.isEnabled = false

                        if (result == 0) {

                            statusTextView.text =
                                "Transaction terminée"

                        } else {

                            statusTextView.text =
                                "Transaction échouée : $result"
                        }
                    }
                }

                override fun onError(
                    message: String
                ) {

                    log("==================================================")
                    log("[ERROR] EMV ERROR")
                    log("[ERROR] $message")
                    log("==================================================")

                    runOnUiThread {

                        startButton.isEnabled = true
                        cancelButton.isEnabled = false

                        statusTextView.text =
                            "Erreur : $message"
                    }
                }
            }
        )

        log("[INIT] PaymentManager initialized")

//        startButton.setOnClickListener {
//
//            log("==================================================")
//            log("[START] PAYMENT BUTTON CLICKED")
//            log("==================================================")
//
//            val amount = amountTextView.text
//                .toString()
//                .trim()
//
//            log("[START] Amount entered = '$amount'")
//
//            if (amount.isEmpty()) {
//
//                log("[START] ERROR: Amount is empty")
//
//                updateStatus(
//                    "Veuillez saisir un montant"
//                )
//
//                return@setOnClickListener
//            }
//
//            log("[START] Starting payment")
//            log("[START] Amount = $amount")
//
//            startButton.isEnabled = false
//            cancelButton.isEnabled = true
//
//            try {
//
//                paymentManager.startPayment(amount)
//
//                log("[START] paymentManager.startPayment() called")
//
//            } catch (e: Exception) {
//
//                Log.e(
//                    TAG,
//                    "[START] Exception while starting payment",
//                    e
//                )
//
//                startButton.isEnabled = true
//                cancelButton.isEnabled = false
//
//                updateStatus(
//                    "Erreur démarrage : ${e.message}"
//                )
//            }
//        }

        startButton.setOnClickListener {

            val amount = amountTextView.text
                .toString()
                .trim()

            if (amount.isEmpty()) {
                updateStatus("Veuillez saisir un montant")
                return@setOnClickListener
            }

            startButton.isEnabled = false
            cancelButton.isEnabled = true

            try {
                paymentManager.startPayment(amount)
            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Erreur démarrage paiement",
                    e
                )

                startButton.isEnabled = true
                cancelButton.isEnabled = false

                updateStatus(
                    "Erreur démarrage : ${e.message}"
                )
            }
        }

        cancelButton.setOnClickListener {

            log("==================================================")
            log("[CANCEL] CANCEL BUTTON CLICKED")
            log("==================================================")

            try {

                paymentManager.cancel()

                log("[CANCEL] Payment cancellation requested")

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "[CANCEL] Exception during cancellation",
                    e
                )
            }

            runOnUiThread {

                startButton.isEnabled = true
                cancelButton.isEnabled = false

                statusTextView.text =
                    "Transaction annulée"
            }
        }

        runOnUiThread {

            statusTextView.text =
                "Terminal prêt"

            startButton.isEnabled = true
            cancelButton.isEnabled = false
        }

        log("[INIT] Terminal ready")
    }

    /**
     * Log simple et lisible.
     */
    private fun log(message: String) {

        Log.d(
            TAG,
            message
        )
    }

    /**
     * Mise à jour sécurisée de l'interface.
     *
     * Cette méthode peut être appelée depuis n'importe quel thread.
     */
    private fun updateStatus(
        message: String
    ) {

        runOnUiThread {

            statusTextView.text = message
        }
    }

    override fun onDestroy() {

        log("==================================================")
        log("[LIFECYCLE] onDestroy()")
        log("==================================================")

        try {

            paymentManager.destroy()

            log("[LIFECYCLE] PaymentManager destroyed")

        } catch (e: Exception) {

            Log.e(
                TAG,
                "[LIFECYCLE] Error destroying PaymentManager",
                e
            )
        }

        super.onDestroy()
    }
}