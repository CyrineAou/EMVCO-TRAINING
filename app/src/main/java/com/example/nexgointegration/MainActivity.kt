package com.example.nexgointegration

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.nexgointegration.emvSimulation.CardReaderManager
import com.example.nexgointegration.emvSimulation.MockCardReaderManager
import com.example.nexgointegration.emvSimulation.NexgoCardReaderManager


class MainActivity : ComponentActivity() {

        // 💡 Passez à 'false' lorsque vous testerez sur un vrai terminal Nexgo
        private val EST_EMULATEUR = true

        private lateinit var cardReaderManager: CardReaderManager

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            // Sélection dynamique
            cardReaderManager = if (EST_EMULATEUR) {
                MockCardReaderManager()
            } else {
                NexgoCardReaderManager(this)
            }

            val btnLireCarte = findViewById<Button>(R.id.btnLireCarte)
            btnLireCarte.setOnClickListener {

                // Lancer la lecture
                cardReaderManager.demarrerLectureCarte { carte, expiration ->
                    // Message visuel rapide à l'écran
                    Toast.makeText(this, "Carte lue: $carte", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
