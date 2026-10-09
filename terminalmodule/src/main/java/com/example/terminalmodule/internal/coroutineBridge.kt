package com.example.terminalmodule.internal

import android.os.SystemClock
import com.example.terminalmodule.model.TerminalError
import com.example.terminalmodule.model.TerminalResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private fun TerminalResult<*>.summary() = when (this) {
    is TerminalResult.Success -> "Success"          // la valeur n'est jamais loggée
    is TerminalResult.Failure -> "Failure($error)"
}

/** Transforme un SDK à callback en fonction suspendue. [onCancel] arrête le matériel. */
internal suspend fun <T> awaitCallback(
    name: String = "callback",
    onCancel: () -> Unit = {},
    register: ((TerminalResult<T>) -> Unit) -> Unit,
): TerminalResult<T> = suspendCancellableCoroutine { cont ->
    val t0 = SystemClock.elapsedRealtime()
    fun elapsed() = SystemClock.elapsedRealtime() - t0

    cont.invokeOnCancellation {
        TerminalLog.w("[$name] annulé après ${elapsed()} ms, arrêt du matériel")
        runCatching(onCancel).onFailure { TerminalLog.e("[$name] onCancel a échoué", it) }
    }
    try {
        register { result ->
            if (cont.isActive) {
                TerminalLog.d("[$name] réponse après ${elapsed()} ms : ${result.summary()}")
                cont.resume(result)
            } else {
                TerminalLog.w("[$name] réponse ignorée (déjà terminé) après ${elapsed()} ms")
            }
        }
    } catch (e: Throwable) {
        TerminalLog.e("[$name] register a échoué : ${e.javaClass.simpleName}: ${e.message}", e)
        throw e
    }
}

/** Filet de sécurité : aucune exception SDK ne remonte à l'UI. */
internal suspend fun <T> terminalCall(block: suspend () -> TerminalResult<T>): TerminalResult<T> =
    try {
        block()
    } catch (e: CancellationException) {
        TerminalLog.d("terminalCall : coroutine annulée")
        throw e
    } catch (e: Throwable) {
        TerminalLog.e("terminalCall : ${e.javaClass.simpleName}: ${e.message}", e)
        TerminalResult.Failure(TerminalError.Unexpected(e))
    }