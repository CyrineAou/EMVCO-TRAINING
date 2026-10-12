package com.example.terminalmodule.internal

import com.example.terminalmodule.model.TerminalError
import com.nexgo.oaf.apiv3.SdkResult
import java.lang.reflect.Modifier

/** Convertit les codes de retour du SDK Nexgo (et nos codes locaux) en erreurs du module. */
internal object NexgoErrorMapper {

    /** Codes produits par notre propre code, jamais par le SDK. */
    const val LOCAL_KEY_MISSING = -90001
    const val LOCAL_INVALID_BLOCK = -90002

    /** @return null si succès. */
    fun fromCode(code: Int): TerminalError? {
        if (code == SdkResult.Success) return null

        when (code) {
            LOCAL_KEY_MISSING -> {
                TerminalLog.w("Erreur locale : clé maître ou clé PIN absente du terminal")
                return TerminalError.InvalidKey("Clé maître ou clé PIN absente du terminal")
            }
            LOCAL_INVALID_BLOCK -> {
                TerminalLog.w("Erreur locale : bloc PIN invalide")
                return TerminalError.Hardware(code, "Bloc PIN invalide (vide, tout à zéro ou taille incorrecte)")
            }
        }

        val sdkName = nameOf(code)
        TerminalLog.w("Erreur SDK Nexgo : code=$code, constante=${sdkName ?: "non identifiée"}")

        return when (code) {
            SdkResult.Printer_PaperLack -> TerminalError.PaperOut
            SdkResult.Printer_TooHot -> TerminalError.Overheat
            SdkResult.TimeOut -> TerminalError.Timeout
            else -> TerminalError.Hardware(
                code,
                buildString {
                    append("Erreur Nexgo ").append(code)
                    if (sdkName != null) append(" (").append(sdkName).append(")")
                }
            )
        }
    }

    /** Retrouve le nom de la constante de SdkResult qui porte ce code. */
    private fun nameOf(code: Int): String? = runCatching {
        SdkResult::class.java.fields.firstOrNull { f ->
            Modifier.isStatic(f.modifiers) &&
                    f.type == Int::class.javaPrimitiveType &&
                    f.getInt(null) == code
        }?.name
    }.getOrNull()
}