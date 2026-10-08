package com.example.terminalmodule.pax.internal

import android.content.Context
import com.example.terminalmodule.api.TerminalServices

/** Même nom + même package dans src/nexgo : Gradle ne compile que celle du flavor actif. */
internal object TerminalFactory {
    fun create(context: Context): TerminalServices = PaxTerminalServices(context)
}
