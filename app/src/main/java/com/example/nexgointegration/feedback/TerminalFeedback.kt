package com.example.nexgointegration.feedback

/**
 * Retour sensoriel du terminal (LED + buzzer).
 * Le reste de l'application ne dépend que de cette interface,
 * pas du SDK du constructeur.
 */
interface TerminalFeedback {
    fun success()
    fun failure()

    /** Éteint les LED et coupe toute séquence en cours. */
    fun reset()
}