package com.example.terminalmodule.internal

import android.util.Log
import com.example.terminalmodule.api.EmvOnlineAuthorizer
import com.example.terminalmodule.api.EmvPaymentListener
import com.example.terminalmodule.api.EmvPaymentService
import com.example.terminalmodule.model.EmvEntryMode
import com.example.terminalmodule.model.EmvOnlineRequest
import com.example.terminalmodule.model.EmvPaymentRequest
import com.example.terminalmodule.model.EmvPaymentResult
import com.nexgo.libpboc.ByteUtils
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener
import com.nexgo.oaf.apiv3.emv.CandidateAppInfoEntity
import com.nexgo.oaf.apiv3.emv.EmvEntryModeEnum
import com.nexgo.oaf.apiv3.emv.EmvHandler2
import com.nexgo.oaf.apiv3.emv.EmvOnlineResultEntity
import com.nexgo.oaf.apiv3.emv.EmvProcessFlowEnum
import com.nexgo.oaf.apiv3.emv.EmvProcessResultEntity
import com.nexgo.oaf.apiv3.emv.EmvTransConfigurationEntity
import com.nexgo.oaf.apiv3.emv.OnEmvProcessListener2
import com.nexgo.oaf.apiv3.emv.PromptEnum
import java.util.HashSet
import java.util.LinkedHashMap

internal class NexgoEmvPaymentService(
    private val deviceEngine: DeviceEngine,
    private val emvHandler: EmvHandler2,
    private val configurator: NexgoEmvConfigurator
) : EmvPaymentService {

    companion object {
        private const val TAG = "EMV_PAYMENT"
        private const val SEARCH_TIMEOUT = 30

        private val ONLINE_TAGS = arrayOf(
            "9F26", "9F27", "9F10", "9F37", "9F36", "95", "9A", "9C", "9F02", "9F03",
            "5F2A", "82", "9F1A", "9F33", "9F34", "9F35", "57", "5F34", "4F", "84", "9F6E"
        )

        private val RESULT_TAGS = arrayOf(
            "9F26", "9F27", "9F10", "9F37", "9F36", "95", "9B", "9F1A", "5F2A", "9A", "9C",
            "9F02", "9F03", "57", "5A", "5F34", "5F24", "9F0F", "8F", "4F", "84", "8E",
            "9F34", "9F35", "9F66", "9F6C", "82", "9F33", "9F6E", "9F3E", "9F0A",
            "9F1E", "9F21", "9F08", "9F07","56", "9F15", "9F1C", "9F09", "9F0D", "9F0E", "5F25", "5F28"
        )
    }

    @Volatile private var searching = false
    @Volatile private var running = false
    @Volatile private var searchId = 0

    override fun start(
        request: EmvPaymentRequest,
        authorizer: EmvOnlineAuthorizer,
        listener: EmvPaymentListener
    ) {
        Log.w(TAG, "start() appelé : searching=$searching running=$running searchId=$searchId")

        if (searching || running) {
            Log.w(TAG, "start() REFUSÉ : transaction déjà en cours (searching=$searching running=$running)")
            listener.onError("Transaction déjà en cours")
            return
        }
        val amount = request.amountInCents.toLongOrNull()
        if (amount == null || amount <= 0L) {
            listener.onError("Montant invalide : ${request.amountInCents}")
            return
        }

        val cardReader = deviceEngine.getCardReader()

        // Invalide toute recherche précédente avant d'en lancer une nouvelle
        val myId = ++searchId
        try { cardReader.stopSearch() } catch (_: Exception) {}

        val slots = HashSet<CardSlotTypeEnum>().apply {
            add(CardSlotTypeEnum.ICC1)
            add(CardSlotTypeEnum.RF)
        }

        searching = true
        listener.onWaitingForCard()

        try {
            Log.w(TAG, "searchCard lancé (id=$myId, timeout=${SEARCH_TIMEOUT}s)")

            cardReader.searchCard(slots, SEARCH_TIMEOUT, object : OnCardInfoListener {

                override fun onCardInfo(retCode: Int, cardInfoEntity: CardInfoEntity?) {
                    Log.w(
                        TAG,
                        "onCardInfo : id=$myId courant=$searchId retCode=$retCode " +
                                "searching=$searching slot=${cardInfoEntity?.cardExistslot}"
                    )

                    // Callback d'une ancienne recherche, ou recherche déjà terminée : on l'ignore
                    if (myId != searchId || !searching) {
                        Log.w(TAG, "Callback de recherche périmé ignoré (code=$retCode)")
                        return
                    }
                    searching = false
                    try { cardReader.stopSearch() } catch (_: Exception) {}

                    if (retCode != 0 || cardInfoEntity == null) {
                        Log.w(TAG, "Erreur lecture carte : retCode=$retCode")
                        listener.onError("Erreur lecture carte : $retCode")
                        return
                    }

                    val contactless = cardInfoEntity.cardExistslot == CardSlotTypeEnum.RF
                    val entry = if (contactless) EmvEntryMode.CONTACTLESS else EmvEntryMode.CONTACT
                    listener.onCardDetected(entry)

                    if (contactless) {
                        try { deviceEngine.beeper.beep(5, 5) } catch (_: Exception) {}
                    }
                    runEmv(request, entry, authorizer, listener)
                }

                override fun onSwipeIncorrect() {
                    Log.w(TAG, "onSwipeIncorrect : id=$myId courant=$searchId")
                    if (myId != searchId) return
                    searching = false
                    listener.onError("Lecture magnétique incorrecte")
                }

                override fun onMultipleCards() {
                    Log.w(TAG, "onMultipleCards : id=$myId courant=$searchId")
                    if (myId != searchId) return
                    searching = false
                    listener.onError("Plusieurs cartes détectées")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "searchCard a levé une exception", e)
            searching = false
            listener.onError("Erreur recherche carte : ${e.message}")
        }
    }

    private fun terminalSerial(): String? =
        try {
            deviceEngine.deviceInfo.sn
        } catch (e: Throwable) {
            Log.w(TAG, "Numéro de série terminal indisponible", e)
            null
        }

    private fun runEmv(
        request: EmvPaymentRequest,
        entry: EmvEntryMode,
        authorizer: EmvOnlineAuthorizer,
        listener: EmvPaymentListener
    ) {
        Log.w(TAG, "runEmv démarré : mode=$entry")
        val contactless = entry == EmvEntryMode.CONTACTLESS

        // 9F33 / 9F66 viennent des paramètres terminal chargés depuis le SDK Worldline.
        // 9F1E / 9F35 / 9F21 sont générés ici, à chaque transaction.
        configurator.applyTerminalTlv(contactless, terminalSerial())

        val transParam = EmvTransConfigurationEntity().apply {
            transAmount = request.amountInCents
            emvTransType = request.transactionType.toByte()
            termId = request.terminalId
            merId = request.merchantId
            countryCode = request.countryCode
            currencyCode = request.currencyCode
            this.emvEntryModeEnum =
                if (contactless) EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACTLESS
                else EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACT
            emvProcessFlowEnum = EmvProcessFlowEnum.EMV_PROCESS_FLOW_STANDARD
        }

        running = true

        emvHandler.emvProcess(transParam, object : OnEmvProcessListener2 {

            override fun onSelApp(
                appNameList: MutableList<String>?,
                candidateList: MutableList<CandidateAppInfoEntity>?,
                isMustSelect: Boolean
            ) {
                Log.d(TAG, "onSelApp : ${candidateList?.size ?: 0} candidat(s)")
                candidateList?.forEachIndexed { i, c ->
                    Log.d(TAG, "[$i] AID=${c.aid} label=${c.appLabel} prio=${c.priority}")
                }
                // Sélection automatique de la première application
                emvHandler.onSetSelAppResponse(if (candidateList.isNullOrEmpty()) -1 else 0)
            }

            override fun onConfirmCardNo(cardInfo: CardInfoEntity?) {
                emvHandler.onSetConfirmCardNoResponse(cardInfo != null)
            }

            override fun onTransInitBeforeGPO() {
                configurator.applyTerminalTlv(contactless, terminalSerial())
                emvHandler.onSetTransInitBeforeGPOResponse(true)
            }

            override fun onCardHolderInputPin(isOnlinePin: Boolean, remainingTries: Int) {
                // TODO : brancher le service PIN pad de terminalmodule
                emvHandler.onSetPinInputResponse(true, false)
            }

            override fun onContactlessTapCardAgain() {
                emvHandler.onSetContactlessTapCardResponse(true)
            }

            override fun onOnlineProc() {
                val tags = enrichTags(readTags(ONLINE_TAGS))
                Log.d(TAG, "onOnlineProc : ${tags.size} tags envoyés à l'autorisation")

                val response = try {
                    authorizer.authorize(EmvOnlineRequest(entry, tags))
                } catch (e: Throwable) {
                    Log.e(TAG, "Autorisation en ligne échouée", e)
                    null
                }

                if (response == null) {
                    // Échec de communication : code non nul (valeur exacte à confirmer dans le SDK)
                    emvHandler.onSetOnlineProcResponse(-1, EmvOnlineResultEntity())
                    return
                }

                val field55 = response.field55Hex
                    ?: if (response.approved) "8A023030" else "8A023035"

                val entity = EmvOnlineResultEntity().apply {
                    rejCode = if (response.approved) "00" else response.rejectCode
                    authCode = response.authCode ?: ""
                    recvField55 = ByteUtils.hexString2ByteArray(field55)
                }
                emvHandler.onSetOnlineProcResponse(0, entity)
            }

            override fun onPrompt(prompt: PromptEnum?) {
                emvHandler.onSetPromptResponse(true)
            }

            override fun onRemoveCard() {
                emvHandler.onSetRemoveCardResponse()
            }

            override fun onFinish(result: Int, resultEntity: EmvProcessResultEntity?) {
                Log.i("WORLDLINE_EMV", "CB onFinish result=$result entity=$resultEntity")
                running = false
                val tags = enrichTags(readTags(RESULT_TAGS))

                // Ne jamais logger le Track 2 ni le PAN en clair (PCI)
                tags.forEach { (t, v) ->
                    Log.d(TAG, "TAG $t = ${if (t == "57" || t == "5A") "***" else v}")
                }
                listener.onFinished(EmvPaymentResult(result, result == 0, tags))
            }
        })
    }

    override fun cancel() {
        Log.w(TAG, "cancel() appelé : searching=$searching running=$running")
        searchId++
        try { deviceEngine.getCardReader().stopSearch() } catch (_: Exception) {}
        try { if (running) emvHandler.emvProcessCancel() } catch (_: Exception) {}
        searching = false
        running = false
    }

    override fun destroy() = cancel()

    // ---------------- Lecture des tags ----------------

    private fun readTags(requested: Array<String>): Map<String, String> =
        try {
            parseTlv(emvHandler.getTlvByTags(requested))
        } catch (e: Throwable) {
            Log.w(TAG, "Lecture des tags impossible", e)
            emptyMap()
        }

    /**
     * Complète les tags que le kernel ne renvoie pas :
     *  - 9F1E / 9F35 / 9F21 : valeurs injectées pour cette transaction
     *  - 5A / 5F24 : dérivés du Track 2 (57)
     *  - 84 : repris du 4F (AID sélectionné)
     * Les valeurs renvoyées par le kernel ne sont jamais écrasées.
     */
    private fun enrichTags(source: Map<String, String>): MutableMap<String, String> {
        val tags = LinkedHashMap(source)

        configurator.injectedTags.forEach { (t, v) ->
            if (!tags.containsKey(t)) {
                tags[t] = v
                Log.d(TAG, "Tag $t absent du kernel : valeur injectée utilisée")
            }
        }

        // Track 2 : PAN 'D' YYMM ...
        val track2 = tags["57"]?.uppercase()
        if (track2 != null && 'D' in track2) {
            val pan = track2.substringBefore('D').trimEnd('F')
            val expiry = track2.substringAfter('D').take(4) // YYMM
            if (pan.isNotEmpty()) tags.putIfAbsent("5A", pan)
            // Le jour n'est pas dans le Track 2 : "31" est un repli, pas une vraie date
            if (expiry.length == 4) tags.putIfAbsent("5F24", expiry + "31")
        }

        tags["4F"]?.let { tags.putIfAbsent("84", it) }

        return tags
    }

    private fun parseTlv(hex: String?): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        if (hex.isNullOrBlank()) return result
        val bytes = ByteUtils.hexString2ByteArray(hex.replace(Regex("\\s+"), "").uppercase())
        var i = 0
        while (i < bytes.size) {
            val tagStart = i
            val first = bytes[i].toInt() and 0xFF
            i++
            if (first and 0x1F == 0x1F) {
                while (i < bytes.size && (bytes[i].toInt() and 0x80) != 0) i++
                if (i < bytes.size) i++
            }
            if (i >= bytes.size) break
            val tag = ByteUtils.byteArray2HexString(bytes.copyOfRange(tagStart, i)).uppercase()
            val lenByte = bytes[i].toInt() and 0xFF
            i++
            val length = if (lenByte and 0x80 != 0) {
                var len = 0
                repeat(lenByte and 0x7F) { len = (len shl 8) or (bytes[i++].toInt() and 0xFF) }
                len
            } else lenByte
            if (length < 0 || i + length > bytes.size) break
            result[tag] = ByteUtils.byteArray2HexString(bytes.copyOfRange(i, i + length)).uppercase()
            i += length
        }
        return result
    }
}