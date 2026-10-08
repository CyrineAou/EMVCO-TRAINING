//package com.example.terminalmodule
//
//import android.os.Bundle
//import android.util.Log
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.lifecycleScope
//import com.example.terminalmodule.model.PrintLine
//import com.example.terminalmodule.testing.FakeTerminalServices
//import kotlinx.coroutines.launch
//import kotlin.time.Duration.Companion.seconds
//
//class MainActivity : AppCompatActivity() {
//
//    companion object {
//        private const val TAG = "TERMINAL_TEST"
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        val textView = TextView(this).apply {
//            textSize = 18f
//            setPadding(32, 32, 32, 32)
//        }
//
//        setContentView(textView)
//
//        testFakeTerminal(textView)
//    }
//
//    private fun testFakeTerminal(textView: TextView) {
//
//        lifecycleScope.launch {
//
//            try {
//
//                Log.d(TAG, "================================")
//                Log.d(TAG, "FAKE TERMINAL TEST")
//                Log.d(TAG, "================================")
//
//                // 1. Create fake terminal
//                val fakeTerminal = FakeTerminalServices()
//
//                // 2. Inject fake terminal
//                Terminal.override(fakeTerminal)
//
//                Log.d(TAG, "Fake terminal injected")
//
//                val terminal = Terminal.services
//
//                Log.d(TAG, "Device = ${terminal.device}")
//
//                // --------------------------------
//                // CARD READER
//                // --------------------------------
//
//                Log.d(TAG, "Starting card reader...")
//
//                val cardResult = terminal.cardReader.readCard(
//                    timeout = 30.seconds
//                )
//
//                Log.d(TAG, "Card result = $cardResult")
//
//                // --------------------------------
//                // SCANNER
//                // --------------------------------
//
//                Log.d(TAG, "Starting scanner...")
//
//                val scanResult = terminal.scanner.scan(
//                    timeout = 30.seconds
//                )
//
//                Log.d(TAG, "Scan result = $scanResult")
//
//                // --------------------------------
//                // PRINTER
//                // --------------------------------
//
//                Log.d(TAG, "Starting printer...")
//
//                val lines = listOf(PrintLine("TERMINAL TEST"), PrintLine("Card reader OK"), PrintLine("Scanner OK"))
//
//                val printResult = terminal.printer.print(lines)
//
//                Log.d(TAG, "Print result = $printResult")
//
//                // --------------------------------
//                // PRINTED LINES
//                // --------------------------------
//
//                Log.d(TAG, "Printed = ${fakeTerminal.printed}")
//
//                textView.text = """
//
//                    FAKE TERMINAL TEST
//
//                    Device:
//                    ${terminal.device}
//
//                    Card:
//                    $cardResult
//
//                    Scanner:
//                    $scanResult
//
//                    Printer:
//                    $printResult
//
//                    Printed:
//                    ${fakeTerminal.printed}
//
//                """.trimIndent()
//
//                Log.d(TAG, "================================")
//                Log.d(TAG, "FAKE TERMINAL TEST COMPLETED")
//                Log.d(TAG, "================================")
//
//            } catch (e: Throwable) {
//
//                Log.e(TAG, "TEST FAILED", e)
//
//                textView.text = """
//                    TEST FAILED
//
//                    ${e::class.simpleName}
//
//                    ${e.message}
//                """.trimIndent()
//            }
//        }
//    }
//}