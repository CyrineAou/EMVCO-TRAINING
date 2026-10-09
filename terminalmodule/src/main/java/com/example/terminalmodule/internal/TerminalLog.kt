package com.example.terminalmodule.internal

import android.util.Log

internal object TerminalLog {
    private const val TAG = "TerminalTest"

    /** Activé par Terminal.init() uniquement si l'app est debuggable. */
    @Volatile var verbose: Boolean = false

    /** Redirige aussi les logs vers l'écran (utilisé par MainActivity). */
    @Volatile var sink: ((String) -> Unit)? = null

    fun d(msg: String) {
        if (verbose) {
            Log.d(TAG, msg)
            sink?.invoke("D  $msg")
        }
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
        sink?.invoke("I  $msg")
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
        sink?.invoke("W  $msg")
    }

    fun e(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
        sink?.invoke("E  $msg")
    }

    /** Identifiant de session raccourci pour les logs. */
    fun short(id: String) = id.take(8)
}