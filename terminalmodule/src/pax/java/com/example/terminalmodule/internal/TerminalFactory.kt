package com.example.terminalmodule.internal

import android.content.Context
import com.example.terminalmodule.api.TerminalServices
import com.example.terminalmodule.pax.internal.PaxTerminalServices

/** Même nom + même package dans src/nexgo : Gradle ne compile que celle du flavor actif. */
internal object TerminalFactory {
    fun create(context: Context): TerminalServices = PaxTerminalServices(
        context,
        pinPad = TODO()
    )
}
