package com.example.nexgointegration.emvSimulation
import android.os.Handler
import android.os.Looper
import android.util.Log

class MockCardReaderManager : CardReaderManager {

    override fun demarrerLectureCarte(onCardRead: (cardNo: String, expDate: String) -> Unit) {
        Log.d("SIMULATION_CARTE", "=== Début de recherche de carte ===")
        Log.d("SIMULATION_CARTE", "Patientez... Approchez ou insérez une carte.")

        // Simulation d'un délai de 2 secondes (temps que l'utilisateur approche sa carte)
        Handler(Looper.getMainLooper()).postDelayed({

            // Faux numéro de carte et date d'expiration
            val fauxNumeroCarte = "4532 1234 5678 9010"
            val fausseDateExp = "12/28"

            Log.d("SIMULATION_CARTE", ">>> Carte détectée avec succès ! <<<")
            Log.d("SIMULATION_CARTE", "Numéro de Carte (PAN) : $fauxNumeroCarte")
            Log.d("SIMULATION_CARTE", "Date d'expiration      : $fausseDateExp")
            Log.d("SIMULATION_CARTE", "=== Fin de lecture ===")

            // Transmettre les données lues
            onCardRead(fauxNumeroCarte, fausseDateExp)

        }, 2000)
    }
}