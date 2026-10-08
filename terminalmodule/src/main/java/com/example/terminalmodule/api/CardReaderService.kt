package com.example.terminalmodule.api

import com.example.terminalmodule.model.CardData
import com.example.terminalmodule.model.TerminalResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface CardReaderService {
    /** Annuler la coroutine appelante arrête la lecture côté SDK. */
    suspend fun readCard(timeout: Duration = 30.seconds): TerminalResult<CardData>
}
