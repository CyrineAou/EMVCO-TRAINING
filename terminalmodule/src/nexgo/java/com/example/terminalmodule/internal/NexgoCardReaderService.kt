package com.example.terminalmodule.internal

import com.example.terminalmodule.api.CardReaderService
import com.example.terminalmodule.model.CardData
import com.example.terminalmodule.model.TerminalError
import com.example.terminalmodule.model.TerminalResult
import com.example.terminalmodule.nexgo.internal.NexgoErrorMapper
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener
import kotlin.time.Duration

/** Piste magnétique uniquement. Puce / sans contact : brancher le noyau EMV ici. */
internal class NexgoCardReaderService(private val engine: DeviceEngine) : CardReaderService {

    override suspend fun readCard(timeout: Duration): TerminalResult<CardData> = terminalCall {
        val reader = engine.cardReader
        awaitCallback<CardData>(onCancel = { reader.stopSearch() }) { resume ->
            val slots = hashSetOf(CardSlotTypeEnum.SWIPE, CardSlotTypeEnum.ICC1, CardSlotTypeEnum.RF)
            reader.searchCard(slots, timeout.inWholeSeconds.toInt(), object : OnCardInfoListener {
                override fun onCardInfo(retCode: Int, info: CardInfoEntity?) {
                    resume(toResult(retCode, info))
                }
                override fun onSwipeIncorrect() =
                    resume(TerminalResult.Failure(TerminalError.Hardware(-1, "Lecture piste incorrecte")))
                override fun onMultipleCards() =
                    resume(TerminalResult.Failure(TerminalError.Unsupported("Plusieurs cartes détectées")))
            })
        }
    }

    private fun toResult(code: Int, info: CardInfoEntity?): TerminalResult<CardData> {
        NexgoErrorMapper.fromCode(code)?.let { return TerminalResult.Failure(it) }
        if (info == null) return TerminalResult.Failure(TerminalError.Hardware(code, "Carte non lue"))
        if (info.cardExistslot != CardSlotTypeEnum.SWIPE) // SDK-VERIFY
            return TerminalResult.Failure(TerminalError.Unsupported("Puce / sans contact : EMV non géré"))
        val t2 = Track2Parser.parse(info.tk2) // SDK-VERIFY
            ?: return TerminalResult.Failure(TerminalError.Hardware(-1, "Piste 2 illisible"))
        return TerminalResult.Success(t2.toCardData())
    }
}