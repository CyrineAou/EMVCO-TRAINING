package com.example.terminalmodule

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.terminalmodule.internal.CardSessionStore
import com.example.terminalmodule.internal.SdkInspector
import com.example.terminalmodule.internal.TerminalLog
import com.example.terminalmodule.internal.hexToBytes
import com.example.terminalmodule.internal.toHex
import com.example.terminalmodule.model.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class MainActivity : Activity() {

    private companion object {
        const val TAG = "TerminalTest"
        const val REQ_CAMERA = 42
        const val MAX_LOG_CHARS = 40_000
    }

    private val scope = MainScope()
    private var job: Job? = null
    private var pendingAfterPermission: (() -> Unit)? = null

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView


    private val isDebuggable: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // ───────────── Cycle de vie ─────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())

        // Les logs internes du module apparaissent aussi à l'écran (sans Logcat).
        TerminalLog.sink = { line -> runOnUiThread { appendLine(line) } }

        log("Démarrage. debuggable=$isDebuggable")
        initTerminal()
    }

    override fun onDestroy() {
        TerminalLog.sink = null
        scope.cancel()
        super.onDestroy()
    }

    // ───────────── Initialisation ─────────────

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

    // ───────────── Tests : imprimante, carte, scanner ─────────────

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

    // ───────────── Tests : PIN pad (debug uniquement) ─────────────

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

    private fun testInjectKeys() = runTest("Injection clés de test") {
        val pinPad = Terminal.services.pinPad
        val master = pinPad.injectKey(
            KeyInjection.ClearMasterForTest(
                TestKeys.MASTER_INDEX, TestKeys.TMK.hexToBytes(), TestKeys.TMK_KCV.hexToBytes()
            )
        )
        if (master is TerminalResult.Failure) return@runTest master
        pinPad.injectKey(
            KeyInjection.UnderParent(
                KeyKind.PIN, TestKeys.PIN_INDEX, TestKeys.MASTER_INDEX,
                TestKeys.PIN_KEY_UNDER_TMK.hexToBytes(), TestKeys.PIN_KEY_KCV.hexToBytes()
            )
        )
    }


    /** PAN de test, sans carte : le bloc peut être vérifié car le PAN est connu. */
    private fun testPinWithTestPan() = runTest("PIN : tape ${TestKeys.TEST_PIN} sur le terminal") {
        val sessionId = CardSessionStore.put(TestKeys.PAN)
        Terminal.services.pinPad
            .requestPin(PinRequest(sessionId = sessionId, pinKeyIndex = TestKeys.PIN_INDEX))
            .map { verifyPinBlock(it) }
    }

    /** Vraie carte glissée : pas de vérification possible (PAN inconnu du test). */
    private fun testPinRealCard() = runTest("PIN avec une carte de test") {
        val card = Terminal.services.cardReader.readCard()
        if (card !is TerminalResult.Success) return@runTest card
        val sessionId = card.value.sessionId
            ?: return@runTest TerminalResult.Failure(TerminalError.SessionExpired)
        Terminal.services.pinPad.requestPin(PinRequest(sessionId = sessionId, pinKeyIndex = TestKeys.PIN_INDEX))
    }

    /** Déchiffre le bloc avec la clé PIN de test et contrôle le PIN. Test uniquement. */
    private fun verifyPinBlock(enc: EncryptedPin): String {
        if (enc.block.size != 8) return "✖ bloc de ${enc.block.size} octets (8 attendus)"
        val key = TestKeys.PIN_KEY_CLEAR.hexToBytes()
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key + key.copyOf(8), "DESede"))
        val clear = cipher.doFinal(enc.block)
        val panField = ("0000" + TestKeys.PAN.dropLast(1).takeLast(12)).hexToBytes()
        val pinField = ByteArray(8) { (clear[it].toInt() xor panField[it].toInt()).toByte() }.toHex()
        val length = pinField[1].digitToIntOrNull() ?: return "✖ bloc invalide (longueur illisible)"
        if (length !in 4..12) return "✖ bloc invalide (longueur $length)"
        val pin = pinField.substring(2, 2 + length)
        return if (pin == TestKeys.TEST_PIN) "bloc valide, PIN décodé conforme" else "✖ le PIN décodé ne correspond pas"
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

    private fun cancelCurrent() {
        if (job?.isActive == true) log("⏹ Annulation demandée") else log("Rien à annuler")
        job?.cancel()
    }

    private fun <T> runTest(label: String, block: suspend () -> TerminalResult<T>) {
        if (job?.isActive == true) {
            log("⚠ Une opération est déjà en cours : attends sa fin ou appuie sur Annuler")
            return
        }
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
                // Attrape aussi les Error (ex. NoClassDefFoundError, NotImplementedError)
                log("💥 $label : ${e.javaClass.simpleName}: ${e.message}", e)
            } finally {
                setStatus("Terminal : ${runCatching { Terminal.services.device }.getOrNull() ?: "?"}")
            }
        }
    }

    // ───────────── Journal ─────────────

    /** Journal de l'activité : écran + Logcat (niveau Info, plus visible que Debug). */
    private fun log(msg: String, error: Throwable? = null) {
        Log.i(TAG, msg, error)
        appendLine("A  $msg")
    }

    private fun appendLine(text: String) {
        if (!::logView.isInitialized) return
        logView.append("[${now()}] $text\n")
        if (logView.length() > MAX_LOG_CHARS) {
            logView.text = logView.text.takeLast(MAX_LOG_CHARS / 2)
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setStatus(text: String) {
        runOnUiThread { if (::statusView.isInitialized) statusView.text = text }
    }

    private fun copyLog() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("journal", logView.text))
        Toast.makeText(this, "Journal copié", Toast.LENGTH_SHORT).show()
    }

    private fun diagnosticPinPadSdk() {
        log("Diagnostic SDK PIN pad (noms uniquement)")
        SdkInspector.dump("com.nexgo.oaf.apiv3.device.pinpad.PinPad")
        SdkInspector.dump("com.nexgo.oaf.apiv3.device.pinpad.PinAlgorithmModeEnum")
        SdkInspector.dump("com.nexgo.oaf.apiv3.device.pinpad.WorkKeyTypeEnum")
        SdkInspector.dump("com.nexgo.oaf.apiv3.device.pinpad.OnPinPadInputListener")
        SdkInspector.dump("com.nexgo.oaf.apiv3.SdkResult", constantPrefix = "PinPad")
    }

    private fun clearLog() {
        logView.text = ""
    }

    private fun now() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    // ───────────── Interface ─────────────

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        statusView = TextView(this).apply {
            textSize = 16f
            text = "Terminal : …"
        }
        root.addView(statusView)

        root.addView(section("Périphériques"))
        root.addView(button("Imprimer un ticket test", ::testPrinter))
        root.addView(button("Lire une carte (piste magnétique)", ::testCardReader))
        root.addView(button("Scanner un code", ::testScanner))
        root.addView(button("Diagnostic SDK PIN pad", ::diagnosticPinPadSdk))

        if (isDebuggable) {
            root.addView(section("PIN pad (debug)"))
            root.addView(button("1) Injecter les clés de test", ::testInjectKeys))
            root.addView(button("2) Saisie du PIN + vérification", ::testPinWithTestPan))
            root.addView(button("PIN avec une carte de test", ::testPinRealCard))
        }

        root.addView(button("Annuler l'opération en cours", ::cancelCurrent))

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("Copier le journal", ::copyLog), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(button("Effacer", ::clearLog), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })

        logView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }
        scroll = ScrollView(this).apply { addView(logView) }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        return root
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        textSize = 14f
        setPadding(0, 16, 0, 4)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        setOnClickListener { onClick() }
    }
}
