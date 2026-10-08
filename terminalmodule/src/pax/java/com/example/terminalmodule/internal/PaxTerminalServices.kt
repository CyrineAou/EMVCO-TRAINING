package com.example.terminalmodule.pax.internal

import android.content.Context
import com.example.terminalmodule.api.CardReaderService
import com.example.terminalmodule.api.PrinterService
import com.example.terminalmodule.api.ScannerService
import com.example.terminalmodule.api.TerminalServices
import com.example.terminalmodule.model.DeviceType


internal class PaxTerminalServices(context: Context) : TerminalServices {
    private val dal: IDAL by lazy { NeptuneLiteUser.getInstance().getDal(context) } // SDK-VERIFY

    override val device = DeviceType.PAX
    override val printer: PrinterService by lazy { PaxPrinterService(dal) }
    override val cardReader: CardReaderService by lazy { PaxCardReaderService(dal) }
    override val scanner: ScannerService by lazy { PaxScannerService(dal) }
}
