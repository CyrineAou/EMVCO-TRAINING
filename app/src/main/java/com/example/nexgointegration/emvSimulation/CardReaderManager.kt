package com.example.nexgointegration.emvSimulation

interface CardReaderManager {
    fun demarrerLectureCarte(onCardRead: (cardNo: String, expDate: String) -> Unit)
}