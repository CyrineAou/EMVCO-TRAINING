//package com.example.nexgointegration
//
//import android.content.Context
//import android.util.Log
//import com.example.nexgointegration.gateway.PaymentGateway
//import com.nexgo.oaf.apiv3.APIProxy
//import com.nexgo.oaf.apiv3.DeviceEngine
//import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
//import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
//import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener
//import com.nexgo.oaf.apiv3.emv.EmvEntryModeEnum
//import com.nexgo.oaf.apiv3.emv.EmvProcessResultEntity
//import java.util.HashSet
//
//class PaymentManager(
//    context: Context,
//    private val paymentGateway: PaymentGateway,
//    private val listener: Listener
//) {
//
//    companion object {
//        private const val TAG = "PaymentManager"
//    }
//
//    interface Listener {
//        fun onWaitingForCard()
//        fun onCardDetected(cardInfo: CardInfoEntity)
//        fun onTransactionFinished(
//            result: Int,
//            resultEntity: EmvProcessResultEntity?
//        )
//        fun onError(message: String)
//    }
//
//    private val deviceEngine: DeviceEngine =
//        APIProxy.getDeviceEngine(context)
//
//    private val emvTransactionManager =
//        EmvTransactionManager(
//            context = context,
//            paymentGateway = paymentGateway,
//            deviceEngine = deviceEngine,
//
//            listener = object : EmvTransactionManager.Listener {
//
//                override fun onStatus(message: String) {
//                    Log.d(TAG, message)
//                }
//
//                override fun onFinished(
//                    result: Int,
//                    resultEntity: EmvProcessResultEntity?
//                ) {
//                    listener.onTransactionFinished(
//                        result,
//                        resultEntity
//                    )
//                }
//
//                override fun onError(message: String) {
//                    listener.onError(message)
//                }
//            }
//        )
//
//    private var searching = false
//    private var transactionRunning = false
//
//    fun startPayment(amountInCents: String) {
//
//        if (searching || transactionRunning) {
//            Log.w(TAG, "Transaction already running")
//            return
//        }
//
//        if (amountInCents.isBlank()) {
//            listener.onError("Montant invalide")
//            return
//        }
//
//        listener.onWaitingForCard()
//
//        searching = true
//
//        val cardReader = deviceEngine.getCardReader()
//
//        try {
//            cardReader.stopSearch()
//        } catch (_: Exception) {
//        }
//
//        val slotTypes = HashSet<CardSlotTypeEnum>().apply {
//            add(CardSlotTypeEnum.ICC1)
//            add(CardSlotTypeEnum.RF)
//        }
//
//        cardReader.searchCard(
//            slotTypes,
//            30,
//            object : OnCardInfoListener {
//
//                override fun onCardInfo(
//                    retCode: Int,
//                    cardInfoEntity: CardInfoEntity?
//                ) {
//
//                    if (!searching) {
//                        return
//                    }
//
//                    searching = false
//
//                    try {
//                        cardReader.stopSearch()
//                    } catch (_: Exception) {
//                    }
//
//                    if (retCode != 0 || cardInfoEntity == null) {
//
//                        listener.onError(
//                            "Erreur lecture carte : $retCode"
//                        )
//
//                        return
//                    }
//
//                    listener.onCardDetected(cardInfoEntity)
//
//                    val entryMode =
//                        if (
//                            cardInfoEntity.cardExistslot ==
//                            CardSlotTypeEnum.RF
//                        ) {
//                            EmvEntryModeEnum
//                                .EMV_ENTRY_MODE_CONTACTLESS
//                        } else {
//                            EmvEntryModeEnum
//                                .EMV_ENTRY_MODE_CONTACT
//                        }
//
//                    if (
//                        entryMode ==
//                        EmvEntryModeEnum
//                            .EMV_ENTRY_MODE_CONTACTLESS
//                    ) {
//
//                        try {
//                            deviceEngine.beeper.beep(5, 5)
//                        } catch (_: Exception) {
//                        }
//                    }
//
//                    transactionRunning = true
//
//                    emvTransactionManager.startTransaction(
//                        amountInCents,
//                        entryMode
//                    )
//                }
//
//                override fun onSwipeIncorrect() {
//                    searching = false
//                    listener.onError("Lecture magnétique incorrecte")
//                }
//
//                override fun onMultipleCards() {
//                    searching = false
//                    listener.onError("Plusieurs cartes détectées")
//                }
//            }
//        )
//    }
//
//    fun cancel() {
//
//        try {
//            deviceEngine.getCardReader().stopSearch()
//        } catch (_: Exception) {
//        }
//
//        try {
//            emvTransactionManager.cancel()
//        } catch (_: Exception) {
//        }
//
//        searching = false
//        transactionRunning = false
//    }
//
//    fun destroy() {
//        cancel()
//    }
//}


package com.example.nexgointegration

import android.content.Context
import android.util.Log
import com.example.nexgointegration.gateway.PaymentGateway
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

        fun onCardDetected(
            cardInfo: CardInfoEntity
        )

        fun onTransactionFinished(
            result: Int,
            resultEntity: EmvProcessResultEntity?
        )

        fun onError(
            message: String
        )
    }

    private val deviceEngine: DeviceEngine =
        APIProxy.getDeviceEngine(context)

    /**
     * Gestionnaire de la transaction EMV.
     *
     * C'est cette classe qui devra contenir la logique :
     *
     * onOnlineProc()
     *      ↓
     * PaymentGateway
     *      ↓
     * réponse serveur
     *      ↓
     * onSetOnlineProcResponse()
     */
    private val emvTransactionManager =
        EmvTransactionManager(
            context = context,
            paymentGateway = paymentGateway,
            deviceEngine = deviceEngine,

            listener = object : EmvTransactionManager.Listener {

                override fun onStatus(
                    message: String
                ) {

                    Log.d(
                        TAG,
                        "[EMV] $message"
                    )
                }

                override fun onFinished(
                    result: Int,
                    resultEntity: EmvProcessResultEntity?
                ) {

                    /*
                     * La transaction EMV est terminée.
                     *
                     * Il est important de remettre
                     * transactionRunning à false.
                     */
                    transactionRunning = false

                    Log.d(
                        TAG,
                        "[EMV] Transaction finished: result=$result"
                    )

                    listener.onTransactionFinished(
                        result,
                        resultEntity
                    )
                }

                override fun onError(
                    message: String
                ) {

                    /*
                     * En cas d'erreur, on libère
                     * également l'état de transaction.
                     */
                    transactionRunning = false

                    Log.e(
                        TAG,
                        "[EMV] Error: $message"
                    )

                    listener.onError(
                        message
                    )
                }
            }
        )

    /**
     * Indique qu'une recherche de carte est en cours.
     */
    private var searching = false

    /**
     * Indique qu'une transaction EMV est en cours.
     */
    private var transactionRunning = false

    /**
     * Démarre une transaction.
     *
     * @param amountInCents montant en unité minimale
     *                       attendu par ton application.
     */
    fun startPayment(
        amountInCents: String
    ) {

        Log.d(
            TAG,
            "=============================================="
        )

        Log.d(
            TAG,
            "[START] startPayment()"
        )

        Log.d(
            TAG,
            "[START] amount=$amountInCents"
        )

        Log.d(
            TAG,
            "=============================================="
        )

        /*
         * Protection contre deux transactions
         * simultanées.
         */
        if (searching || transactionRunning) {

            Log.w(
                TAG,
                "[START] Transaction already running"
            )

            return
        }

        /*
         * Vérification du montant.
         */
        if (amountInCents.isBlank()) {

            Log.e(
                TAG,
                "[START] Invalid amount"
            )

            listener.onError(
                "Montant invalide"
            )

            return
        }

        /*
         * Vérification supplémentaire :
         * le montant doit être numérique.
         */
        val amountValue = amountInCents.toLongOrNull()

        if (amountValue == null) {

            Log.e(
                TAG,
                "[START] Amount is not numeric: $amountInCents"
            )

            listener.onError(
                "Montant invalide : $amountInCents"
            )

            return
        }

        if (amountValue <= 0L) {

            Log.e(
                TAG,
                "[START] Amount must be greater than zero"
            )

            listener.onError(
                "Le montant doit être supérieur à zéro"
            )

            return
        }

        /*
         * Demande à l'interface d'afficher
         * "Veuillez présenter la carte".
         */
        listener.onWaitingForCard()

        searching = true

        /*
         * Récupération du lecteur de cartes.
         */
        val cardReader =
            deviceEngine.getCardReader()

        /*
         * Arrêt préventif d'une recherche
         * précédente.
         */
        try {

            cardReader.stopSearch()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "[CARD] stopSearch before start failed",
                e
            )
        }

        /*
         * Types de cartes acceptés :
         *
         * ICC1  -> carte à puce
         * RF    -> sans contact
         */
        val slotTypes =
            HashSet<CardSlotTypeEnum>().apply {

                add(
                    CardSlotTypeEnum.ICC1
                )

                add(
                    CardSlotTypeEnum.RF
                )
            }

        Log.d(
            TAG,
            "[CARD] Searching for card..."
        )

        try {

            cardReader.searchCard(
                slotTypes,
                CARD_SEARCH_TIMEOUT,

                object : OnCardInfoListener {

                    override fun onCardInfo(
                        retCode: Int,
                        cardInfoEntity: CardInfoEntity?
                    ) {

                        /*
                         * Si la recherche a déjà été
                         * annulée, on ignore le callback.
                         */
                        if (!searching) {

                            Log.d(
                                TAG,
                                "[CARD] Callback ignored: search stopped"
                            )

                            return
                        }

                        searching = false

                        /*
                         * Arrêt de la recherche dès
                         * qu'une carte est détectée.
                         */
                        try {

                            cardReader.stopSearch()

                        } catch (e: Exception) {

                            Log.w(
                                TAG,
                                "[CARD] stopSearch after detection failed",
                                e
                            )
                        }

                        /*
                         * Vérification du résultat.
                         */
                        if (
                            retCode != 0 ||
                            cardInfoEntity == null
                        ) {

                            Log.e(
                                TAG,
                                "[CARD] Card detection failed: $retCode"
                            )

                            listener.onError(
                                "Erreur lecture carte : $retCode"
                            )

                            return
                        }

                        Log.d(
                            TAG,
                            "[CARD] Card detected"
                        )

                        Log.d(
                            TAG,
                            "[CARD] Slot=${cardInfoEntity.cardExistslot}"
                        )

                        /*
                         * Informer MainActivity.
                         */
                        listener.onCardDetected(
                            cardInfoEntity
                        )

                        /*
                         * Détermination du mode d'entrée.
                         */
                        val entryMode =
                            if (
                                cardInfoEntity.cardExistslot ==
                                CardSlotTypeEnum.RF
                            ) {

                                Log.d(
                                    TAG,
                                    "[CARD] Entry mode = CONTACTLESS"
                                )

                                EmvEntryModeEnum
                                    .EMV_ENTRY_MODE_CONTACTLESS

                            } else {

                                Log.d(
                                    TAG,
                                    "[CARD] Entry mode = CONTACT"
                                )

                                EmvEntryModeEnum
                                    .EMV_ENTRY_MODE_CONTACT
                            }

                        /*
                         * Bip pour une carte sans contact.
                         */
                        if (
                            entryMode ==
                            EmvEntryModeEnum
                                .EMV_ENTRY_MODE_CONTACTLESS
                        ) {

                            try {

                                deviceEngine.beeper.beep(
                                    5,
                                    5
                                )

                            } catch (e: Exception) {

                                Log.w(
                                    TAG,
                                    "[BEEPER] Beep failed",
                                    e
                                )
                            }
                        }

                        /*
                         * La transaction EMV démarre
                         * réellement ici.
                         */
                        transactionRunning = true

                        Log.d(
                            TAG,
                            "[EMV] Starting EMV transaction"
                        )

                        try {

                            emvTransactionManager.startTransaction(
                                amountInCents,
                                entryMode
                            )

                        } catch (e: Exception) {

                            /*
                             * Si le démarrage du kernel
                             * échoue immédiatement.
                             */
                            transactionRunning = false

                            Log.e(
                                TAG,
                                "[EMV] Failed to start transaction",
                                e
                            )

                            listener.onError(
                                "Erreur démarrage EMV : " +
                                        (e.message ?: "inconnue")
                            )
                        }
                    }

                    override fun onSwipeIncorrect() {

                        searching = false

                        Log.e(
                            TAG,
                            "[CARD] Magnetic stripe read error"
                        )

                        listener.onError(
                            "Lecture magnétique incorrecte"
                        )
                    }

                    override fun onMultipleCards() {

                        searching = false

                        Log.e(
                            TAG,
                            "[CARD] Multiple cards detected"
                        )

                        listener.onError(
                            "Plusieurs cartes détectées"
                        )
                    }
                }
            )

        } catch (e: Exception) {

            searching = false

            Log.e(
                TAG,
                "[CARD] searchCard() failed",
                e
            )

            listener.onError(
                "Erreur recherche carte : " +
                        (e.message ?: "inconnue")
            )
        }
    }

    /**
     * Annule la recherche ou la transaction.
     */
    fun cancel() {

        Log.d(
            TAG,
            "[CANCEL] Cancel requested"
        )

        /*
         * Arrêt de la recherche de carte.
         */
        try {

            deviceEngine
                .getCardReader()
                .stopSearch()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "[CANCEL] stopSearch failed",
                e
            )
        }

        /*
         * Annulation de la transaction EMV.
         */
        try {

            emvTransactionManager.cancel()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "[CANCEL] EMV cancel failed",
                e
            )
        }

        /*
         * Réinitialisation de l'état.
         */
        searching = false
        transactionRunning = false

        Log.d(
            TAG,
            "[CANCEL] State reset"
        )
    }

    /**
     * Libération des ressources.
     */
    fun destroy() {

        Log.d(
            TAG,
            "[LIFECYCLE] destroy()"
        )

        cancel()
    }
}