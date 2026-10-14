package com.example.nexgointegration

import android.content.Context
import android.util.Log
import com.example.nexgointegration.parameters.EmvParametersManager
import com.example.nexgointegration.parameters.EmvParametersProvider
import com.example.terminalmodule.EmvKernelConfiguratorProvider
import com.example.terminalmodule.EmvPaymentServiceProvider
import com.example.terminalmodule.api.EmvOnlineAuthorizer
import com.example.terminalmodule.api.EmvPaymentListener
import com.example.terminalmodule.model.EmvEntryMode
import com.example.terminalmodule.model.EmvPaymentRequest
import com.example.terminalmodule.model.EmvPaymentResult

class PaymentManager(
    context: Context,
    val parametersProvider: EmvParametersProvider,
    private val authorizer: EmvOnlineAuthorizer,
    private val listener: Listener
) {

    companion object {
        private const val TAG = "PaymentManager"
    }

    interface Listener {
        fun onWaitingForCard()
        fun onCardDetected(entryMode: EmvEntryMode)
        fun onTransactionFinished(result: EmvPaymentResult)
        fun onError(message: String)
    }

    /** Aussi utilisé par MainActivity pour l'auto-test. */
    val emvKernelConfigurator = EmvKernelConfiguratorProvider.create(context)

    private val paymentService = EmvPaymentServiceProvider.create(context)

    private val emvParameters = EmvParametersManager(parametersProvider, emvKernelConfigurator)

    init {
        emvParameters.apply().onFailure {
            listener.onError("Paramètres EMV : ${it.message}")
        }
    }

    fun startPayment(amountInCents: String) {
        Log.d(TAG, "startPayment amount=$amountInCents")
        paymentService.start(
            request = EmvPaymentRequest(amountInCents = amountInCents),
            authorizer = authorizer,
            listener = object : EmvPaymentListener {
                override fun onWaitingForCard() = listener.onWaitingForCard()
                override fun onCardDetected(entryMode: EmvEntryMode) = listener.onCardDetected(entryMode)
                override fun onFinished(result: EmvPaymentResult) = listener.onTransactionFinished(result)
                override fun onError(message: String) = listener.onError(message)
            }
        )
    }

    fun cancel() = paymentService.cancel()

    fun destroy() = paymentService.destroy()
}