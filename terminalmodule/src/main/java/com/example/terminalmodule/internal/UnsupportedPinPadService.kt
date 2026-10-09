package com.example.terminalmodule.internal

import com.example.terminalmodule.api.PinPadService
import com.example.terminalmodule.model.*

internal object UnsupportedPinPadService : PinPadService {
    private val failure = TerminalResult.Failure(TerminalError.Unsupported("PIN pad non implémenté pour ce terminal"))
    override suspend fun injectKey(injection: KeyInjection) = failure
    override suspend fun requestPin(request: PinRequest) = failure
}