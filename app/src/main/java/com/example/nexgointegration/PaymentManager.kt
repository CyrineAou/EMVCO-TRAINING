package com.example.nexgointegration

import android.content.Context
import android.util.Log
import com.example.nexgointegration.getway.PaymentGateway
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
    }

    interface Listener {
        fun onWaitingForCard()
        fun onCardDetected(cardInfo: CardInfoEntity)
        fun onTransactionFinished(
            result: Int,
            resultEntity: EmvProcessResultEntity?
        )
        fun onError(message: String)
    }

    private val deviceEngine: DeviceEngine =
        APIProxy.getDeviceEngine(context)

    private val emvTransactionManager =
        EmvTransactionManager(
            context = context,
            paymentGateway = paymentGateway,
            deviceEngine = deviceEngine,

            listener = object : EmvTransactionManager.Listener {

                override fun onStatus(message: String) {
                    Log.d(TAG, message)
                }

                override fun onFinished(
                    result: Int,
                    resultEntity: EmvProcessResultEntity?
                ) {
                    listener.onTransactionFinished(
                        result,
                        resultEntity
                    )
                }

                override fun onError(message: String) {
                    listener.onError(message)
                }
            }
        )

    private var searching = false
    private var transactionRunning = false

    fun startPayment(amountInCents: String) {

        if (searching || transactionRunning) {
            Log.w(TAG, "Transaction already running")
            return
        }

        if (amountInCents.isBlank()) {
            listener.onError("Montant invalide")
            return
        }

        listener.onWaitingForCard()

        searching = true

        val cardReader = deviceEngine.getCardReader()

        try {
            cardReader.stopSearch()
        } catch (_: Exception) {
        }

        val slotTypes = HashSet<CardSlotTypeEnum>().apply {
            add(CardSlotTypeEnum.ICC1)
            add(CardSlotTypeEnum.RF)
        }

        cardReader.searchCard(
            slotTypes,
            30,
            object : OnCardInfoListener {

                override fun onCardInfo(
                    retCode: Int,
                    cardInfoEntity: CardInfoEntity?
                ) {

                    if (!searching) {
                        return
                    }

                    searching = false

                    try {
                        cardReader.stopSearch()
                    } catch (_: Exception) {
                    }

                    if (retCode != 0 || cardInfoEntity == null) {

                        listener.onError(
                            "Erreur lecture carte : $retCode"
                        )

                        return
                    }

                    listener.onCardDetected(cardInfoEntity)

                    val entryMode =
                        if (
                            cardInfoEntity.cardExistslot ==
                            CardSlotTypeEnum.RF
                        ) {
                            EmvEntryModeEnum
                                .EMV_ENTRY_MODE_CONTACTLESS
                        } else {
                            EmvEntryModeEnum
                                .EMV_ENTRY_MODE_CONTACT
                        }

                    if (
                        entryMode ==
                        EmvEntryModeEnum
                            .EMV_ENTRY_MODE_CONTACTLESS
                    ) {

                        try {
                            deviceEngine.beeper.beep(5, 5)
                        } catch (_: Exception) {
                        }
                    }

                    transactionRunning = true

                    emvTransactionManager.startTransaction(
                        amountInCents,
                        entryMode
                    )
                }

                override fun onSwipeIncorrect() {
                    searching = false
                    listener.onError("Lecture magnétique incorrecte")
                }

                override fun onMultipleCards() {
                    searching = false
                    listener.onError("Plusieurs cartes détectées")
                }
            }
        )
    }

    fun cancel() {

        try {
            deviceEngine.getCardReader().stopSearch()
        } catch (_: Exception) {
        }

        try {
            emvTransactionManager.cancel()
        } catch (_: Exception) {
        }

        searching = false
        transactionRunning = false
    }

    fun destroy() {
        cancel()
    }
}