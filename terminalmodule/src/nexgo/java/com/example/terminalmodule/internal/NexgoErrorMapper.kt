package com.example.terminalmodule.nexgo.internal

import com.example.terminalmodule.model.TerminalError
import com.nexgo.oaf.apiv3.SdkResult

/** Seul endroit qui connaît les codes d'erreur Nexgo. */
internal object NexgoErrorMapper {

    /** @return null si succès. */
    fun fromCode(code: Int): TerminalError? = when (code) {
        SdkResult.Success -> null
        SdkResult.Printer_PaperLack -> TerminalError.PaperOut // SDK-VERIFY
        SdkResult.Printer_TooHot -> TerminalError.Overheat // SDK-VERIFY
        SdkResult.TimeOut -> TerminalError.Timeout // SDK-VERIFY
        else -> TerminalError.Hardware(code, "Erreur Nexgo ($code)")
    }
}
