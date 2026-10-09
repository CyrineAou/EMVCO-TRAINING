package com.example.terminalmodule.internal

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Garde le PAN le temps d'une transaction. Usage unique, expiration 2 minutes. */
internal object CardSessionStore {
    private class Entry(val pan: CharArray, val expiresAtNanos: Long)

    private const val TTL_NANOS = 120_000_000_000L
    private val entries = ConcurrentHashMap<String, Entry>()

    fun put(pan: String): String {
        purgeExpired()
        val id = UUID.randomUUID().toString()
        entries[id] = Entry(pan.toCharArray(), System.nanoTime() + TTL_NANOS)
        TerminalLog.d("session créée id=${TerminalLog.short(id)} (actives=${entries.size})")
        return id
    }

    /** Rend le PAN une seule fois puis l'efface. */
    fun take(id: String): String? {
        val e = entries.remove(id)
        if (e == null) {
            TerminalLog.w("session id=${TerminalLog.short(id)} introuvable (déjà utilisée ou inconnue)")
            return null
        }
        val valid = System.nanoTime() < e.expiresAtNanos
        val pan = if (valid) String(e.pan) else null
        e.pan.fill('0')
        if (valid) TerminalLog.d("session id=${TerminalLog.short(id)} consommée")
        else TerminalLog.w("session id=${TerminalLog.short(id)} expirée")
        return pan
    }

    fun clear() {
        entries.values.forEach { it.pan.fill('0') }
        entries.clear()
        TerminalLog.d("sessions effacées")
    }

    private fun purgeExpired() {
        val now = System.nanoTime()
        val before = entries.size
        entries.entries.removeIf { (_, e) -> (now >= e.expiresAtNanos).also { if (it) e.pan.fill('0') } }
        if (entries.size != before) TerminalLog.d("sessions expirées purgées : ${before - entries.size}")
    }
}