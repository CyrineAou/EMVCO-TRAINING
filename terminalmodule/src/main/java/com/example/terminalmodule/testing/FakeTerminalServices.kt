package com.example.terminalmodule.testing

import com.example.terminalmodule.api.CardReaderService
import com.example.terminalmodule.api.PrinterService
import com.example.terminalmodule.api.ScannerService
import com.example.terminalmodule.api.TerminalServices
import com.example.terminalmodule.model.CardData
import com.example.terminalmodule.model.DeviceType
import com.example.terminalmodule.model.EntryMode
import com.example.terminalmodule.model.PrintLine
import com.example.terminalmodule.model.TerminalResult
import kotlin.time.Duration

class FakeTerminalServices(
    var cardResult: TerminalResult<CardData> =
        TerminalResult.Success(CardData("411111******1111", "12/30", null, EntryMode.SWIPE)),
    var scanResult: TerminalResult<String> = TerminalResult.Success("1234567890"),
    var printResult: TerminalResult<Unit> = TerminalResult.Success(Unit),
) : TerminalServices {
    val printed = mutableListOf<List<PrintLine>>()

    override val device = DeviceType.FAKE
    override val printer = object : PrinterService {
        override suspend fun print(lines: List<PrintLine>): TerminalResult<Unit> {
            printed += lines; return printResult
        }
    }
    override val cardReader = object : CardReaderService {
        override suspend fun readCard(timeout: Duration) = cardResult
    }
    override val scanner = object : ScannerService {
        override suspend fun scan(timeout: Duration) = scanResult
    }
}
