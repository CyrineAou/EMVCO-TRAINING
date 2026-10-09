package com.example.terminalmodule.model

/** Le PAN complet ne sort jamais de la couche L3 : seule la version masquée est exposée. */
data class CardData(
    val maskedPan: String,
    val expiry: String?,
    val holderName: String?,
    val entryMode: EntryMode,
    val sessionId: String? = null,
)

enum class EntryMode { SWIPE, CHIP, CONTACTLESS }
