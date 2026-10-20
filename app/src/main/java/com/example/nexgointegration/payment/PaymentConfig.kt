package com.example.nexgointegration.payment

/** Paramètres fixes du paiement de démonstration. */
object PaymentConfig {

    /** Montant prédéfini, en centimes (comme attendu par PaymentManager.startPayment). */
    const val AMOUNT_IN_CENTS = 100L

    private const val CURRENCY_SYMBOL = "€"

    /** Montant lisible pour l'affichage : 100 centimes -> "1,00 €". */
    val displayLabel: String
        get() = "%d,%02d %s".format(AMOUNT_IN_CENTS / 100, AMOUNT_IN_CENTS % 100, CURRENCY_SYMBOL)
}