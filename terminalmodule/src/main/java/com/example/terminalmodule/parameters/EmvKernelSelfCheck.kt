package com.example.nexgointegration.parameters

import android.util.Log
import com.example.terminalmodule.api.EmvKernelConfigurator
import com.example.terminalmodule.model.EmvAidParam
import com.example.terminalmodule.model.EmvCapkParam
import com.example.terminalmodule.model.EmvTerminalParam
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rapport dans Logcat, filtre : tag:EMV_TEST */
object EmvKernelSelfCheck {

    private const val TAG = "EMV_TEST"

    data class Report(val lines: List<String>, val passed: Boolean)

    fun run(
        aids: List<EmvAidParam>,
        capks: List<EmvCapkParam>,
        terminal: EmvTerminalParam,
        configurator: EmvKernelConfigurator?,
        ignored: List<String> = emptyList()
    ): Report {
        val lines = mutableListOf<String>()
        var ok = true

        fun check(name: String, cond: Boolean, detail: String = "") {
            if (!cond) ok = false
            lines += (if (cond) "[OK]   " else "[FAIL] ") + name +
                    (if (detail.isNotEmpty()) " : $detail" else "")
        }

        // ---------- Partie A : données ----------
        check("A1 AID chargés", aids.isNotEmpty(), "${aids.size}")
        check("A2 CAPK valides", capks.isNotEmpty(), "${capks.size}")

        aids.forEach { a ->
            lines += "[INFO] AID ${a.aid} ver=${a.appVersion} " +
                    "TACden=${a.tacDenial} TAConl=${a.tacOnline} TACdef=${a.tacDefault} " +
                    "floor=${a.floorLimit} clFloor=${a.contactlessFloorLimit} " +
                    "clCvm=${a.contactlessCvmLimit} clTrans=${a.contactlessTransLimit}"
            check(
                "A3 AID ${a.aid} hexa",
                a.aid.matches(Regex("[0-9A-F]{10,32}")) && a.aid.length % 2 == 0
            )
            check(
                "A4 AID ${a.aid} TAC (10 hexa)",
                listOf(a.tacDenial, a.tacOnline, a.tacDefault)
                    .all { it.matches(Regex("[0-9A-F]{10}")) }
            )
        }

        lines += "[INFO] TERMINAL 9F33 contact=${terminal.contactCapabilities} " +
                "9F33 contactless=${terminal.contactlessCapabilities} 9F66 TTQ=${terminal.contactlessTtq}"

        ignored.forEach { lines += "[WARN] CAPK ignorée : $it" }

        capks.forEach { c ->
            val id = "${c.rid}/${c.index}"
            check("A5 CAPK $id RID (10 hexa)", c.rid.matches(Regex("[0-9A-F]{10}")))
            check(
                "A6 CAPK $id modulus",
                c.modulus.matches(Regex("[0-9A-F]+")) && c.modulus.length % 2 == 0,
                "${c.modulus.length / 2} octets"
            )
            check("A7 CAPK $id exposant", c.exponent == "03" || c.exponent == "010001", c.exponent)
            check(
                "A8 CAPK $id checksum (40 hexa)",
                c.checkSum.matches(Regex("[0-9A-F]{40}")),
                "${c.checkSum.length} car."
            )
            check("A9 CAPK $id date YYMMDD", c.expireDate.matches(Regex("\\d{6}")), c.expireDate)
        }

        // CAPK expirées : simple avertissement, pas un échec
        val today = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        val expired = capks.count { it.expireDate < today }
        if (expired > 0) {
            lines += "[WARN] $expired CAPK ont une date d'expiration dépassée (aujourd'hui = $today)"
        }

        check(
            "A10 Paramètres terminal",
            terminal.contactlessTtq.length == 8 &&
                    terminal.contactCapabilities.length == 6 &&
                    terminal.contactlessCapabilities.length == 6
        )

        // ---------- Partie B : vrai kernel ----------
        if (configurator == null) {
            lines += "[SKIP] B Envoi au kernel L2 (aucun configurator)"
        } else {
            val r = configurator.loadParameters(aids, capks, terminal)
            check("B1 Envoi au kernel L2", r.isSuccess, r.exceptionOrNull()?.toString() ?: "")
        }

        lines += if (ok) "=== RESULTAT : TOUS LES TESTS OK ===" else "=== RESULTAT : ECHEC ==="
        lines.forEach { Log.d(TAG, it) }
        return Report(lines, ok)
    }
}