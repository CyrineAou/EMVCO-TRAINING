package com.example.terminalmodule.model

/** Erreurs normalisées : l'UI ne voit jamais un code SDK brut. */
sealed interface TerminalError {
    val message: String

    data object Cancelled : TerminalError { override val message = "Opération annulée" }
    data object Timeout : TerminalError { override val message = "Délai dépassé" }
    data object PaperOut : TerminalError { override val message = "Plus de papier" }
    data object Overheat : TerminalError { override val message = "Imprimante en surchauffe" }
    data object Busy : TerminalError { override val message = "Périphérique occupé" }
    data class Unsupported(override val message: String) : TerminalError
    data class Hardware(val code: Int, override val message: String) : TerminalError
    data class Unexpected(val cause: Throwable) : TerminalError {
        override val message: String get() = cause.message ?: "Erreur inattendue"
    }
    data class InvalidKey(override val message: String) : TerminalError
    data object KeyCheckMismatch : TerminalError { override val message = "KCV incorrect" }
    data class Forbidden(override val message: String) : TerminalError
    data object PinBypassed : TerminalError { override val message = "PIN contourné" }
    data object SessionExpired : TerminalError { override val message = "Session carte expirée : relire la carte" }
}
