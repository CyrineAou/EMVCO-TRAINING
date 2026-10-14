package com.example.nexgointegration.parameters

import com.example.terminalmodule.model.EmvAidParam
import com.example.terminalmodule.model.EmvCapkParam
import com.example.terminalmodule.model.EmvTerminalParam

interface EmvParametersProvider {
    fun getAids(): List<EmvAidParam>
    fun getCapks(): List<EmvCapkParam>
    fun getTerminalParam(): EmvTerminalParam

    /** CAPK écartées à la lecture (checksum invalide, doublon), pour les logs. */
    val ignoredCapks: List<String> get() = emptyList()
}