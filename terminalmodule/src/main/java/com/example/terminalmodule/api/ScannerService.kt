package com.example.terminalmodule.api

import com.example.terminalmodule.model.TerminalResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface ScannerService {
    /** Annuler la coroutine appelante arrête le scan côté SDK. */
    suspend fun scan(timeout: Duration = 30.seconds): TerminalResult<String>
}
