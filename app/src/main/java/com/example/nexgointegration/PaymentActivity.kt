package com.example.nexgointegration

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import com.example.nexgointegration.feedback.NexgoTerminalFeedback
import com.example.nexgointegration.gateway.FakePaymentGateway
import com.example.nexgointegration.parameters.WorldlineEmvParametersProvider
import com.example.nexgointegration.payment.PaymentConfig
import com.example.nexgointegration.payment.PaymentFlowController
import com.example.nexgointegration.payment.PaymentUiState
import com.example.nexgointegration.ui.payment.PaymentView
import com.example.terminalmodule.Terminal
import com.example.worldlineintegrationsdk.WorldlineSync
import com.magellan.tapandgo.validatorsdk.T2UOpenPaymentSDK.sendWaitingTaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran de paiement à montant fixe.
 * Cette activité ne fait que brancher les trois couches entre elles :
 * l'UI (PaymentView), la logique (PaymentFlowController) et le retour LED/buzzer.
 */
class PaymentActivity : Activity() {

    private val scope = MainScope()

    private lateinit var paymentView: PaymentView
    private var controller: PaymentFlowController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        paymentView = PaymentView(this, PaymentConfig.displayLabel)
        setContentView(paymentView)
        paymentView.render(PaymentUiState.Preparing)

        prepareTerminalThenStart()
    }

    override fun onDestroy() {
        controller?.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun prepareTerminalThenStart() {
        scope.launch {
            try {
                Terminal.init(applicationContext)
                WorldlineSync(applicationContext).run()
                    .onFailure { Log.w(TAG, "Synchro Worldline échouée : ${it.message}", it) }
            } catch (e: Throwable) {
                Log.e(TAG, "Initialisation du terminal échouée", e)
                paymentView.render(PaymentUiState.Failure("Terminal indisponible"))
                return@launch
            }

            controller = buildController().also { flow ->
                paymentView.onCancelClicked = flow::cancel
                paymentView.onRetryClicked = flow::start
                flow.start()
            }
        }
    }

    private fun buildController() = PaymentFlowController(
        createManager = { listener ->
            PaymentManager(
                context = this,
                parametersProvider = WorldlineEmvParametersProvider(this),
                authorizer = GatewayOnlineAuthorizer(FakePaymentGateway()),
                listener = listener
            )
        },
        feedback = NexgoTerminalFeedback(applicationContext, scope),
        scope = scope,
        onStateChanged = paymentView::render,
        afterTransaction = { withContext(Dispatchers.IO) { sendWaitingTaps() } }
    )

    private companion object {
        const val TAG = "PaymentActivity"
    }
}