package com.example.terminalmodule.nexgo.internal


import android.content.Context
import com.example.terminalmodule.api.CardReaderService
import com.example.terminalmodule.api.PrinterService
import com.example.terminalmodule.api.ScannerService
import com.example.terminalmodule.api.TerminalServices
import com.example.terminalmodule.internal.NexgoCardReaderService
import com.example.terminalmodule.internal.NexgoScannerService
import com.example.terminalmodule.model.DeviceType
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.DeviceEngine

internal class NexgoTerminalServices(context: Context) : TerminalServices {
    private val engine: DeviceEngine by lazy { APIProxy.getDeviceEngine(context) }
    override val device = DeviceType.NEXGO
    override val printer: PrinterService by lazy { NexgoPrinterService(engine) }
    override val cardReader: CardReaderService by lazy { NexgoCardReaderService(engine) }
    override val scanner: ScannerService by lazy { NexgoScannerService(engine) }
}
