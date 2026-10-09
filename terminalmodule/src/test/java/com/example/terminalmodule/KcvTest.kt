package com.example.terminalmodule

import com.example.terminalmodule.internal.Kcv
import com.example.terminalmodule.internal.hexToBytes
import com.example.terminalmodule.internal.toHex
import org.junit.Assert.assertEquals
import org.junit.Test

class KcvTest {
    @Test fun kcvOfZeroKey() = assertEquals("8CA64D", Kcv.tdes(ByteArray(16)).toHex())
    @Test fun hexRoundTrip() = assertEquals("0A1B2C", "0a1b2c".hexToBytes().toHex())
}