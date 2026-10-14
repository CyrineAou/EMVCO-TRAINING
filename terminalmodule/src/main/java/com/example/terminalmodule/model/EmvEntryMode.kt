package com.example.terminalmodule.model

enum class EmvEntryMode { CONTACT, CONTACTLESS }

data class EmvPaymentRequest(
    val amountInCents: String,
    val currencyCode: String = "0788",
    val countryCode: String = "0788",
    val transactionType: Int = 0x00,
    val terminalId: String = "NEXGO001",
    val merchantId: String = "MERCHANT123"
)

/** Données envoyées à l'hôte pour l'autorisation en ligne (tags EMV en hexa). */
data class EmvOnlineRequest(
    val entryMode: EmvEntryMode,
    val tags: Map<String, String>
)

/**
 * Réponse de l'hôte. field55Hex doit contenir le tag 8A (code réponse) ;
 * s'il est absent, 8A est déduit de [approved].
 */
data class EmvOnlineResponse(
    val approved: Boolean,
    val authCode: String? = null,
    val rejectCode: String = "00",
    val field55Hex: String? = null
)

data class EmvPaymentResult(
    val code: Int,
    val approved: Boolean,
    val tags: Map<String, String>
)