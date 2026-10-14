package com.example.terminalmodule.testing

import com.example.terminalmodule.api.EmvKernelConfigurator
import com.example.terminalmodule.model.EmvAidParam
import com.example.terminalmodule.model.EmvCapkParam
import com.example.terminalmodule.model.EmvTerminalParam

class MockEmvKernelConfigurator : EmvKernelConfigurator {
    var aids: List<EmvAidParam> = emptyList()
    var capks: List<EmvCapkParam> = emptyList()
    var terminal: EmvTerminalParam? = null

    override fun loadParameters(
        aids: List<EmvAidParam>,
        capks: List<EmvCapkParam>,
        terminal: EmvTerminalParam
    ): Result<Unit> {
        this.aids = aids
        this.capks = capks
        this.terminal = terminal
        return Result.success(Unit)
    }
}