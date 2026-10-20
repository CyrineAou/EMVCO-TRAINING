package com.example.nexgointegration.payment

/**
 * Tout ce que l'écran de paiement peut afficher.
 * L'UI ne connaît que ces états : jamais le PaymentManager ni l'EMV.
 */
sealed interface PaymentUiState {
    /** Le terminal s'initialise. */
    data object Preparing : PaymentUiState

    /** On attend que le client présente sa carte sans contact. */
    data object WaitingForCard : PaymentUiState

    /** Carte détectée, la transaction est en cours. */
    data object Processing : PaymentUiState

    data object Success : PaymentUiState

    data class Failure(val reason: String) : PaymentUiState

    /** Annulation volontaire : pas de bip d'échec. */
    data object Cancelled : PaymentUiState
}