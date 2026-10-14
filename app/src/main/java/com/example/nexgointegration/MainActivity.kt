package com.example.nexgointegration

import DataConverter.hexToBytes
import DataConverter.toHex
import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import com.example.worldlineintegrationsdk.FakeWorldlineSdk
import com.example.worldlineintegrationsdk.WorldlineSync
import com.example.worldlineintegrationsdk.dispatcher.SdkTableDispatcher
import com.example.worldlineintegrationsdk.sdk.SdkTableAdapter
import com.nexgo.oaf.apiv3.device.reader.CardInfoEntity
import com.nexgo.oaf.apiv3.emv.EmvProcessResultEntity
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class MainActivity : Activity() {

    private companion object {
        const val TAG = "TerminalTest"
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
    val PIN_PAD_CLASS = "com.nexgo.oaf.apiv3.device.pinpad.PinPad"

    // ───────────── Lifecycle ─────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildUi())

        Log.d(EMV_TAG, ">>> MainActivity started")
        log("Démarrage")
        initTerminal()
        // dans MainActivity, à la place de l'appel direct à initPayment() dans onCreate :
        scope.launch {
            WorldlineSync(applicationContext).run()
                .onFailure { log("✖ Worldline sync: ${it.message}", it) }
            initPayment()
        }
    }

    override fun onDestroy() {
        try {
            paymentManager?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "destroy PaymentManager", e)
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun initTerminal() {
        log("Appareil : ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.SDK_INT})")
        try {
            Terminal.init(applicationContext)
            val device = Terminal.services.device
            setStatus("Terminal : $device")
            log("Terminal.init OK, device = $device")
        } catch (e: Throwable) {
            setStatus("Terminal : ERREUR d'initialisation")
            log("Terminal.init a échoué : ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    /** Crée le PaymentManager : charge aussi les paramètres EMV vers le kernel L2. */
    private fun initPayment() {
        try {
            paymentManager = PaymentManager(
                context = this,
                parametersProvider = WorldlineEmvParametersProvider(this),   // → WorldlineEmvParametersProvider(this)
                authorizer = GatewayOnlineAuthorizer(FakePaymentGateway()),
                listener = object : PaymentManager.Listener {

                    override fun onWaitingForCard() {
                        log("[CARD] En attente de carte")
                        setStatus("Présente ou insère la carte...")
                    }

                    override fun onCardDetected(entryMode: EmvEntryMode) {
                        log("[CARD] Carte détectée : $entryMode")
                    }

                    override fun onTransactionFinished(result: EmvPaymentResult) {
                        log(if (result.approved) "✔ [EMV] Acceptée (code ${result.code})"
                        else "✖ [EMV] Échec (code ${result.code})")
                        log("[EMV] TVR(95)=${result.tags["95"]} TSI(9B)=${result.tags["9B"]}")
                        setStatus("Terminal : prêt")
                    }

                    override fun onError(message: String) {
                        log("✖ [ERREUR] $message")
                        setStatus("Terminal : erreur")
                    }
                }
            )
            log("PaymentManager initialisé")
        } catch (e: Throwable) {
            log("💥 PaymentManager : ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    // ───────────── 1. Périphériques ─────────────

    private fun testPrinter() = runTest("Impression") {
        Terminal.services.printer.print(
            listOf(
                PrintLine.Text("TEST TERMINAL", bold = true, size = FontSize.LARGE, align = Align.CENTER),
                PrintLine.Text("Date : ${now()}"),
                PrintLine.Feed(3),
            )
        )
    }

    private fun testCardReader() = runTest("Lecture carte (glisse une carte)") {
        Terminal.services.cardReader.readCard().map { c ->
            "${c.maskedPan}  exp ${c.expiry ?: "?"}  (${c.entryMode})"
        }
    }

    private fun testScanner() = ensureCameraPermission {
        runTest("Scan") { Terminal.services.scanner.scan() }
    }

    // ───────────── 2. Paramètres EMV → kernel L2 ─────────────

    private fun testEmvParameters() = runPlain("Paramètres EMV → kernel L2") {
        val pm = paymentManager ?: error("PaymentManager non initialisé")
        val provider = pm.parametersProvider

        val report = EmvKernelSelfCheck.run(
            aids = provider.getAids(),
            capks = provider.getCapks(),
            terminal = provider.getTerminalParam(),
            configurator = pm.emvKernelConfigurator,
            ignored = provider.ignoredCapks
        )
        report.lines.forEach { log(it) }
        check(report.passed) { "un ou plusieurs tests en échec (voir [FAIL])" }
        "tous les tests OK"
    }

    private fun showEmvParameters() = runPlain("Afficher les paramètres EMV lus") {
        val provider = (paymentManager ?: error("PaymentManager non initialisé")).parametersProvider

        val aids = provider.getAids()
        val capks = provider.getCapks()
        val terminal = provider.getTerminalParam()

        aids.forEach {
            log("AID ${it.aid} v${it.appVersion} TAC[den=${it.tacDenial} onl=${it.tacOnline} def=${it.tacDefault}] floor=${it.floorLimit}")
        }
        capks.forEach {
            log("CAPK ${it.rid}/${it.index} mod=${it.modulus.length / 2}o exp=${it.exponent} date=${it.expireDate}")
        }
        log("TERMINAL capContact=${terminal.contactCapabilities} capCL=${terminal.contactlessCapabilities} TTQ=${terminal.contactlessTtq}")
        "${aids.size} AID, ${capks.size} CAPK"
    }

    // ───────────── 3. Paiement EMV ─────────────

    private fun startPayment() {
        val pm = paymentManager ?: run { log("⚠ PaymentManager non initialisé"); return }
        val amount = amountInput.text.toString().trim()
        if (amount.isEmpty()) {
            log("⚠ Saisis un montant (en centimes)")
            return
        }
        log("▶ Paiement, montant = $amount")
        try {
            pm.startPayment(amount)
        } catch (e: Exception) {
            log("💥 startPayment : ${e.message}", e)
        }
    }
    // ───────────── 4. PIN pad : briques communes ─────────────

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
                TestKeys.MASTER_INDEX, TestKeys.TMK.hexToBytes(), TestKeys.TMK_KCV.hexToBytes()
            )
        )
        if (master is TerminalResult.Failure) return master
        return pinPad.injectKey(
            KeyInjection.UnderParent(
                KeyKind.PIN, TestKeys.PIN_INDEX, TestKeys.MASTER_INDEX,
                TestKeys.PIN_KEY_UNDER_TMK.hexToBytes(), TestKeys.PIN_KEY_KCV.hexToBytes()
            )
        )
    }

    private suspend fun pinTest(): TerminalResult<String> {
        log("Réglages PIN : ${PinSdkOptions.describe()}")
        val sessionId = CardSessionStore.put(TestKeys.PAN)
        return Terminal.services.pinPad
            .requestPin(PinRequest(sessionId = sessionId, pinKeyIndex = TestKeys.PIN_INDEX))
            .map { verifyPinBlock(it) }
    }

    private fun verifyPinBlock(enc: EncryptedPin): String {
        if (enc.block.size != 8) return "✖ bloc de ${enc.block.size} octets (8 attendus)"
        val format = PinSdkOptions.forceFormat ?: enc.format

        val key = TestKeys.PIN_KEY_CLEAR.hexToBytes()
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key + key.copyOf(8), "DESede"))
        val clear = cipher.doFinal(enc.block)

        val field: ByteArray = if (format == PinBlockFormat.ISO_1) {
            clear
        } else {
            val panField = ("0000" + TestKeys.PAN.dropLast(1).takeLast(12)).hexToBytes()
            ByteArray(8) { (clear[it].toInt() xor panField[it].toInt()).toByte() }
        }

        val hex = field.toHex()
        val expectedControl = when (format) {
            PinBlockFormat.ISO_0 -> '0'
            PinBlockFormat.ISO_1 -> '1'
            PinBlockFormat.ISO_3 -> '3'
        }
        if (hex[0] != expectedControl) {
            return "✖ champ de contrôle ${hex[0]} (attendu $expectedControl) : clé, PAN ou format différents"
        }
        val length = hex[1].digitToIntOrNull(16) ?: return "✖ bloc invalide (longueur illisible)"
        if (length !in 4..12) return "✖ bloc invalide (longueur $length)"
        val pin = hex.substring(2, 2 + length)
        return if (pin == TestKeys.TEST_PIN) "bloc valide, PIN décodé conforme" else "✖ le PIN décodé ne correspond pas"
    }

    // ───────────── PIN pad : tests de base ─────────────

    private fun testInjectKeys() = runTest("Injection clés de test") { injectTestKeys() }

    private fun testPinWithTestPan() =
        runTest("PIN : tape ${TestKeys.TEST_PIN} puis valide") { pinTest() }

    private fun testInjectThenPin() = runTest("Injection + PIN : tape ${TestKeys.TEST_PIN}") {
        PinSdkOptions.reset()
        val injected = injectTestKeys()
        if (injected is TerminalResult.Failure) return@runTest injected
        pinTest()
    }

    private fun testPinRealCard() = runTest("PIN avec une carte de test") {
        val card = Terminal.services.cardReader.readCard()
        if (card !is TerminalResult.Success) return@runTest card
        val sessionId = card.value.sessionId
            ?: return@runTest TerminalResult.Failure(TerminalError.SessionExpired)
        Terminal.services.pinPad.requestPin(PinRequest(sessionId = sessionId, pinKeyIndex = TestKeys.PIN_INDEX))
    }

    // ───────────── PIN pad : variantes ─────────────

    private data class PinVariant(
        val label: String,
        val format: PinBlockFormat?,
        val pan: PanMode,
        val bytes: Boolean,
    )

    private val pinVariants = listOf(
        PinVariant("ISO-1 (n'utilise pas le PAN)", PinBlockFormat.ISO_1, PanMode.FULL, false),
        PinVariant("ISO-0, PAN en octets", null, PanMode.FULL, true),
        PinVariant("ISO-0, PAN sans chiffre de contrôle (15)", null, PanMode.NO_CHECK, false),
        PinVariant("ISO-0, PAN 12 chiffres", null, PanMode.ISO12, false),
    )
    private var pinVariantIndex = 0
    private var algoModeIndex = 0

    private fun testPinNextVariant() {
        if (busy()) return
        val v = pinVariants[pinVariantIndex % pinVariants.size]
        pinVariantIndex++
        PinSdkOptions.reset()
        PinSdkOptions.forceFormat = v.format
        PinSdkOptions.panMode = v.pan
        PinSdkOptions.panAsBytes = v.bytes
        log("Variante $pinVariantIndex/${pinVariants.size} : ${v.label}")
        testPinWithTestPan()
    }

    private fun testPinNextAlgorithmMode() {
        if (busy()) return
        val names = SdkInspector.enumValuesOf(PIN_PAD_CLASS, "setAlgorithmMode")
        if (names.isEmpty()) {
            log("⚠ setAlgorithmMode introuvable")
            return
        }
        val name = names[algoModeIndex % names.size]
        algoModeIndex++
        PinSdkOptions.reset()
        PinSdkOptions.presets["setAlgorithmMode"] = name
        log("Mode algorithme $algoModeIndex/${names.size} : $name")
        runTest("Injection + PIN, setAlgorithmMode($name)") {
            val injected = injectTestKeys()
            if (injected is TerminalResult.Failure) return@runTest injected
            pinTest()
        }
    }

    private val lengthVariants = listOf(
        listOf(4),
        listOf(4, 5, 6),
        listOf(0, 4, 5, 6, 7, 8, 9, 10, 11, 12),
    )
    private var lengthIndex = 0

    private fun testPinNextLengths() {
        if (busy()) return
        val l = lengthVariants[lengthIndex % lengthVariants.size]
        lengthIndex++
        PinSdkOptions.reset()
        PinSdkOptions.lengthOverride = l
        log("Longueurs $lengthIndex/${lengthVariants.size} : $l")
        testPinWithTestPan()
    }

    private val keyboardPresets = listOf(
        "setPinKeyboardMode" to "FIXED",
        "setPinKeyboardMode" to "RANDOM",
        "setPinKeyboardViewMode" to "VIEW_1",
        "setPinKeyboardClearMode" to "BACKSPACE",
        "setPinKeyboardPinMaskMode" to "VISIBLE",
        "setCipherMode" to "ECB",
    )
    private var keyboardIndex = 0

    private fun testPinNextKeyboardSetting() {
        if (busy()) return
        val (method, value) = keyboardPresets[keyboardIndex % keyboardPresets.size]
        keyboardIndex++
        PinSdkOptions.reset()
        PinSdkOptions.presets[method] = value
        log("Réglage $keyboardIndex/${keyboardPresets.size} : $method($value)")
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
        log("Réglages PIN réinitialisés : ${PinSdkOptions.describe()}")
    }

    // ───────────── 5. Diagnostic du SDK ─────────────

    private fun diagnosticPinPadMethods() {
        log("Diagnostic : méthodes et codes du PIN pad")
        SdkInspector.dump(PIN_PAD_CLASS)
        SdkInspector.dump("com.nexgo.oaf.apiv3.device.pinpad.PinAlgorithmModeEnum")
        SdkInspector.dump("com.nexgo.oaf.apiv3.device.pinpad.WorkKeyTypeEnum")
        SdkInspector.dump("com.nexgo.oaf.apiv3.SdkResult", constantPrefix = "PinPad")
    }

    private fun diagnosticPinPadSettings() {
        log("Diagnostic : valeurs possibles des réglages du PIN pad")
        SdkInspector.dumpSetterEnums(
            PIN_PAD_CLASS,
            listOf(
                "setAlgorithmMode", "setCipherMode", "setPinKeyboardMode",
                "setPinKeyboardViewMode", "setPinKeyboardClearMode", "setPinKeyboardPinMaskMode",
            )
        )
    }
    private fun cancelPayment() {
        try {
            paymentManager?.cancel()
            log("⏹ Paiement annulé")
        } catch (e: Exception) {
            log("💥 cancel : ${e.message}", e)
        }
    }

    // ───────────── Permission caméra ─────────────

    private fun ensureCameraPermission(onGranted: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            log("Permission caméra demandée")
            pendingAfterPermission = onGranted
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == REQ_CAMERA && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingAfterPermission?.invoke()
        } else {
            log("✖ Permission caméra refusée")
        }
        pendingAfterPermission = null
    }

    // ───────────── Exécution commune ─────────────

    private fun busy(): Boolean {
        val running = job?.isActive == true
        if (running) log("⚠ Une opération est déjà en cours : attends sa fin ou appuie sur Annuler")
        return running
    }

    private fun cancelCurrent() {
        if (job?.isActive == true) log("⏹ Annulation demandée") else log("Rien à annuler")
        job?.cancel()
        cancelPayment()
    }

    private fun <T> runTest(label: String, block: suspend () -> TerminalResult<T>) {
        if (busy()) return
        log("▶ $label")
        setStatus("En cours : $label")
        job = scope.launch {
            val start = SystemClock.elapsedRealtime()
            try {
                val result = block()
                val ms = SystemClock.elapsedRealtime() - start
                when (result) {
                    is TerminalResult.Success -> log("✔ $label (${ms} ms) : ${result.value}")
                    is TerminalResult.Failure -> log("✖ $label (${ms} ms) : ${result.error}")
                }
            } catch (e: CancellationException) {
                log("⏹ $label annulé après ${SystemClock.elapsedRealtime() - start} ms")
                throw e
            } catch (e: Throwable) {
                log("💥 $label : ${e.javaClass.simpleName}: ${e.message}", e)
            } finally {
                resetStatus()
            }
        }
    }

    private fun runPlain(label: String, block: suspend () -> String) {
        if (busy()) return
        log("▶ $label")
        setStatus("En cours : $label")
        job = scope.launch {
            val start = SystemClock.elapsedRealtime()
            try {
                val message = withContext(Dispatchers.IO) { block() }
                log("✔ $label (${SystemClock.elapsedRealtime() - start} ms) : $message")
            } catch (e: CancellationException) {
                log("⏹ $label annulé")
                throw e
            } catch (e: Throwable) {
                log("✖ $label : ${e.javaClass.simpleName}: ${e.message}", e)
            } finally {
                resetStatus()
            }
        }
    }

    private fun resetStatus() {
        setStatus("Terminal : ${runCatching { Terminal.services.device }.getOrNull() ?: "?"}")
    }

    // ───────────── Journal (thread-safe) ─────────────

    private fun log(msg: String, error: Throwable? = null) {
        Log.i(TAG, msg, error)
        if (msg.startsWith("[OK]") || msg.startsWith("[FAIL]") ||
            msg.startsWith("[SKIP]") || msg.startsWith("===")
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
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun setStatus(text: String) {
        runOnUiThread { if (::statusView.isInitialized) statusView.text = text }
    }

    private fun copyLog() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("journal", logView.text))
        Toast.makeText(this, "Journal copié", Toast.LENGTH_SHORT).show()
    }

    private fun clearLog() {
        logView.text = ""
    }

    private fun now() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    // ───────────── Interface ─────────────

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

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        buttons.addView(section("1. Périphériques"))
        buttons.addView(button("Imprimer un ticket test", ::testPrinter))
        buttons.addView(button("Lire une carte (piste magnétique)", ::testCardReader))
        buttons.addView(button("Scanner un code", ::testScanner))

        buttons.addView(section("2. Paramètres EMV → kernel L2"))
        buttons.addView(button("Envoyer au kernel L2 + auto-test", ::testEmvParameters))
        buttons.addView(button("Afficher AID / CAPK lus", ::showEmvParameters))

        buttons.addView(section("3. Paiement EMV"))
        amountInput = EditText(this).apply {
            hint = "Montant en centimes"
            setText("1000")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        buttons.addView(amountInput)
        buttons.addView(button("Démarrer le paiement", ::startPayment))
        buttons.addView(button("Annuler le paiement", ::cancelPayment))


            buttons.addView(section("4. PIN pad : tests de base"))
            buttons.addView(button("Injecter les clés de test", ::testInjectKeys))
            buttons.addView(button("Saisie du PIN + vérification", ::testPinWithTestPan))
            buttons.addView(button("Injection + saisie (tout-en-un)", ::testInjectThenPin))
            buttons.addView(button("PIN avec une carte de test", ::testPinRealCard))

            buttons.addView(section("5. PIN pad : variantes"))
            buttons.addView(button("Longueurs de PIN suivantes", ::testPinNextLengths))
            buttons.addView(button("Réglage clavier suivant", ::testPinNextKeyboardSetting))
            buttons.addView(button("Variante PAN / format suivante", ::testPinNextVariant))
            buttons.addView(button("Mode algorithme suivant", ::testPinNextAlgorithmMode))
            buttons.addView(button("Réinitialiser les réglages PIN", ::resetPinSettings))

            buttons.addView(section("6. Diagnostic (lecture seule)"))
            buttons.addView(button("Réglages du PIN pad (valeurs)", ::diagnosticPinPadSettings))
            buttons.addView(button("Méthodes et codes du PIN pad", ::diagnosticPinPadMethods))


        root.addView(
            ScrollView(this).apply { addView(buttons) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        )

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("Annuler", ::cancelCurrent), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(button("Copier le journal", ::copyLog), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(button("Effacer", ::clearLog), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })

        logView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }
        logScroll = ScrollView(this).apply { addView(logView) }
        root.addView(logScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        return root
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 16, 0, 4)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        setOnClickListener { onClick() }
    }
}