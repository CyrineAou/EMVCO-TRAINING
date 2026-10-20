package com.example.nexgointegration

import DataConverter.hexToBytes
import DataConverter.toHex
import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.nexgointegration.gateway.FakePaymentGateway
import com.example.nexgointegration.parameters.EmvKernelSelfCheck
import com.example.nexgointegration.parameters.WorldlineEmvParametersProvider
import com.example.terminalmodule.Terminal
import com.example.terminalmodule.internal.CardSessionStore
import com.example.terminalmodule.internal.PanMode
import com.example.terminalmodule.internal.PinSdkOptions
import com.example.terminalmodule.internal.SdkInspector
import com.example.terminalmodule.model.*
import com.example.worldlineintegrationsdk.WorldlineSync
import com.magellan.tapandgo.validatorsdk.T2UOpenPaymentSDK.sendWaitingTaps
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class MainActivity : Activity() {

    private companion object {
        const val TAG = "TerminalTest"
        const val CALLBACK_TAG = "PAYMENT_CALLBACK"
        const val EMV_TAG = "EMV_TEST"
        const val REQ_CAMERA = 42
        const val MAX_LOG_CHARS = 40_000
    }

    private val scope = MainScope()

    private var job: Job? = null
    private var pendingAfterPermission: (() -> Unit)? = null
    private var paymentManager: PaymentManager? = null

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var amountInput: EditText

    private val PIN_PAD_CLASS =
        "com.nexgo.oaf.apiv3.device.pinpad.PinPad"

    // Suivi de la transaction
    private var paymentStartedAt = 0L
    private var paymentAttempt = 0
    private var currentPaymentAmount = ""
    private var paymentInProgress = false
    private var lastEntryMode: String? = null
    private var lastPaymentSummary: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildUi())

        Log.d(EMV_TAG, "MainActivity started")
        log("Démarrage")

        initTerminal()

        scope.launch {
            try {
                WorldlineSync(applicationContext).run()
                    .onFailure {
                        log("Worldline sync échouée : ${it.message}", it)
                    }
            } catch (e: Exception) {
                log("Erreur synchronisation Worldline : ${e.message}", e)
            }

            initPayment()
        }
    }

    override fun onDestroy() {
        try {
            paymentManager?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur destroy PaymentManager", e)
        }

        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------
    // Initialisation
    // ---------------------------------------------------------

    private fun initTerminal() {
        log(
            "Appareil : ${Build.MANUFACTURER} ${Build.MODEL}, " +
                    "Android ${Build.VERSION.SDK_INT}"
        )

        try {
            Terminal.init(applicationContext)

            val device = Terminal.services.device
            setStatus("Terminal : $device")
            log("Terminal.init OK : $device")
        } catch (e: Throwable) {
            setStatus("Erreur initialisation terminal")
            log(
                "Terminal.init échoué : ${e.javaClass.simpleName}: ${e.message}",
                e
            )
        }
    }

    private fun initPayment() {
        try {
            paymentManager = PaymentManager(
                context = this,
                parametersProvider = WorldlineEmvParametersProvider(this),
                authorizer = GatewayOnlineAuthorizer(FakePaymentGateway()),
                listener = object : PaymentManager.Listener {

                    override fun onWaitingForCard() {
                        paymentCallback("MANAGER_INITIALIZED",
                            "PaymentManager initialisé")
                        setStatus("Prêt : tu peux démarrer le paiement")
                    }

                    override fun onCardDetected(entryMode: EmvEntryMode) {
                        lastEntryMode = entryMode.toString()

                        paymentCallback(
                            "CARD_DETECTED",
                            "Mode de lecture=$lastEntryMode"
                        )

                        setStatus("Carte détectée")
                    }

                    override fun onTransactionFinished(result: EmvPaymentResult) {
                        val info = EmvRequestedInfoMapper.build(
                            result,
                            (paymentManager ?: return).parametersProvider.getAids()
                        )
                        val duration = elapsedPaymentMs()
                        paymentInProgress = false

                        val state = if (result.approved) "APPROVED" else "DECLINED_OR_FAILED"

                        fun getEmvTag(tags: Map<String, String>, tag: String): String? =
                            tags.entries
                                .firstOrNull { it.key.equals(tag, ignoreCase = true) }
                                ?.value
                                ?.takeIf { it.isNotBlank() }
                        fun maskPan(pan: String?): String {
                            if (pan.isNullOrBlank()) return "Non disponible"
                            val digits = pan.filter { it.isDigit() }
                            if (digits.length < 10) return "Donnée masquée"
                            return "${digits.take(6)}******${digits.takeLast(4)}"
                        }

                        fun logEmvRequestedInfo(tags: Map<String, String>) {
                            fun value(tag: String): String =
                                getEmvTag(tags, tag) ?: "Non disponible"

                            // 5A est maintenant fourni par le service (kernel, ou dérivé du Track 2)
                            val pan = getEmvTag(tags, "5A")?.replace("F", "", ignoreCase = true)

                            Log.i("WORLDLINE_EMV", "========== DONNÉES EMV ==========")
                            Log.i("WORLDLINE_EMV", "PAN = ${maskPan(pan)}")
                            Log.i("WORLDLINE_EMV", "transactionDate (9A) = ${value("9A")}")
                            Log.i("WORLDLINE_EMV", "transactionTime (9F21) = ${value("9F21")}")
                            Log.i(
                                "WORLDLINE_EMV",
                                "track2 (57) = ${
                                    if (getEmvTag(
                                            tags,
                                            "57"
                                        ) != null
                                    ) "Disponible (masqué)" else "Non disponible"
                                }"
                            )
                            Log.i("WORLDLINE_EMV", "cardSequenceNumber (5F34) = ${value("5F34")}")
                            Log.i("WORLDLINE_EMV", "otherAmount (9F03) = ${value("9F03")}")
                            Log.i("WORLDLINE_EMV", "terminalCapabilities (9F33) = ${value("9F33")}")
                            Log.i("WORLDLINE_EMV", "amountAuthorized (9F02) = ${value("9F02")}")
                            Log.i("WORLDLINE_EMV", "terminalCountryCode (9F1A) = ${value("9F1A")}")
                            Log.i("WORLDLINE_EMV", "cardExpirationDate (5F24) = ${value("5F24")}")
                            Log.i("WORLDLINE_EMV", "AIP (82) = ${value("82")}")
                            Log.i("WORLDLINE_EMV", "TVR (95) = ${value("95")}")
                            Log.i("WORLDLINE_EMV", "TSI (9B) = ${value("9B")}")
                            Log.i("WORLDLINE_EMV", "IAD (9F10) = ${value("9F10")}")
                            Log.i("WORLDLINE_EMV", "AC (9F26) = ${value("9F26")}")
                            Log.i("WORLDLINE_EMV", "CID (9F27) = ${value("9F27")}")
                            Log.i("WORLDLINE_EMV", "ATC (9F36) = ${value("9F36")}")
                            Log.i("WORLDLINE_EMV", "UN (9F37) = ${value("9F37")}")
                            Log.i("WORLDLINE_EMV", "EMV_TERM_TYPE (9F35) = ${value("9F35")}")
                            Log.i("WORLDLINE_EMV", "APPL_VER_NUM (9F08) = ${value("9F08")}")
                            Log.i("WORLDLINE_EMV", "DFName (84) = ${value("84")}")
                            Log.i("WORLDLINE_EMV", "TERM_SER_NUM (9F1E) = ${value("9F1E")}")
                            Log.i("WORLDLINE_EMV", "CVMResults (9F34) = ${value("9F34")}")
                            Log.i("WORLDLINE_EMV", "TTQ (9F66) = ${value("9F66")}")
                            Log.i("WORLDLINE_EMV", "formFactorIndicator (9F6E) = ${value("9F6E")}")
                            Log.i(
                                "WORLDLINE_EMV",
                                "applicationUsageControl (9F07) = ${value("9F07")}"
                            )
                            Log.i("WORLDLINE_EMV", "Tags reçus = ${tags.keys.joinToString(", ")}")
                            Log.i("WORLDLINE_EMV", "=================================")
                        }
                        Log.i("WORLDLINE_EMV", "RÉSULTAT code=${result.code} approved=${result.approved} mode=${lastEntryMode ?: "inconnu"}")
                        // UN SEUL appel (l'ancien code l'appelait deux fois)
                        logEmvRequestedInfo(result.tags)

                        callSendWaitingTaps("fin de paiement, code=${result.code}, approved=${result.approved}")

                        // Conserver le résultat pour consultation ultérieure.
                        lastPaymentSummary = buildString {
                            appendLine("Tentative : $paymentAttempt")
                            appendLine("Montant : $currentPaymentAmount centimes")
                            appendLine("État : $state")
                            appendLine("Code retour : ${result.code}")
                            appendLine("Mode de lecture : ${lastEntryMode ?: "inconnu"}")
                            appendLine("Durée : ${duration} ms")
                            appendLine("TVR (95) : ${result.tags["95"] ?: "absent"}")
                            appendLine("TSI (9B) : ${result.tags["9B"] ?: "absent"}")
                            append("Date : ${now()}")
                        }

                        paymentCallback(
                            "TRANSACTION_FINISHED",
                            "tentative=$paymentAttempt, " +
                                    "montant=$currentPaymentAmount centimes, " +
                                    "état=$state, code=${result.code}, " +
                                    "mode=${lastEntryMode ?: "inconnu"}, " +
                                    "durée=${duration}ms"
                        )

                        log("========== RÉSULTAT PAIEMENT ==========")
                        lastPaymentSummary?.lines()?.forEach { log(it) }
                        log("=======================================")

                        log(
                            "[EMV] TVR(95)=${result.tags["95"] ?: "absent"} " +
                                    "TSI(9B)=${result.tags["9B"] ?: "absent"}"
                        )

                        setStatus(
                            if (result.approved) {
                                "Paiement terminé : accepté"
                            } else {
                                "Paiement terminé : refusé ou échoué"
                            }
                        )

                        scope.launch {
                            val start = SystemClock.elapsedRealtime()
                            try {
                                withContext(Dispatchers.IO) {
                                    sendWaitingTaps()   // à remplacer par ton instance de T2UOpenPaymentSDK
                                }
                                val ms = SystemClock.elapsedRealtime() - start
                                paymentCallback("WAITING_TAPS", "sendWaitingTaps terminé sans exception (${ms} ms)")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                paymentCallback("WAITING_TAPS_ERROR", "${e.javaClass.simpleName}: ${e.message}")
                                log("sendWaitingTaps a échoué : ${e.message}", e)
                            }
                        }
                    }

                    override fun onError(message: String) {
                        val duration = elapsedPaymentMs()
                        paymentInProgress = false

                        lastPaymentSummary = buildString {
                            appendLine("Tentative : $paymentAttempt")
                            appendLine("Montant : $currentPaymentAmount centimes")
                            appendLine("État : ERROR")
                            appendLine("Erreur : $message")
                            appendLine("Durée : ${duration} ms")
                            append("Date : ${now()}")
                        }

                        paymentCallback(
                            "ERROR",
                            "$message, durée=${duration}ms"
                        )

                        setStatus("Erreur de paiement")
                    }
                }
            )

            paymentCallback(
                "MANAGER_INITIALIZED",
                "PaymentManager initialisé"
            )
        } catch (e: Throwable) {
            paymentCallback(
                "INITIALIZATION_ERROR",
                "${e.javaClass.simpleName}: ${e.message}"
            )

            log("Erreur PaymentManager : ${e.message}", e)
        }
    }

    // ---------------------------------------------------------
    // Résultat du paiement
    // ---------------------------------------------------------

    private fun paymentCallback(event: String, details: String) {
        val message = "[PAYMENT][$event] $details"
        Log.i(CALLBACK_TAG, message)
        appendLine(message)
    }

    private fun elapsedPaymentMs(): Long {
        if (paymentStartedAt == 0L) return 0L
        return SystemClock.elapsedRealtime() - paymentStartedAt
    }

    private fun showPaymentState() {
        paymentCallback(
            "CURRENT_STATE",
            "tentative=$paymentAttempt, " +
                    "montant=$currentPaymentAmount, " +
                    "enCours=$paymentInProgress, " +
                    "durée=${elapsedPaymentMs()}ms"
        )
    }

    private fun showLastPaymentResult() {
        val summary = lastPaymentSummary

        if (summary == null) {
            log("Aucun résultat de paiement disponible")
            return
        }

        log("========== DERNIER RÉSULTAT ==========")
        summary.lines().forEach { log(it) }
        log("======================================")
    }

    // ---------------------------------------------------------
    // Démarrage et annulation du paiement
    // ---------------------------------------------------------

    private fun startPayment() {
        val pm = paymentManager ?: run {
            log("PaymentManager non initialisé")
            return
        }

        if (paymentInProgress) {
            log("Un paiement est déjà en cours")
            return
        }

        val amount = amountInput.text.toString().trim()
        val amountValue = amount.toLongOrNull()

        if (amountValue == null || amountValue <= 0L) {
            log("Montant invalide : saisis un entier positif en centimes")
            return
        }

        paymentAttempt++
        currentPaymentAmount = amount
        paymentStartedAt = SystemClock.elapsedRealtime()
        paymentInProgress = true
        lastEntryMode = null
        lastPaymentSummary = null

        paymentCallback(
            "START",
            "tentative=$paymentAttempt, montant=$amount centimes"
        )

        setStatus("Démarrage du paiement : $amount centimes")

        try {
            pm.startPayment(amount)

            paymentCallback(
                "START_RETURNED",
                "startPayment a rendu la main ; attente des callbacks"
            )
        } catch (e: Exception) {
            paymentInProgress = false

            paymentCallback(
                "START_EXCEPTION",
                "${e.javaClass.simpleName}: ${e.message}"
            )

            log("Erreur startPayment : ${e.message}", e)
            setStatus("Erreur au démarrage du paiement")
        }
    }

    private fun cancelPayment() {
        try {
            paymentManager?.cancel()
            paymentInProgress = false

            paymentCallback(
                "CANCEL_REQUESTED",
                "Demande d'annulation envoyée"
            )

            setStatus("Annulation demandée")
        } catch (e: Exception) {
            log("Erreur annulation : ${e.message}", e)
        }
    }

    // ---------------------------------------------------------
    // Périphériques
    // ---------------------------------------------------------

    private fun testPrinter() = runTest("Impression") {
        Terminal.services.printer.print(
            listOf(
                PrintLine.Text(
                    "TEST TERMINAL",
                    bold = true,
                    size = FontSize.LARGE,
                    align = Align.CENTER
                ),
                PrintLine.Text("Date : ${now()}"),
                PrintLine.Feed(3)
            )
        )
    }

    private fun testCardReader() =
        runTest("Lecture carte") {
            Terminal.services.cardReader.readCard().map { card ->
                "${card.maskedPan} exp ${card.expiry ?: "?"} (${card.entryMode})"
            }
        }

    private fun testScanner() = ensureCameraPermission {
        runTest("Scan") {
            Terminal.services.scanner.scan()
        }
    }

    // ---------------------------------------------------------
    // Paramètres EMV
    // ---------------------------------------------------------

    private fun testEmvParameters() =
        runPlain("Paramètres EMV vers kernel L2") {
            val pm = paymentManager
                ?: error("PaymentManager non initialisé")

            val provider = pm.parametersProvider

            val report = EmvKernelSelfCheck.run(
                aids = provider.getAids(),
                capks = provider.getCapks(),
                terminal = provider.getTerminalParam(),
                configurator = pm.emvKernelConfigurator,
                ignored = provider.ignoredCapks
            )

            report.lines.forEach { log(it) }
            check(report.passed) { "Des tests ont échoué ; consulter le journal" }

            "Tous les tests sont OK"
        }

    private fun showEmvParameters() =
        runPlain("Afficher les paramètres EMV") {
            val provider = (
                    paymentManager ?: error("PaymentManager non initialisé")
                    ).parametersProvider

            val aids = provider.getAids()
            val capks = provider.getCapks()
            val terminal = provider.getTerminalParam()

            aids.forEach {
                log(
                    "AID ${it.aid}, version=${it.appVersion}, " +
                            "TAC denial=${it.tacDenial}, online=${it.tacOnline}, " +
                            "default=${it.tacDefault}, floor=${it.floorLimit}"
                )
            }

            capks.forEach {
                log(
                    "CAPK ${it.rid}/${it.index}, " +
                            "modulus=${it.modulus.length / 2} octets, " +
                            "expiration=${it.expireDate}"
                )
            }

            log(
                "Terminal : contact=${terminal.contactCapabilities}, " +
                        "contactless=${terminal.contactlessCapabilities}, " +
                        "TTQ=${terminal.contactlessTtq}"
            )

            "${aids.size} AID et ${capks.size} CAPK"
        }

    // ---------------------------------------------------------
    // PIN pad : clés de test
    // ---------------------------------------------------------

    private object TestKeys {
        const val PAN = "4111111111111111"
        const val TMK = "0123456789ABCDEFFEDCBA9876543210"
        const val TMK_KCV = "08D7B4"
        const val PIN_KEY_CLEAR = "11223344556677889900AABBCCDDEEFF"
        const val PIN_KEY_UNDER_TMK = "3EB3B72576BBBE834F0B96AAA5A8B548"
        const val PIN_KEY_KCV = "5ED7EA"
        const val MASTER_INDEX = 1
        const val PIN_INDEX = 1
        const val TEST_PIN = "1234"
    }

    private suspend fun injectTestKeys(): TerminalResult<Unit> {
        val pinPad = Terminal.services.pinPad

        val master = pinPad.injectKey(
            KeyInjection.ClearMasterForTest(
                TestKeys.MASTER_INDEX,
                TestKeys.TMK.hexToBytes(),
                TestKeys.TMK_KCV.hexToBytes()
            )
        )

        if (master is TerminalResult.Failure) return master

        return pinPad.injectKey(
            KeyInjection.UnderParent(
                KeyKind.PIN,
                TestKeys.PIN_INDEX,
                TestKeys.MASTER_INDEX,
                TestKeys.PIN_KEY_UNDER_TMK.hexToBytes(),
                TestKeys.PIN_KEY_KCV.hexToBytes()
            )
        )
    }

    private suspend fun pinTest(): TerminalResult<String> {
        log("Réglages PIN : ${PinSdkOptions.describe()}")

        val sessionId = CardSessionStore.put(TestKeys.PAN)

        return Terminal.services.pinPad.requestPin(
            PinRequest(
                sessionId = sessionId,
                pinKeyIndex = TestKeys.PIN_INDEX
            )
        ).map { verifyPinBlock(it) }
    }

    private fun verifyPinBlock(enc: EncryptedPin): String {
        if (enc.block.size != 8) {
            return "Bloc invalide : ${enc.block.size} octets au lieu de 8"
        }

        val format = PinSdkOptions.forceFormat ?: enc.format
        val key = TestKeys.PIN_KEY_CLEAR.hexToBytes()
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding")

        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key + key.copyOf(8), "DESede")
        )

        val clear = cipher.doFinal(enc.block)

        val field = if (format == PinBlockFormat.ISO_1) {
            clear
        } else {
            val panField = (
                    "0000" + TestKeys.PAN.dropLast(1).takeLast(12)
                    ).hexToBytes()

            ByteArray(8) {
                (clear[it].toInt() xor panField[it].toInt()).toByte()
            }
        }

        val hex = field.toHex()

        val expectedControl = when (format) {
            PinBlockFormat.ISO_0 -> '0'
            PinBlockFormat.ISO_1 -> '1'
            PinBlockFormat.ISO_3 -> '3'
        }

        if (hex[0] != expectedControl) {
            return "Bloc invalide : format, clé ou PAN différents"
        }

        val length = hex[1].digitToIntOrNull(16)
            ?: return "Bloc invalide : longueur illisible"

        if (length !in 4..12) {
            return "Bloc invalide : longueur $length"
        }

        val pin = hex.substring(2, 2 + length)

        return if (pin == TestKeys.TEST_PIN) {
            "Bloc valide ; PIN de test conforme"
        } else {
            "Le PIN décodé ne correspond pas"
        }
    }

    private fun testInjectKeys() =
        runTest("Injection des clés de test") { injectTestKeys() }

    private fun testPinWithTestPan() =
        runTest("Saisie PIN de test") { pinTest() }

    private fun testInjectThenPin() =
        runTest("Injection puis saisie PIN") {
            PinSdkOptions.reset()

            val injected = injectTestKeys()
            if (injected is TerminalResult.Failure) return@runTest injected

            pinTest()
        }

    private fun testPinRealCard() =
        runTest("PIN avec carte") {
            val card = Terminal.services.cardReader.readCard()

            if (card !is TerminalResult.Success) return@runTest card

            val sessionId = card.value.sessionId
                ?: return@runTest TerminalResult.Failure(
                    TerminalError.SessionExpired
                )

            Terminal.services.pinPad.requestPin(
                PinRequest(
                    sessionId = sessionId,
                    pinKeyIndex = TestKeys.PIN_INDEX
                )
            )
        }

    // ---------------------------------------------------------
    // Variantes PIN
    // ---------------------------------------------------------

    private data class PinVariant(
        val label: String,
        val format: PinBlockFormat?,
        val pan: PanMode,
        val bytes: Boolean
    )

    private val pinVariants = listOf(
        PinVariant("ISO-1 sans PAN", PinBlockFormat.ISO_1, PanMode.FULL, false),
        PinVariant("ISO-0 PAN en octets", null, PanMode.FULL, true),
        PinVariant("ISO-0 PAN sans chiffre de contrôle", null, PanMode.NO_CHECK, false),
        PinVariant("ISO-0 PAN 12 chiffres", null, PanMode.ISO12, false)
    )

    private var pinVariantIndex = 0
    private var algoModeIndex = 0
    private var lengthIndex = 0
    private var keyboardIndex = 0

    private fun testPinNextVariant() {
        if (busy()) return

        val variant = pinVariants[pinVariantIndex % pinVariants.size]
        pinVariantIndex++

        PinSdkOptions.reset()
        PinSdkOptions.forceFormat = variant.format
        PinSdkOptions.panMode = variant.pan
        PinSdkOptions.panAsBytes = variant.bytes

        log("Variante PIN $pinVariantIndex : ${variant.label}")
        testPinWithTestPan()
    }

    private fun testPinNextAlgorithmMode() {
        if (busy()) return

        val names = SdkInspector.enumValuesOf(
            PIN_PAD_CLASS,
            "setAlgorithmMode"
        )

        if (names.isEmpty()) {
            log("setAlgorithmMode introuvable")
            return
        }

        val name = names[algoModeIndex % names.size]
        algoModeIndex++

        PinSdkOptions.reset()
        PinSdkOptions.presets["setAlgorithmMode"] = name

        runTest("Injection + PIN, setAlgorithmMode($name)") {
            val injected = injectTestKeys()
            if (injected is TerminalResult.Failure) return@runTest injected
            pinTest()
        }
    }

    private val lengthVariants = listOf(
        listOf(4),
        listOf(4, 5, 6),
        listOf(0, 4, 5, 6, 7, 8, 9, 10, 11, 12)
    )

    private fun testPinNextLengths() {
        if (busy()) return

        val lengths = lengthVariants[lengthIndex % lengthVariants.size]
        lengthIndex++

        PinSdkOptions.reset()
        PinSdkOptions.lengthOverride = lengths

        log("Longueurs PIN $lengthIndex : $lengths")
        testPinWithTestPan()
    }

    private val keyboardPresets = listOf(
        "setPinKeyboardMode" to "FIXED",
        "setPinKeyboardMode" to "RANDOM",
        "setPinKeyboardViewMode" to "VIEW_1",
        "setPinKeyboardClearMode" to "BACKSPACE",
        "setPinKeyboardPinMaskMode" to "VISIBLE",
        "setCipherMode" to "ECB"
    )

    private fun testPinNextKeyboardSetting() {
        if (busy()) return

        val (method, value) =
            keyboardPresets[keyboardIndex % keyboardPresets.size]
        keyboardIndex++

        PinSdkOptions.reset()
        PinSdkOptions.presets[method] = value

        runTest("Injection + PIN, $method($value)") {
            val injected = injectTestKeys()
            if (injected is TerminalResult.Failure) return@runTest injected
            pinTest()
        }
    }

    private fun resetPinSettings() {
        PinSdkOptions.reset()
        lengthIndex = 0
        keyboardIndex = 0
        log("Réglages PIN réinitialisés")
    }

    // ---------------------------------------------------------
    // Diagnostics PIN pad
    // ---------------------------------------------------------

    private fun diagnosticPinPadMethods() {
        log("Diagnostic des méthodes et codes PIN pad")
        SdkInspector.dump(PIN_PAD_CLASS)
        SdkInspector.dump(
            "com.nexgo.oaf.apiv3.device.pinpad.PinAlgorithmModeEnum"
        )
        SdkInspector.dump(
            "com.nexgo.oaf.apiv3.device.pinpad.WorkKeyTypeEnum"
        )
        SdkInspector.dump(
            "com.nexgo.oaf.apiv3.SdkResult",
            constantPrefix = "PinPad"
        )
    }

    private fun diagnosticPinPadSettings() {
        SdkInspector.dumpSetterEnums(
            PIN_PAD_CLASS,
            listOf(
                "setAlgorithmMode",
                "setCipherMode",
                "setPinKeyboardMode",
                "setPinKeyboardViewMode",
                "setPinKeyboardClearMode",
                "setPinKeyboardPinMaskMode"
            )
        )
    }

    // ---------------------------------------------------------
    // Permissions et exécution des tests
    // ---------------------------------------------------------

    private fun ensureCameraPermission(onGranted: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
        } else {
            pendingAfterPermission = onGranted
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                REQ_CAMERA
            )
        }
    }

    override fun onRequestPermissionsResult(
        code: Int,
        perms: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(code, perms, results)

        if (code == REQ_CAMERA &&
            results.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            pendingAfterPermission?.invoke()
        } else {
            log("Permission caméra refusée")
        }

        pendingAfterPermission = null
    }

    private fun busy(): Boolean {
        val running = job?.isActive == true

        if (running) {
            log("Une opération est déjà en cours")
        }

        return running
    }

    private fun cancelCurrent() {
        job?.cancel()
        cancelPayment()
    }

    private fun <T> runTest(
        label: String,
        block: suspend () -> TerminalResult<T>
    ) {
        if (busy()) return

        log("Démarrage : $label")
        setStatus("En cours : $label")

        job = scope.launch {
            val start = SystemClock.elapsedRealtime()

            try {
                when (val result = block()) {
                    is TerminalResult.Success -> {
                        log(
                            "Succès $label " +
                                    "(${SystemClock.elapsedRealtime() - start} ms): " +
                                    "${result.value}"
                        )
                    }

                    is TerminalResult.Failure -> {
                        log(
                            "Échec $label " +
                                    "(${SystemClock.elapsedRealtime() - start} ms): " +
                                    "${result.error}"
                        )
                    }
                }
            } catch (e: CancellationException) {
                log("Opération annulée : $label")
                throw e
            } catch (e: Throwable) {
                log(
                    "Erreur $label : ${e.javaClass.simpleName}: ${e.message}",
                    e
                )
            } finally {
                resetStatus()
            }
        }
    }

    private fun runPlain(label: String, block: suspend () -> String) {
        if (busy()) return

        log("Démarrage : $label")
        setStatus("En cours : $label")

        job = scope.launch {
            val start = SystemClock.elapsedRealtime()

            try {
                val message = withContext(Dispatchers.IO) { block() }

                log(
                    "Succès $label " +
                            "(${SystemClock.elapsedRealtime() - start} ms) : $message"
                )
            } catch (e: CancellationException) {
                log("Opération annulée : $label")
                throw e
            } catch (e: Throwable) {
                log(
                    "Erreur $label : ${e.javaClass.simpleName}: ${e.message}",
                    e
                )
            } finally {
                resetStatus()
            }
        }
    }

    private fun resetStatus() {
        setStatus(
            "Terminal : " +
                    (runCatching { Terminal.services.device }.getOrNull() ?: "?")
        )
    }
    private fun callSendWaitingTaps(reason: String) {
        scope.launch {
            val start = SystemClock.elapsedRealtime()
            Log.i("WORLDLINE_EMV", "sendWaitingTaps : appel ($reason)")
            log("sendWaitingTaps : appel ($reason)")

            try {
                withContext(Dispatchers.IO) {
                    sendWaitingTaps()
                }
                val ms = SystemClock.elapsedRealtime() - start
                Log.i("WORLDLINE_EMV", "sendWaitingTaps : terminé sans exception (${ms} ms)")
                log("sendWaitingTaps : terminé sans exception (${ms} ms)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val ms = SystemClock.elapsedRealtime() - start
                Log.e("WORLDLINE_EMV", "sendWaitingTaps : ÉCHEC après ${ms} ms", e)
                log("sendWaitingTaps : ÉCHEC ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }
    }
    // ---------------------------------------------------------
    // Journal
    // ---------------------------------------------------------

    private fun log(msg: String, error: Throwable? = null) {
        Log.i(TAG, msg, error)

        if (
            msg.startsWith("[OK]") ||
            msg.startsWith("[FAIL]") ||
            msg.startsWith("[SKIP]") ||
            msg.startsWith("===")
        ) {
            Log.d(EMV_TAG, msg)
        }

        appendLine("A  $msg")
    }

    private fun appendLine(text: String) {
        runOnUiThread {
            if (!::logView.isInitialized) return@runOnUiThread

            logView.append("[${now()}] $text\n")

            if (logView.length() > MAX_LOG_CHARS) {
                logView.text = logView.text.takeLast(MAX_LOG_CHARS / 2)
            }

            if (::logScroll.isInitialized) {
                logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun setStatus(text: String) {
        runOnUiThread {
            if (::statusView.isInitialized) statusView.text = text
        }
    }

    private fun copyLog() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("journal", logView.text)
        )

        Toast.makeText(this, "Journal copié", Toast.LENGTH_SHORT).show()
    }

    private fun clearLog() {
        logView.text = ""
        log("Journal effacé")
    }

    private fun now(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            .format(Date())

    private fun diagnosticEmvCodes() {
        SdkInspector.dump("com.nexgo.oaf.apiv3.SdkResult")
    }

    // ---------------------------------------------------------
    // Interface
    // ---------------------------------------------------------

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        statusView = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            text = "Terminal : …"
        }

        root.addView(statusView)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        buttons.addView(section("1. Périphériques"))
        buttons.addView(button("Imprimer un ticket test", ::testPrinter))
        buttons.addView(button("Lire une carte", ::testCardReader))
        buttons.addView(button("Scanner un code", ::testScanner))

        buttons.addView(section("2. Paramètres EMV"))
        buttons.addView(
            button("Envoyer au kernel L2 + auto-test", ::testEmvParameters)
        )
        buttons.addView(
            button("Afficher AID / CAPK lus", ::showEmvParameters)
        )

        buttons.addView(section("3. Paiement EMV et résultats"))

        amountInput = EditText(this).apply {
            hint = "Montant en centimes"
            setText("1000")
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        buttons.addView(amountInput)
        buttons.addView(button("Démarrer le paiement", ::startPayment))
        buttons.addView(button("Afficher l'état du paiement", ::showPaymentState))
        buttons.addView(button("Afficher le dernier résultat", ::showLastPaymentResult))
        buttons.addView(button("Annuler le paiement", ::cancelPayment))

        buttons.addView(section("4. PIN pad : tests de base"))
        buttons.addView(button("Injecter les clés de test", ::testInjectKeys))
        buttons.addView(button("Saisie PIN + vérification", ::testPinWithTestPan))
        buttons.addView(button("Injection + saisie PIN", ::testInjectThenPin))
        buttons.addView(button("PIN avec une carte", ::testPinRealCard))

        buttons.addView(section("5. PIN pad : variantes"))
        buttons.addView(button("Longueurs PIN suivantes", ::testPinNextLengths))
        buttons.addView(button("Réglage clavier suivant", ::testPinNextKeyboardSetting))
        buttons.addView(button("Variante PAN / format suivante", ::testPinNextVariant))
        buttons.addView(button("Mode algorithme suivant", ::testPinNextAlgorithmMode))
        buttons.addView(button("Réinitialiser les réglages PIN", ::resetPinSettings))

        buttons.addView(section("6. Diagnostic"))
        buttons.addView(button("Réglages du PIN pad", ::diagnosticPinPadSettings))
        buttons.addView(button("Méthodes et codes du PIN pad", ::diagnosticPinPadMethods))
        buttons.addView(button("Codes SDK Nexgo", ::diagnosticEmvCodes))

        buttons.addView(section("0. Écran de paiement"))
        buttons.addView(
            button("Ouvrir l'écran de paiement (100)") {
                startActivity(Intent(this, PaymentActivity::class.java))
            }
        )

        root.addView(
            ScrollView(this).apply { addView(buttons) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        )

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL

                addView(
                    button("Annuler", ::cancelCurrent),
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                )

                addView(
                    button("Copier le journal", ::copyLog),
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                )

                addView(
                    button("Effacer", ::clearLog),
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                )
            }
        )

        logView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }

        logScroll = ScrollView(this).apply {
            addView(logView)
        }

        root.addView(
            logScroll,
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        )

        return root
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 16, 0, 4)
    }

    private fun button(
        text: String,
        onClick: () -> Unit
    ) = Button(this).apply {
        this.text = text
        layoutParams = LinearLayout.LayoutParams(
            MATCH_PARENT,
            WRAP_CONTENT
        )
        setOnClickListener { onClick() }
    }
}
