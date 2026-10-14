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
            "5F2A", "82", "9F1A", "9F33", "9F34", "57", "5F34", "4F", "84", "9F6E"
        )
        private val RESULT_TAGS = arrayOf(
            "9F26", "9F27", "9F10", "9F37", "9F36", "95", "9B", "9F1A", "5F2A", "9A", "9C",
            "9F02", "57", "5F34", "9F0F", "8F", "4F", "5F24", "8E", "9F34", "9F66", "9F6C",
            "82", "9F33", "9F6E", "9F3E", "9F0A"
        )
    }

    @Volatile private var searching = false
    @Volatile private var running = false

    override fun start(
        request: EmvPaymentRequest,
        authorizer: EmvOnlineAuthorizer,
        listener: EmvPaymentListener
    ) {
        if (searching || running) {
            Log.w(TAG, "Transaction déjà en cours")
            return
        }
        val amount = request.amountInCents.toLongOrNull()
        if (amount == null || amount <= 0L) {
            listener.onError("Montant invalide : ${request.amountInCents}")
            return
        }

        val cardReader = deviceEngine.getCardReader()
        try { cardReader.stopSearch() } catch (_: Exception) {}

        val slots = HashSet<CardSlotTypeEnum>().apply {
            add(CardSlotTypeEnum.ICC1)
            add(CardSlotTypeEnum.RF)
        }

        listener.onWaitingForCard()
        searching = true

        try {
            cardReader.searchCard(slots, SEARCH_TIMEOUT, object : OnCardInfoListener {

                override fun onCardInfo(retCode: Int, cardInfoEntity: CardInfoEntity?) {
                    if (!searching) return
                    searching = false
                    try { cardReader.stopSearch() } catch (_: Exception) {}

                    if (retCode != 0 || cardInfoEntity == null) {
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
                    searching = false
                    listener.onError("Lecture magnétique incorrecte")
                }

                override fun onMultipleCards() {
                    searching = false
                    listener.onError("Plusieurs cartes détectées")
                }
            })
        } catch (e: Exception) {
            searching = false
            listener.onError("Erreur recherche carte : ${e.message}")
        }
    }

    private fun runEmv(
        request: EmvPaymentRequest,
        entry: EmvEntryMode,
        authorizer: EmvOnlineAuthorizer,
        listener: EmvPaymentListener
    ) {
        val contactless = entry == EmvEntryMode.CONTACTLESS

        // 9F33 / 9F66 viennent des paramètres terminal chargés depuis le SDK Worldline
        configurator.applyTerminalTlv(contactless)

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
                configurator.applyTerminalTlv(contactless)
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
                val tags = readTags(ONLINE_TAGS)
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
                running = false
                val tags = readTags(RESULT_TAGS)
                tags.forEach { (t, v) -> Log.d(TAG, "TAG $t = $v") }
                listener.onFinished(EmvPaymentResult(result, result == 0, tags))
            }
        })
    }

    override fun cancel() {
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