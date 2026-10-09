package com.example.terminalmodule.internal

import com.example.terminalmodule.model.KeyInjection
import com.example.terminalmodule.model.TerminalError
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal object Kcv {
    /** KCV TDES : 3 premiers octets de TDES-ECB(clé, 8 octets à zéro). */
    fun tdes(key: ByteArray): ByteArray {
        val k24 = when (key.size) {
            16 -> key + key.copyOfRange(0, 8)
            24 -> key
            else -> throw IllegalArgumentException("Clé TDES : 16 ou 24 octets")
        }
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k24, "DESede"))
        return cipher.doFinal(ByteArray(8)).copyOf(3)
    }
}

internal fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }

internal fun String.hexToBytes(): ByteArray {
    val s = filter { !it.isWhitespace() }
    require(s.length % 2 == 0) { "Hex de longueur impaire" }
    return ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

internal object KeyValidator {
    /** @return null si l'injection est acceptable. Aucun message ne contient d'octet de clé. */
    fun validate(i: KeyInjection, debuggable: Boolean): TerminalError? {
        if (i.index !in 0..99) return TerminalError.InvalidKey("Index de clé hors plage")
        return when (i) {
            is KeyInjection.ClearMasterForTest -> {
                if (!debuggable) return TerminalError.Forbidden("Clé en clair interdite hors build debug")
                if (i.key.size != 16 && i.key.size != 24) return TerminalError.InvalidKey("Longueur de clé invalide")
                val expected = i.kcv
                if (expected != null && !Kcv.tdes(i.key).contentEquals(expected.copyOf(3)))
                    TerminalError.KeyCheckMismatch else null
            }
            is KeyInjection.UnderParent -> {
                if (i.parentIndex !in 0..99) return TerminalError.InvalidKey("Index parent hors plage")
                if (i.cryptogram.size != 16 && i.cryptogram.size != 24) TerminalError.InvalidKey("Longueur de cryptogramme invalide")
                else null
            }
        }
    }
}