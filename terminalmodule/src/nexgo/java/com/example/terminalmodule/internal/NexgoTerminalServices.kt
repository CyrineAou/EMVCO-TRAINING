package com.example.terminalmodule.internal

import android.content.Context
import com.example.terminalmodule.api.CardReaderService
import com.example.terminalmodule.api.PinPadService
import com.example.terminalmodule.api.PrinterService
import com.example.terminalmodule.api.ScannerService
import com.example.terminalmodule.api.TerminalServices
import com.example.terminalmodule.model.DeviceType
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.DeviceEngine

internal class NexgoTerminalServices(context: Context) : TerminalServices {
    private val appContext = context.applicationContext

    private val engine: DeviceEngine by lazy {
        TerminalLog.i("APIProxy.getDeviceEngine ...")
        try {
            APIProxy.getDeviceEngine(appContext).also { TerminalLog.i("DeviceEngine obtenu") }
        } catch (e: Throwable) {
            TerminalLog.e("DeviceEngine indisponible : ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
    }

    override val device = DeviceType.NEXGO
    override val printer: PrinterService by lazy { NexgoPrinterService(engine) }
    override val cardReader: CardReaderService by lazy { NexgoCardReaderService(engine) }
    override val scanner: ScannerService by lazy { NexgoScannerService(engine) }
    override val pinPad: PinPadService by lazy { NexgoPinPadService(engine, appContext) }
}