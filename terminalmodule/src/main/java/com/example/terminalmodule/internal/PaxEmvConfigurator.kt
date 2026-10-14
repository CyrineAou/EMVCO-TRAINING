package com.example.terminalmodule.internal

import com.example.terminalmodule.api.EmvKernelConfigurator
import com.example.terminalmodule.model.EmvAidParam
import com.example.terminalmodule.model.EmvCapkParam
import com.example.terminalmodule.model.EmvTerminalParam

internal class PaxEmvConfigurator : EmvKernelConfigurator {
    override fun loadParameters(
        aids: List<EmvAidParam>,
        capks: List<EmvCapkParam>,
        terminal: EmvTerminalParam
    ): Result<Unit> =
        Result.failure(NotImplementedError("À implémenter avec le SDK PAX"))
}