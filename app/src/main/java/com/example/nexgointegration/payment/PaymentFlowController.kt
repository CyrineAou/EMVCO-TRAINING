package com.example.nexgointegration.payment

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.nexgointegration.PaymentManager
import com.example.nexgointegration.feedback.TerminalFeedback
import com.example.terminalmodule.model.EmvEntryMode
import com.example.terminalmodule.model.EmvPaymentResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Chef d'orchestre du paiement.
 *
 * Il traduit les callbacks du PaymentManager en états UI [PaymentUiState]
 * et déclenche le retour sonore/lumineux. Il ne dessine rien.
 */
class PaymentFlowController(
    createManager: (PaymentManager.Listener) -> PaymentManager,
    private val feedback: TerminalFeedback,
    private val scope: CoroutineScope,
    private val onStateChanged: (PaymentUiState) -> Unit,
    /** Travail à faire une fois la transaction terminée (ex. sendWaitingTaps). */
    private val afterTransaction: suspend () -> Unit = {}
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var state: PaymentUiState = PaymentUiState.Preparing
    private var inProgress = false

    // Le listener doit exister avant le manager qui le reçoit.
    private val listener = object : PaymentManager.Listener {

        override fun onWaitingForCard() = onMain {
            if (inProgress && state != PaymentUiState.Processing) {
                publish(PaymentUiState.WaitingForCard)
            }
        }

        override fun onCardDetected(entryMode: EmvEntryMode) = onMain {
            if (inProgress) publish(PaymentUiState.Processing)
        }

        override fun onTransactionFinished(result: EmvPaymentResult) = onMain {
            if (!inProgress) return@onMain
            inProgress = false

            if (result.approved) {
                succeed()
            } else {
                fail("Refusé par la banque (code ${result.code})")
            }
            closeTransaction()
        }

        override fun onError(message: String) = onMain {
            if (!inProgress) return@onMain
            inProgress = false
            fail(message)
        }
    }

    private val manager: PaymentManager = createManager(listener)

    // ---------------------------------------------------------
    // Actions appelées par l'écran
    // ---------------------------------------------------------

    fun start() {
        if (inProgress) return
        inProgress = true
        feedback.reset()
        publish(PaymentUiState.WaitingForCard)

        try {
            manager.startPayment(PaymentConfig.AMOUNT_IN_CENTS.toString())
        } catch (e: Exception) {
            inProgress = false
            fail("Démarrage impossible : ${e.message}")
        }
    }

    fun cancel() {
        if (!inProgress) return
        inProgress = false
        runCatching { manager.cancel() }
        publish(PaymentUiState.Cancelled)
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { manager.destroy() }
        feedback.reset()
    }

    // ---------------------------------------------------------
    // Résultats
    // ---------------------------------------------------------

    private fun succeed() {
        publish(PaymentUiState.Success)
        feedback.success()
    }

    private fun fail(reason: String) {
        publish(PaymentUiState.Failure(reason))
        feedback.failure()
    }

    private fun closeTransaction() {
        scope.launch {
            try {
                afterTransaction()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Fin de transaction : échec de afterTransaction", e)
            }
        }
    }

    // ---------------------------------------------------------
    // Outils internes
    // ---------------------------------------------------------

    private fun publish(newState: PaymentUiState) {
        state = newState
        onStateChanged(newState)
    }

    /** Les callbacks du SDK peuvent arriver sur un autre thread : on revient sur l'UI. */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private companion object {
        const val TAG = "PaymentFlow"
    }
}