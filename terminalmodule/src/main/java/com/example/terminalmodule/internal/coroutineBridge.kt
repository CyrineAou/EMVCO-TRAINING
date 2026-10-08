package com.example.terminalmodule.internal

import com.example.terminalmodule.model.TerminalError
import com.example.terminalmodule.model.TerminalResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Transforme un SDK à callback en fonction suspendue. [onCancel] arrête le matériel. */
internal suspend fun <T> awaitCallback(
    onCancel: () -> Unit = {},
    register: ((TerminalResult<T>) -> Unit) -> Unit,
): TerminalResult<T> = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching(onCancel) }
    register { result -> if (cont.isActive) cont.resume(result) }
}

/** Filet de sécurité : aucune exception SDK ne remonte à l'UI. */
internal suspend fun <T> terminalCall(block: suspend () -> TerminalResult<T>): TerminalResult<T> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        TerminalResult.Failure(TerminalError.Unexpected(e))
    }
