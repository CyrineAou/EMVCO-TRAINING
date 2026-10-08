package com.example.terminalmodule.internal

import com.example.terminalmodule.model.CardData
import com.example.terminalmodule.model.EntryMode


internal data class Track2(val pan: String, val expiryMmYy: String?)

/** Logique partagée par Pax et Nexgo : écrite une seule fois, testable sans terminal. */
internal object Track2Parser {
    fun parse(raw: String?): Track2? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().removePrefix(";").removeSuffix("?")
        val sep = s.indexOfFirst { it == '=' || it == 'D' || it == 'd' }
        if (sep < 12) return null
        val pan = s.substring(0, sep)
        if (!pan.all(Char::isDigit)) return null
        val exp = s.substring(sep + 1).take(4)
        val expiry = if (exp.length == 4 && exp.all(Char::isDigit)) "${exp.substring(2)}/${exp.substring(0, 2)}" else null
        return Track2(pan, expiry)
    }
}

internal object PanMasker {
    fun mask(pan: String): String =
        if (pan.length < 10) "*".repeat(pan.length)
        else pan.take(6) + "*".repeat(pan.length - 10) + pan.takeLast(4)
}

internal fun Track2.toCardData(mode: EntryMode = EntryMode.SWIPE) =
    CardData(PanMasker.mask(pan), expiryMmYy, holderName = null, entryMode = mode)
