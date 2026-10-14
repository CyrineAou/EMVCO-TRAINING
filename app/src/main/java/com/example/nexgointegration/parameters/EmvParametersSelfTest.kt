package com.example.nexgointegration.parameters

import android.content.Context
import android.util.Log
import com.example.nexgointegration.EmvParametersManager
import com.example.terminalmodule.api.EmvKernelConfigurator
import com.example.terminalmodule.testing.MockEmvKernelConfigurator


/**
 * Auto-test du passage des paramètres, lançable sur le terminal.
 *
 * Partie A : vérifie les données lues (JSON + AID) avec un faux configurator.
 * Partie B : envoie au VRAI configurator (kernel L2) et vérifie qu'il n'y a pas d'erreur.
 *
 * Rapport dans Logcat, filtre : EMV_TEST
 */
class EmvParametersSelfTest(
    private val context: Context,
    private val realConfigurator: EmvKernelConfigurator? = null
) {

    companion object {
        private const val TAG = "EMV_TEST"
    }

    data class Report(val lines: List<String>, val passed: Boolean) {
        override fun toString() = lines.joinToString("\n")
    }

    fun run(): Report {
        val lines = mutableListOf<String>()
        var ok = true

        fun check(name: String, cond: Boolean, detail: String = "") {
            if (!cond) ok = false
            lines += (if (cond) "[OK]   " else "[FAIL] ") + name +
                    if (detail.isNotEmpty()) " : $detail" else ""
        }

        // ---------- Partie A : données ----------
        val mock = MockEmvKernelConfigurator()
        val result = EmvParametersManager(context, mock).apply()
        check("A1 Lecture des paramètres", result.isSuccess,
            result.exceptionOrNull()?.toString() ?: "")

        if (result.isSuccess) {
            check("A2 AID chargés", mock.aids.isNotEmpty(), "${mock.aids.size}")
            check("A3 CAPK chargés", mock.capks.isNotEmpty(), "${mock.capks.size}")

            mock.aids.forEach { a ->
                check("A4 AID ${a.aid} format hexa",
                    a.aid.matches(Regex("[0-9A-F]{10,32}")) && a.aid.length % 2 == 0)
                check("A5 AID ${a.aid} TAC (10 hexa)",
                    listOf(a.tacDenial, a.tacOnline, a.tacDefault)
                        .all { it.matches(Regex("[0-9A-F]{10}")) })
            }

            mock.capks.forEach { c ->
                val id = "${c.rid}/${c.index}"
                check("A6 CAPK $id RID (10 hexa)", c.rid.matches(Regex("[0-9A-F]{10}")))
                check("A7 CAPK $id modulus hexa pair",
                    c.modulus.matches(Regex("[0-9A-F]+")) && c.modulus.length % 2 == 0,
                    "${c.modulus.length / 2} octets")
                check("A8 CAPK $id exposant", c.exponent == "03" || c.exponent == "010001",
                    c.exponent)
                check("A9 CAPK $id checksum (40 hexa)", c.checkSum.matches(Regex("[0-9A-F]{40}")),
                    "${c.checkSum.length} car.")
                check("A10 CAPK $id date YYMMDD", c.expireDate.matches(Regex("\\d{6}")),
                    c.expireDate)
            }

            val t = mock.terminal
            check("A11 Paramètres terminal",
                t != null && t.contactlessTtq.length == 8 &&
                        t.contactCapabilities.length == 6 && t.contactlessCapabilities.length == 6)
        }

        // ---------- Partie B : vrai kernel ----------
        if (realConfigurator == null) {
            lines += "[SKIP] B Envoi au kernel L2 (aucun configurator réel fourni)"
        } else {
            val real = EmvParametersManager(context, realConfigurator).apply()
            check("B1 Envoi au kernel L2", real.isSuccess,
                real.exceptionOrNull()?.toString() ?: "")
        }

        lines += if (ok) "=== RESULTAT : TOUS LES TESTS OK ===" else "=== RESULTAT : ECHEC ==="
        val report = Report(lines, ok)
        report.lines.forEach { Log.d(TAG, it) }
        return report
    }
}