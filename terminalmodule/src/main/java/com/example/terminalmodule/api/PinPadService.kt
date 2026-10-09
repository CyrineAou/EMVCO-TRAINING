package com.example.terminalmodule.api

import com.example.terminalmodule.model.*

interface PinPadService {
    suspend fun injectKey(injection: KeyInjection): TerminalResult<Unit>
    suspend fun requestPin(request: PinRequest): TerminalResult<EncryptedPin>
}