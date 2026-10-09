package com.example.terminalmodule.internal

import com.example.terminalmodule.api.CardReaderService
import com.example.terminalmodule.model.CardData
import com.example.terminalmodule.model.TerminalError
import com.example.terminalmodule.model.TerminalResult
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener
import kotlin.time.Duration

/** Piste magnétique uniquement. Puce / sans contact : brancher le noyau EMV ici. */
internal class NexgoCardReaderService(private val engine: DeviceEngine) : CardReaderService {

    override suspend fun readCard(timeout: Duration): TerminalResult<CardData> = terminalCall {
        val reader = engine.cardReader
        TerminalLog.i("readCard : timeout=${timeout.inWholeSeconds}s, slots=SWIPE+ICC1+RF")
        awaitCallback<CardData>(
            name = "readCard",
            onCancel = { TerminalLog.d("stopSearch"); reader.stopSearch() },
        ) { resume ->
            val slots = hashSetOf(CardSlotTypeEnum.SWIPE, CardSlotTypeEnum.ICC1, CardSlotTypeEnum.RF)
            TerminalLog.d("searchCard appelé")
            reader.searchCard(slots, timeout.inWholeSeconds.toInt(), object : OnCardInfoListener {
                override fun onCardInfo(retCode: Int, info: CardInfoEntity?) {
                    // Jamais le contenu de la piste : seulement sa longueur.
                    val detail = if (info == null) "info=null"
                    else "slot=${info.cardExistslot}, tk2=${info.tk2?.length ?: 0} car."
                    TerminalLog.d("onCardInfo retCode=$retCode, $detail")
                    resume(toResult(retCode, info))
                }

                override fun onSwipeIncorrect() {
                    TerminalLog.w("onSwipeIncorrect : lecture de piste incorrecte")
                    resume(TerminalResult.Failure(TerminalError.Hardware(-1, "Lecture piste incorrecte")))
                }

                override fun onMultipleCards() {
                    TerminalLog.w("onMultipleCards : plusieurs cartes détectées")
                    resume(TerminalResult.Failure(TerminalError.Unsupported("Plusieurs cartes détectées")))
                }
            })
        }
    }

    private fun toResult(code: Int, info: CardInfoEntity?): TerminalResult<CardData> {
        NexgoErrorMapper.fromCode(code)?.let { return TerminalResult.Failure(it) }
        if (info == null) {
            TerminalLog.w("carte non lue : info nulle malgré retCode=$code")
            return TerminalResult.Failure(TerminalError.Hardware(code, "Carte non lue"))
        }
        if (info.cardExistslot != CardSlotTypeEnum.SWIPE) { // SDK-VERIFY
            TerminalLog.w("carte ${info.cardExistslot} : EMV non géré par ce module")
            return TerminalResult.Failure(TerminalError.Unsupported("Puce / sans contact : EMV non géré"))
        }
        val t2 = Track2Parser.parse(info.tk2) // SDK-VERIFY
        if (t2 == null) {
            TerminalLog.w("piste 2 illisible (${info.tk2?.length ?: 0} car.)")
            return TerminalResult.Failure(TerminalError.Hardware(-1, "Piste 2 illisible"))
        }
        val card = t2.toCardData()
        TerminalLog.i("carte lue : ${card.maskedPan}, exp=${card.expiry ?: "?"}, mode=${card.entryMode}")
        return TerminalResult.Success(card)
    }
}