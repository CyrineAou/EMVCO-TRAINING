package com.example.terminalmodule

import android.content.Context
import com.example.terminalmodule.api.TerminalServices
import com.example.terminalmodule.nexgo.internal.TerminalFactory

/** Seul objet public de configuration. Appeler [init] dans Application.onCreate(). */
object Terminal {
    @Volatile private var instance: TerminalServices? = null

    val services: TerminalServices
        get() = checkNotNull(instance) { "Terminal.init(context) doit être appelé dans Application.onCreate()" }

    fun init(context: Context) {
        if (instance != null) return
        synchronized(this) {
            if (instance == null) instance = TerminalFactory.create(context.applicationContext)
        }
    }

    /** Pour les tests / previews : injecter un faux terminal. */
    fun override(services: TerminalServices) { instance = services }
}
