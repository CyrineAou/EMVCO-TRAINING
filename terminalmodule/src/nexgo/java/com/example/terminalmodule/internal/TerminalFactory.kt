package com.example.terminalmodule.nexgo.internal

import android.content.Context
import com.example.terminalmodule.api.TerminalServices

/** Même nom + même package dans src/pax : Gradle ne compile que celle du flavor actif. */
internal object TerminalFactory {
    fun create(context: Context): TerminalServices = NexgoTerminalServices(context)
}
