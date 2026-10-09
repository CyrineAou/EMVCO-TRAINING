package com.example.terminalmodule.internal

import com.example.terminalmodule.api.PrinterService
import com.example.terminalmodule.model.Align
import com.example.terminalmodule.model.FontSize
import com.example.terminalmodule.model.PrintLine
import com.example.terminalmodule.model.TerminalResult
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.device.printer.AlignEnum

internal class NexgoPrinterService(private val engine: DeviceEngine) : PrinterService {

    override suspend fun print(lines: List<PrintLine>): TerminalResult<Unit> = terminalCall {
        TerminalLog.i("print : ${lines.size} éléments")
        awaitCallback<Unit>(name = "print") { resume ->
            val p = engine.printer
            p.initPrinter()
            TerminalLog.d("initPrinter OK")
            lines.forEachIndexed { i, line ->
                // Le contenu n'est pas loggé : un reçu peut contenir des données de carte.
                when (line) {
                    is PrintLine.Text -> {
                        TerminalLog.d("  [$i] Text ${line.value.length} car., taille=${line.size}, gras=${line.bold}")
                        p.appendPrnStr(line.value, line.size.px(), line.align.toNexgo(), line.bold)
                    }
                    is PrintLine.Image -> {
                        TerminalLog.d("  [$i] Image ${line.bitmap.width}x${line.bitmap.height}")
                        p.appendImage(line.bitmap, line.align.toNexgo()) // SDK-VERIFY
                    }
                    is PrintLine.Feed -> {
                        TerminalLog.d("  [$i] Feed ${line.lines}")
                        repeat(line.lines) { p.appendPrnStr(" ", 24, AlignEnum.LEFT, false) }
                    }
                }
            }
            TerminalLog.d("startPrint appelé")
            p.startPrint(true) { code ->
                TerminalLog.i("print : retCode=$code")
                val error = NexgoErrorMapper.fromCode(code)
                resume(if (error == null) TerminalResult.Success(Unit) else TerminalResult.Failure(error))
            }
        }
    }

    private fun FontSize.px() = when (this) {
        FontSize.SMALL -> 16
        FontSize.NORMAL -> 24
        FontSize.LARGE -> 32
    }

    private fun Align.toNexgo() = when (this) {
        Align.LEFT -> AlignEnum.LEFT
        Align.CENTER -> AlignEnum.CENTER
        Align.RIGHT -> AlignEnum.RIGHT
    }
}