package com.example.terminalmodule.pax.internal

import com.example.terminalmodule.api.PrinterService
import com.example.terminalmodule.internal.terminalCall
import com.example.terminalmodule.model.FontSize
import com.example.terminalmodule.model.PrintLine
import com.example.terminalmodule.model.TerminalResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/** Limites Pax : pas de gras ni d'alignement natifs (ignorés). */
internal class PaxPrinterService(private val dal: IDAL) : PrinterService {

    override suspend fun print(lines: List<PrintLine>): TerminalResult<Unit> = terminalCall {
        withContext(Dispatchers.IO) {
            try {
                val p = dal.printer
                p.init()
                lines.forEach { line ->
                    when (line) {
                        is PrintLine.Text -> {
                            applyFont(p, line.size)
                            p.printStr(line.value + "\n", null)
                        }
                        is PrintLine.Image -> p.printBitmap(line.bitmap)
                        is PrintLine.Feed -> p.step(line.lines * 24)
                    }
                }
                val error = PaxErrorMapper.printer(p.start())
                if (error == null) TerminalResult.Success(Unit) else TerminalResult.Failure(error)
            } catch (e: Exception) {
                TerminalResult.Failure(PaxErrorMapper.fromException(e))
            }
        }
    }

    private fun applyFont(p: IPrinter, size: FontSize) {
        val (ascii, ext) = when (size) {
            FontSize.SMALL -> EFontTypeAscii.FONT_8_16 to EFontTypeExtCode.FONT_16_16
            FontSize.NORMAL -> EFontTypeAscii.FONT_12_24 to EFontTypeExtCode.FONT_24_24
            FontSize.LARGE -> EFontTypeAscii.FONT_16_32 to EFontTypeExtCode.FONT_32_32
        }
        p.fontSet(ascii, ext)
    }
}
