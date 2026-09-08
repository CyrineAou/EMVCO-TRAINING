package com.example.nexgointegration

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.device.beeper.Beeper
import com.nexgo.oaf.apiv3.device.pinpad.OnPinPadInputListener
import com.nexgo.oaf.apiv3.device.pinpad.PinAlgorithmModeEnum
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.device.reader.CardSlotTypeEnum
import com.nexgo.oaf.apiv3.device.reader.OnCardInfoListener
import com.nexgo.oaf.apiv3.emv.*
import java.util.HashSet

class MainActivity : ComponentActivity() {

    private lateinit var deviceEngine: DeviceEngine
    private lateinit var emvHandler: EmvHandler2
    private lateinit var statusTextView: TextView
    private lateinit var pinMaskTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)

        deviceEngine = APIProxy.getDeviceEngine(this)
        emvHandler = deviceEngine.getEmvHandler2("Main-Kernel")

        startBancaireWorkflow("1000")
    }

    private fun startBancaireWorkflow(amountInCents: String) {
        runOnUiThread { statusTextView.text = "Veuillez insérer ou approcher votre carte..." }

        val cardReader = deviceEngine.getCardReader()
        val slotTypes = HashSet<CardSlotTypeEnum>().apply {
            add(CardSlotTypeEnum.ICC1) // Carte à puce
            add(CardSlotTypeEnum.RF)   // Sans contact
        }

        // Correction de OnCardInfoListener avec toutes ses méthodes requises
        cardReader.searchCard(slotTypes, 30, object : OnCardInfoListener {
            override fun onCardInfo(retCode: Int, cardInfoEntity: CardInfoEntity?) {
                if (retCode == 0 && cardInfoEntity != null) {
                    runEmvKernel(amountInCents)
                } else {
                    runOnUiThread { statusTextView.text = "Erreur lecture carte: $retCode" }
                }
            }

            override fun onSwipeIncorrect() {
                runOnUiThread { statusTextView.text = "Lecture incorrecte, réessayez" }
            }

            override fun onMultipleCards() {
                runOnUiThread { statusTextView.text = "Plusieurs cartes détectées" }
            }
        })
    }

    private fun runEmvKernel(amountInCents: String) {
        val transParam = EmvTransConfigurationEntity().apply {
            transAmount = amountInCents
            emvTransType = 0x00.toByte() // 0x00 = Vente
            termId = "NEXGO001"
            merId = "MERCHANT123"
            countryCode = "0788"        // Code ISO pays (ex: 0788 Tunisie)
            currencyCode = "0788"       // Code ISO devise
        }

        emvHandler.emvProcess(transParam, object : OnEmvProcessListener2 {

            override fun onSelApp(
                appNameList: MutableList<String>?,
                candidateList: MutableList<CandidateAppInfoEntity>?,
                isMustSelect: Boolean
            ) {
                emvHandler.onSetSelAppResponse(0)
            }

            override fun onConfirmCardNo(cardInfo: CardInfoEntity?) {
                emvHandler.onSetConfirmCardNoResponse(true)
            }

            override fun onTransInitBeforeGPO() {
                emvHandler.onSetTransInitBeforeGPOResponse(true)
            }

            override fun onCardHolderInputPin(isOnlinePin: Boolean, remainingTries: Int) {
                runOnUiThread { statusTextView.text = "Veuillez saisir votre code PIN..." }

                val pinPad = deviceEngine.getPinPad()
                val listener = object : OnPinPadInputListener {
                    override fun onInputResult(result: Int, pinBlock: ByteArray?) {
                        runOnUiThread { statusTextView.text = "Traitement en cours..." }
                        val success = (result == 0 && pinBlock != null)
                        emvHandler.onSetPinInputResponse(success, false)
                    }

                    override fun onSendKey(keyCode: Byte) {
                        runOnUiThread {
                            pinMaskTextView.append("*")
                            deviceEngine.beeper.beep(10,10)
                        }
                    }
                }

                val keyIndexArray = intArrayOf(1)

                if (isOnlinePin) {
                    val pan = emvHandler.getEmvCardDataInfo()?.cardNo ?: ""
                    val panBytes = pan.toByteArray()

                    pinPad.inputOnlinePin(
                        keyIndexArray,
                        keyIndexArray.size,
                        panBytes,
                        panBytes.size,
                        PinAlgorithmModeEnum.ISO9564FMT0,
                        listener
                    )
                } else {
                    pinPad.inputOfflinePin(keyIndexArray, keyIndexArray.size, listener)
                }
            }

            override fun onContactlessTapCardAgain() {
                runOnUiThread { statusTextView.text = "Veuillez re-présenter la carte" }
                emvHandler.onSetContactlessTapCardResponse(true)
            }

            override fun onOnlineProc() {
                runOnUiThread { statusTextView.text = "Autorisation bancaire en cours..." }

                Thread {
                    Thread.sleep(1500)

                    val resultEntity = EmvOnlineResultEntity().apply {
                        rejCode = "00"
                        authCode = "AUTH88"
                        recvField55 = byteArrayOf(0x8A.toByte(), 0x02, 0x30, 0x30)
                    }

                    emvHandler.onSetOnlineProcResponse(0, resultEntity)
                }.start()
            }

            override fun onPrompt(prompt: PromptEnum?) {
                emvHandler.onSetPromptResponse(true)
            }

            override fun onRemoveCard() {
                runOnUiThread { statusTextView.text = "Veuillez retirer la carte" }
                emvHandler.onSetRemoveCardResponse()
            }

            override fun onFinish(result: Int, resultEntity: EmvProcessResultEntity?) {
                runOnUiThread {
                    if (result == 0) {
                        statusTextView.text = "PAIEMENT ACCEPTÉ"
                    } else {
                        statusTextView.text = "PAIEMENT REFUSÉ ($result)"
                    }
                }
            }
        })
    }
}