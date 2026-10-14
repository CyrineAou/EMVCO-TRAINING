package com.example.terminalmodule.api

import com.example.terminalmodule.model.EmvEntryMode
import com.example.terminalmodule.model.EmvOnlineRequest
import com.example.terminalmodule.model.EmvOnlineResponse
import com.example.terminalmodule.model.EmvPaymentRequest
import com.example.terminalmodule.model.EmvPaymentResult

/** Appelé sur le thread EMV (pas le thread UI) : un appel réseau bloquant est permis. */
interface EmvOnlineAuthorizer {
    fun authorize(request: EmvOnlineRequest): EmvOnlineResponse
}

interface EmvPaymentListener {
    fun onWaitingForCard()
    fun onCardDetected(entryMode: EmvEntryMode)
    fun onFinished(result: EmvPaymentResult)
    fun onError(message: String)
}

interface EmvPaymentService {
    fun start(request: EmvPaymentRequest, authorizer: EmvOnlineAuthorizer, listener: EmvPaymentListener)
    fun cancel()
    fun destroy()
}