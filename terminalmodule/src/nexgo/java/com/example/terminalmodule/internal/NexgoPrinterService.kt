package com.example.terminalmodule.nexgo.internal

import com.example.terminalmodule.api.PrinterService
import com.example.terminalmodule.internal.awaitCallback
import com.example.terminalmodule.internal.terminalCall
import com.example.terminalmodule.model.Align
import com.example.terminalmodule.model.FontSize
import com.example.terminalmodule.model.PrintLine
import com.example.terminalmodule.model.TerminalResult
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.device.printer.AlignEnum


internal class NexgoPrinterService(private val engine: DeviceEngine) : PrinterService {

    override suspend fun print(lines: List<PrintLine>): TerminalResult<Unit> = terminalCall {
        awaitCallback<Unit> { resume ->
            val p = engine.printer
            p.initPrinter()
            lines.forEach { line ->
                when (line) {
                    is PrintLine.Text -> p.appendPrnStr(line.value, line.size.px(), line.align.toNexgo(), line.bold)
                    is PrintLine.Image -> p.appendImage(line.bitmap, line.align.toNexgo()) // SDK-VERIFY
                    is PrintLine.Feed -> repeat(line.lines) { p.appendPrnStr(" ", 24, AlignEnum.LEFT, false) }
                }
            }
            p.startPrint(true) { code ->
                val error = NexgoErrorMapper.fromCode(code)
                resume(if (error == null) TerminalResult.Success(Unit) else TerminalResult.Failure(error))
            }
        }
    }

    private fun FontSize.px() = when (this) { FontSize.SMALL -> 16; FontSize.NORMAL -> 24; FontSize.LARGE -> 32 }
    private fun Align.toNexgo() = when (this) {
        Align.LEFT -> AlignEnum.LEFT
        Align.CENTER -> AlignEnum.CENTER
        Align.RIGHT -> AlignEnum.RIGHT
    }
}
