package com.example.terminalmodule

import com.example.terminalmodule.internal.PanMasker
import com.example.terminalmodule.internal.Track2Parser
import org.junit.Assert.*
import org.junit.Test

class Track2ParserTest {
    @Test fun parsesPanAndExpiry() {
        val t = Track2Parser.parse(";4111111111111111=25121010000?")
        assertEquals("4111111111111111", t?.pan)
        assertEquals("12/25", t?.expiryMmYy)
    }
    @Test fun masksPan() = assertEquals("411111******1111", PanMasker.mask("4111111111111111"))
    @Test fun rejectsGarbage() = assertNull(Track2Parser.parse("abc"))
}
