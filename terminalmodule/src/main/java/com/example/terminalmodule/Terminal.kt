package com.example.terminalmodule

import android.content.Context
import android.content.pm.ApplicationInfo
import com.example.terminalmodule.api.TerminalServices
import com.example.terminalmodule.internal.TerminalFactory
import com.example.terminalmodule.internal.TerminalLog

object Terminal {
    @Volatile private var instance: TerminalServices? = null

    val services: TerminalServices
        get() = checkNotNull(instance) { "Terminal.init(context) doit être appelé avant usage" }

    fun init(context: Context) {
        if (instance != null) {
            TerminalLog.d("init ignoré : déjà initialisé")
            return
        }
        synchronized(this) {
            if (instance != null) return
            val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            TerminalLog.verbose = debuggable
            TerminalLog.i("init : debuggable=$debuggable")
            try {
                instance = TerminalFactory.create(context.applicationContext)
                TerminalLog.i("init OK : device=${instance?.device}")
            } catch (e: Throwable) {
                TerminalLog.e("init a échoué : ${e.javaClass.simpleName}: ${e.message}", e)
                throw e
            }
        }
    }

    fun override(services: TerminalServices) {
        TerminalLog.w("override : terminal remplacé par ${services.device}")
        instance = services
    }
}