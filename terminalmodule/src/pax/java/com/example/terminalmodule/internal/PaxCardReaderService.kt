package com.example.terminalmodule.pax.internal

import com.example.terminalmodule.api.CardReaderService
import com.example.terminalmodule.internal.Track2Parser
import com.example.terminalmodule.internal.awaitCallback
import com.example.terminalmodule.internal.terminalCall
import com.example.terminalmodule.internal.toCardData
import com.example.terminalmodule.model.CardData
import com.example.terminalmodule.model.TerminalError
import com.example.terminalmodule.model.TerminalResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** Piste magnétique uniquement. Puce / sans contact : brancher le noyau EMV ici. */
internal class PaxCardReaderService(private val dal: IDAL) : CardReaderService {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun readCard(timeout: Duration): TerminalResult<CardData> = terminalCall {
        val helper = dal.cardReaderHelper // SDK-VERIFY
        awaitCallback<CardData>(onCancel = { helper.stopPolling() }) { resume ->
            ioScope.launch { resume(poll(helper, timeout)) }
        }
    }

    private fun poll(helper: ICardReaderHelper, timeout: Duration): TerminalResult<CardData> = try {
        val result = helper.polling(EReaderType.MAG, timeout.inWholeMilliseconds.toInt()) // SDK-VERIFY
        val track2 = Track2Parser.parse(result.track2) // SDK-VERIFY
        if (track2 == null) TerminalResult.Failure(TerminalError.Hardware(-1, "Piste 2 illisible"))
        else TerminalResult.Success(track2.toCardData())
    } catch (e: Exception) {
        TerminalResult.Failure(PaxErrorMapper.fromException(e))
    }
}
