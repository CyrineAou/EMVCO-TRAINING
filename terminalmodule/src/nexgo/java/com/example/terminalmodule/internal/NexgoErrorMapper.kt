//package com.example.terminalmodule.internal
//
//import com.example.terminalmodule.model.TerminalError
//import com.nexgo.oaf.apiv3.SdkResult
//import java.lang.reflect.Modifier
//
//internal object NexgoErrorMapper {
//
//    /** @return null si succès. */
//    fun fromCode(code: Int): TerminalError? = when (code) {
//        SdkResult.Success -> null
//        SdkResult.Printer_PaperLack -> TerminalError.PaperOut
//        SdkResult.Printer_TooHot -> TerminalError.Overheat
//        SdkResult.TimeOut -> TerminalError.Timeout
//        else -> {
//            val name = nameOf(code)
//            TerminalLog.w("code SDK Nexgo non mappé : $code (${name ?: "nom inconnu"})")
//            TerminalError.Hardware(code, "Erreur Nexgo $code${name?.let { " ($it)" } ?: ""}")
//        }
//    }
//
//    /** Retrouve le nom de la constante de SdkResult qui porte ce code. */
//    private fun nameOf(code: Int): String? = runCatching {
//        SdkResult::class.java.fields.firstOrNull { f ->
//            Modifier.isStatic(f.modifiers) && f.type == Int::class.javaPrimitiveType && f.getInt(null) == code
//        }?.name
//    }.getOrNull()
//}


package com.example.terminalmodule.internal

import com.example.terminalmodule.model.TerminalError
import com.nexgo.oaf.apiv3.SdkResult
import java.lang.reflect.Modifier

/**
 * Convertit les codes de retour du SDK NEXGO en erreurs du module.
 */
internal object NexgoErrorMapper {

    /**
     * Retourne null lorsque le SDK signale un succès.
     */
    fun fromCode(code: Int): TerminalError? {
        if (code == SdkResult.Success) {
            return null
        }

        val sdkName = nameOf(code)

        TerminalLog.w(
            "Erreur SDK NEXGO : code=$code, " +
                    "constante=${sdkName ?: "non identifiée"}"
        )

        return when (code) {
            SdkResult.Printer_PaperLack ->
                TerminalError.PaperOut

            SdkResult.Printer_TooHot ->
                TerminalError.Overheat

            SdkResult.TimeOut ->
                TerminalError.Timeout

            else ->
                TerminalError.Hardware(
                    code,
                    buildString {
                        append("Erreur Nexgo ")
                        append(code)

                        if (sdkName != null) {
                            append(" (")
                            append(sdkName)
                            append(")")
                        }
                    }
                )
        }
    }

    /**
     * Recherche une constante entière publique de SdkResult
     * correspondant au code reçu.
     */
    private fun nameOf(code: Int): String? {
        return runCatching {
            SdkResult::class.java.fields.firstOrNull { field ->
                Modifier.isStatic(field.modifiers) &&
                        field.type == Int::class.javaPrimitiveType &&
                        field.getInt(null) == code
            }?.name
        }.getOrNull()
    }
}