package com.example.nexgointegration

import android.content.Context
import android.util.Log
import com.example.nexgointegration.gateway.PaymentGateway
import com.example.terminalmodule.EmvKernelConfiguratorProvider
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener
import com.nexgo.oaf.apiv3.emv.EmvEntryModeEnum
import com.nexgo.oaf.apiv3.emv.EmvProcessResultEntity
import java.util.HashSet

class PaymentManager(
    context: Context,
    private val paymentGateway: PaymentGateway,
    private val listener: Listener
) {

    companion object {
        private const val TAG = "PaymentManager"
        private const val CARD_SEARCH_TIMEOUT = 30
    }

    interface Listener {
        fun onWaitingForCard()
        fun onCardDetected(cardInfo: CardInfoEntity)
        fun onTransactionFinished(result: Int, resultEntity: EmvProcessResultEntity?)
        fun onError(message: String)
    }

    // ---------------- State ----------------

    private var searching = false
    private var transactionRunning = false

    // ---------------- EMV parameters (step 1) ----------------

    /** Aussi utilisé par MainActivity pour l'auto-test sur le vrai kernel. */
    val emvKernelConfigurator = EmvKernelConfiguratorProvider.create(context)

    private val emvParameters = EmvParametersManager(
        context = context,
        configurator = emvKernelConfigurator
    )

    init {
        emvParameters.apply()
            .onFailure {
                Log.e(TAG, "[EMV_PARAMS] load failed", it)
                listener.onError("Paramètres EMV : ${it.message}")
            }
    }

    // ---------------- Existing code ----------------

    private val deviceEngine: DeviceEngine =
        APIProxy.getDeviceEngine(context)

    private val emvTransactionManager =
        EmvTransactionManager(
            context = context,
            paymentGateway = paymentGateway,
            deviceEngine = deviceEngine,
            listener = object : EmvTransactionManager.Listener {

                override fun onStatus(message: String) {
                    Log.d(TAG, "[EMV] $message")
                }

                override fun onFinished(result: Int, resultEntity: EmvProcessResultEntity?) {
                    transactionRunning = false
                    Log.d(TAG, "[EMV] Transaction finished: result=$result")
                    listener.onTransactionFinished(result, resultEntity)
                }

                override fun onError(message: String) {
                    transactionRunning = false
                    Log.e(TAG, "[EMV] Error: $message")
                    listener.onError(message)
                }
            }
        )

    fun startPayment(amountInCents: String) {

        Log.d(TAG, "[START] startPayment() amount=$amountInCents")

        if (searching || transactionRunning) {
            Log.w(TAG, "[START] Transaction already running")
            return
        }

        if (amountInCents.isBlank()) {
            Log.e(TAG, "[START] Invalid amount")
            listener.onError("Montant invalide")
            return
        }

        val amountValue = amountInCents.toLongOrNull()

        if (amountValue == null) {
            Log.e(TAG, "[START] Amount is not numeric: $amountInCents")
            listener.onError("Montant invalide : $amountInCents")
            return
        }

        if (amountValue <= 0L) {
            Log.e(TAG, "[START] Amount must be greater than zero")
            listener.onError("Le montant doit être supérieur à zéro")
            return
        }

        listener.onWaitingForCard()
        searching = true

        val cardReader = deviceEngine.getCardReader()

        try {
            cardReader.stopSearch()
        } catch (e: Exception) {
            Log.w(TAG, "[CARD] stopSearch before start failed", e)
        }

        val slotTypes = HashSet<CardSlotTypeEnum>().apply {
            add(CardSlotTypeEnum.ICC1)   // contact
            add(CardSlotTypeEnum.RF)     // contactless
        }

        Log.d(TAG, "[CARD] Searching for card...")

        try {
            cardReader.searchCard(
                slotTypes,
                CARD_SEARCH_TIMEOUT,
                object : OnCardInfoListener {

                    override fun onCardInfo(retCode: Int, cardInfoEntity: CardInfoEntity?) {

                        if (!searching) {
                            Log.d(TAG, "[CARD] Callback ignored: search stopped")
                            return
                        }

                        searching = false

                        try {
                            cardReader.stopSearch()
                        } catch (e: Exception) {
                            Log.w(TAG, "[CARD] stopSearch after detection failed", e)
                        }

                        if (retCode != 0 || cardInfoEntity == null) {
                            Log.e(TAG, "[CARD] Card detection failed: $retCode")
                            listener.onError("Erreur lecture carte : $retCode")
                            return
                        }

                        Log.d(TAG, "[CARD] Card detected, slot=${cardInfoEntity.cardExistslot}")

                        listener.onCardDetected(cardInfoEntity)

                        val entryMode =
                            if (cardInfoEntity.cardExistslot == CardSlotTypeEnum.RF) {
                                Log.d(TAG, "[CARD] Entry mode = CONTACTLESS")
                                EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACTLESS
                            } else {
                                Log.d(TAG, "[CARD] Entry mode = CONTACT")
                                EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACT
                            }

                        if (entryMode == EmvEntryModeEnum.EMV_ENTRY_MODE_CONTACTLESS) {
                            try {
                                deviceEngine.beeper.beep(5, 5)
                            } catch (e: Exception) {
                                Log.w(TAG, "[BEEPER] Beep failed", e)
                            }
                        }

                        transactionRunning = true
                        Log.d(TAG, "[EMV] Starting EMV transaction")

                        try {
                            emvTransactionManager.startTransaction(amountInCents, entryMode)
                        } catch (e: Exception) {
                            transactionRunning = false
                            Log.e(TAG, "[EMV] Failed to start transaction", e)
                            listener.onError(
                                "Erreur démarrage EMV : " + (e.message ?: "inconnue")
                            )
                        }
                    }

                    override fun onSwipeIncorrect() {
                        searching = false
                        Log.e(TAG, "[CARD] Magnetic stripe read error")
                        listener.onError("Lecture magnétique incorrecte")
                    }

                    override fun onMultipleCards() {
                        searching = false
                        Log.e(TAG, "[CARD] Multiple cards detected")
                        listener.onError("Plusieurs cartes détectées")
                    }
                }
            )
        } catch (e: Exception) {
            searching = false
            Log.e(TAG, "[CARD] searchCard() failed", e)
            listener.onError("Erreur recherche carte : " + (e.message ?: "inconnue"))
        }
    }

    fun cancel() {

        Log.d(TAG, "[CANCEL] Cancel requested")

        try {
            deviceEngine.getCardReader().stopSearch()
        } catch (e: Exception) {
            Log.w(TAG, "[CANCEL] stopSearch failed", e)
        }

        try {
            emvTransactionManager.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "[CANCEL] EMV cancel failed", e)
        }

        searching = false
        transactionRunning = false

        Log.d(TAG, "[CANCEL] State reset")
    }

    fun destroy() {
        Log.d(TAG, "[LIFECYCLE] destroy()")
        cancel()
    }
}