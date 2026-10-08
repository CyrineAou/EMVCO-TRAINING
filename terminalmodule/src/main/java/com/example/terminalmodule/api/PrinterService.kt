package com.example.terminalmodule.api

import com.example.terminalmodule.model.PrintLine
import com.example.terminalmodule.model.TerminalResult

interface PrinterService {
    suspend fun print(lines: List<PrintLine>): TerminalResult<Unit>
}
