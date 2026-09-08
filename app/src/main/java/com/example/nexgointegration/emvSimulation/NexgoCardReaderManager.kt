package com.example.nexgointegration.emvSimulation


import android.content.Context
import android.util.Log
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener

class NexgoCardReaderManager(context: Context) : CardReaderManager {

    private val deviceEngine = APIProxy.getDeviceEngine(context)
    private val cardReader = deviceEngine?.cardReader

    override fun demarrerLectureCarte(onCardRead: (cardNo: String, expDate: String) -> Unit) {
        Log.d("NEXGO_CARTE", "Démarrage du lecteur...")

        val slots = HashSet<CardSlotTypeEnum>()
        slots.add(CardSlotTypeEnum.ICC1) // Carte à puce
        slots.add(CardSlotTypeEnum.RF)   // Carte sans contact / NFC

        cardReader?.searchCard(slots, 60, object : OnCardInfoListener {

            // 1. Succès ou échec de lecture
            override fun onCardInfo(retCode: Int, cardInfo: CardInfoEntity?) {
                if (retCode == 0 && cardInfo != null) {
                    val cardNumber = cardInfo.cardNo ?: "Inconnu"
                    val expDate = cardInfo.expiredDate ?: "Inconnu"

                    Log.d("NEXGO_CARTE", "Carte lue: $cardNumber - Exp: $expDate")
                    onCardRead(cardNumber, expDate)
                } else {
                    Log.e("NEXGO_CARTE", "Erreur de lecture, code : $retCode")
                }
            }

            // 2. Glissement de bande magnétique incorrect (Obligatoire)
            override fun onSwipeIncorrect() {
                Log.w("NEXGO_CARTE", "Lecture de la bande magnétique échouée")
            }

            // 3. Plusieurs cartes sans contact détectées simultanément (Obligatoire)
            override fun onMultipleCards() {
                Log.w("NEXGO_CARTE", "Plusieurs cartes sans contact détectées en même temps")
            }

            // 4. Temps écoulé sans présentation de carte (Obligatoire)
//            override fun onSwipeCardTimeout() {
//                Log.w("NEXGO_CARTE", "Temps écoulé (Timeout)")
//            }

        })
    }
}