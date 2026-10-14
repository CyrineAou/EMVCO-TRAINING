
package com.example.terminalmodule.internal

import android.util.Log
import com.nexgo.oaf.apiv3.emv.EmvHandler2
import com.nexgo.oaf.apiv3.emv.EmvTransConfigurationEntity
import com.nexgo.oaf.apiv3.emv.OnEmvProcessListener2
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Responsable du lancement et du contrôle du processus EMV Nexgo.
 *
 * La configuration du kernel reste dans NexgoEmvConfigurator.
 * La traduction des événements EMV vers l'API publique Terminal
 * doit être réalisée par la couche d'abstraction.
 */
internal class NexgoPaymentProcessor(
    private val emvHandler: EmvHandler2
) {

    companion object {
        private const val TAG = "NEXGO_PAYMENT"
    }

    private val transactionRunning = AtomicBoolean(false)

    /**
     * Lance le processus EMV.
     *
     * Le listener est fourni par l'appelant.
     * Il doit implémenter toutes les méthodes de
     * OnEmvProcessListener2 définies dans le SDK Nexgo.
     *
     * Le résultat retourné est le code de l'appel emvProcess(),
     * et non le résultat final du paiement.
     */
    @Synchronized
    fun start(
        configuration: EmvTransConfigurationEntity,
        listener: OnEmvProcessListener2
    ): Result<Int> {
        if (!transactionRunning.compareAndSet(false, true)) {
            return Result.failure(
                IllegalStateException(
                    "Une transaction EMV est déjà en cours."
                )
            )
        }

        return runCatching {
            Log.d(TAG, "Démarrage du processus EMV")

            val result = emvHandler.emvProcess(
                configuration,
                listener
            )

            Log.d(TAG, "Code retour emvProcess = $result")

            result
        }.onFailure { error ->
            transactionRunning.set(false)
            Log.e(TAG, "Échec du démarrage EMV", error)
        }
    }

    /**
     * À appeler lorsque le callback final du SDK confirme
     * que le processus EMV est terminé.
     */
    fun onTransactionFinished() {
        transactionRunning.set(false)
        Log.d(TAG, "Processus EMV terminé")
    }

    /**
     * Demande l'annulation du processus EMV.
     * La fin effective doit être confirmée par le SDK.
     */
    fun cancel() {
        if (!transactionRunning.get()) {
            Log.w(TAG, "Aucune transaction EMV en cours")
            return
        }

        try {
            Log.d(TAG, "Demande d'annulation EMV")
            emvHandler.emvProcessCancel()
        } catch (error: Exception) {
            Log.e(TAG, "Erreur pendant l'annulation EMV", error)
        }
    }

    /**
     * Demande l'interruption du processus EMV.
     */
    fun abort() {
        if (!transactionRunning.get()) {
            Log.w(TAG, "Aucune transaction EMV à interrompre")
            return
        }

        try {
            Log.d(TAG, "Demande d'interruption EMV")
            emvHandler.emvProcessAbort()
        } catch (error: Exception) {
            Log.e(TAG, "Erreur pendant l'interruption EMV", error)
        }
    }

    fun isTransactionRunning(): Boolean =
        transactionRunning.get()
}
