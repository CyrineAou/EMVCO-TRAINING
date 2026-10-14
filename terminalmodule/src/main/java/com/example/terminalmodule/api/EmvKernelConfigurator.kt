package com.example.terminalmodule.api

import com.example.terminalmodule.model.EmvAidParam
import com.example.terminalmodule.model.EmvCapkParam
import com.example.terminalmodule.model.EmvTerminalParam

interface EmvKernelConfigurator {
    fun loadParameters(
        aids: List<EmvAidParam>,
        capks: List<EmvCapkParam>,
        terminal: EmvTerminalParam
    ): Result<Unit>
}