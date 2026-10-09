package com.example.terminalmodule.testing

import com.example.terminalmodule.api.CardReaderService
import com.example.terminalmodule.api.PinPadService
import com.example.terminalmodule.api.PrinterService
import com.example.terminalmodule.api.ScannerService
import com.example.terminalmodule.api.TerminalServices
import com.example.terminalmodule.model.CardData
import com.example.terminalmodule.model.DeviceType
import com.example.terminalmodule.model.EncryptedPin
import com.example.terminalmodule.model.EntryMode
import com.example.terminalmodule.model.KeyInjection
import com.example.terminalmodule.model.PinBlockFormat
import com.example.terminalmodule.model.PinRequest
import com.example.terminalmodule.model.PrintLine
import com.example.terminalmodule.model.TerminalResult
import kotlin.time.Duration

class FakeTerminalServices(
    var cardResult: TerminalResult<CardData> =
        TerminalResult.Success(CardData("411111******1111", "12/30", null, EntryMode.CONTACTLESS)),
    var scanResult: TerminalResult<String> = TerminalResult.Success("1234567890"),
    var printResult: TerminalResult<Unit> = TerminalResult.Success(Unit),
    var pinResult: TerminalResult<EncryptedPin> =
        TerminalResult.Success(EncryptedPin(ByteArray(8), PinBlockFormat.ISO_0))



) : TerminalServices {
    val printed = mutableListOf<List<PrintLine>>()

    override val device = DeviceType.FAKE
    override val printer = object : PrinterService {
        override suspend fun print(lines: List<PrintLine>): TerminalResult<Unit> {
            printed += lines; return printResult
        }
    }

    override val pinPad = object : PinPadService {
        override suspend fun injectKey(injection: KeyInjection) = TerminalResult.Success(Unit)
        override suspend fun requestPin(request: PinRequest) = pinResult
    }
    override val cardReader = object : CardReaderService {
        override suspend fun readCard(timeout: Duration) = cardResult
    }
    override val scanner = object : ScannerService {
        override suspend fun scan(timeout: Duration) = scanResult
    }
}
