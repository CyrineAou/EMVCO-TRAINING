package com.example.terminalmodule.pax.internal

import com.example.terminalmodule.model.TerminalError

/** Seul endroit qui connaît les codes d'erreur Pax. */
internal object PaxErrorMapper {

    /** @return null si succès. SDK-VERIFY : codes de IPrinter.start(). */
    fun printer(status: Int): TerminalError? = when (status) {
        0 -> null
        1 -> TerminalError.Busy
        2 -> TerminalError.PaperOut
        8 -> TerminalError.Overheat
        else -> TerminalError.Hardware(status, "Erreur imprimante Pax ($status)")
    }

    fun fromException(e: Throwable): TerminalError {
        val msg = e.message.orEmpty()
        return if (msg.contains("timeout", ignoreCase = true) || msg.contains("time out", ignoreCase = true))
            TerminalError.Timeout
        else TerminalError.Hardware(-1, msg.ifBlank { e.javaClass.simpleName })
    }
}
