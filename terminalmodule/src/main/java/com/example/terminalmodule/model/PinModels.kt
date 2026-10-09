package com.example.terminalmodule.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class KeyKind { MASTER, PIN, DATA, MAC }
enum class PinBlockFormat { ISO_0, ISO_1, ISO_3 }

/** Comment une clé entre dans le terminal. toString ne révèle jamais la clé. */
sealed class KeyInjection(val kind: KeyKind, val index: Int, val kcv: ByteArray?) {

    /** Clé chiffrée sous une clé parente déjà présente (ex. clé PIN sous TMK). Seule forme valable en production. */
    class UnderParent(
        kind: KeyKind, index: Int,
        val parentIndex: Int,
        val cryptogram: ByteArray,
        kcv: ByteArray? = null,
    ) : KeyInjection(kind, index, kcv)

    /** Clé MAÎTRE en clair : build debuggable et clé de test uniquement. */
    class ClearMasterForTest(
        index: Int,
        val key: ByteArray,
        kcv: ByteArray? = null,
    ) : KeyInjection(KeyKind.MASTER, index, kcv)

    override fun toString() = "${javaClass.simpleName}(kind=$kind, index=$index, key=***)"
}

data class PinRequest(
    val sessionId: String,
    val pinKeyIndex: Int,
    val format: PinBlockFormat = PinBlockFormat.ISO_0,
    val minLength: Int = 4,
    val maxLength: Int = 12,
    val allowBypass: Boolean = false,
    val timeout: Duration = 60.seconds,
)

class EncryptedPin(val block: ByteArray, val format: PinBlockFormat) {
    override fun toString() = "EncryptedPin(format=$format, ${block.size} octets)"
}