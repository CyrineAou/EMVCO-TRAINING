
package com.example.terminalmodule.internal

import com.example.terminalmodule.model.PinBlockFormat
import java.util.concurrent.ConcurrentHashMap

 enum class PanMode {
    FULL,
    NO_CHECK,
    ISO12
}

/**
 * Options de diagnostic du PIN pad Nexgo.
 *
 * Valeurs par défaut : comportement normal, sans surcharge expérimentale.
 */
 object PinSdkOptions {

    @Volatile
    var forceFormat: PinBlockFormat? = null

    @Volatile
    var panMode: PanMode = PanMode.FULL

    @Volatile
    var panAsBytes: Boolean = false

    @Volatile
    var swapTimeoutIndex: Boolean = false

    @Volatile
    var lengthOverride: List<Int>? = null

    /**
     * Réglages facultatifs : nom de méthode -> nom de valeur enum.
     */
    val presets = ConcurrentHashMap<String, String>()

    /**
     * Construit le PAN transmis au SDK.
     */
    fun pan(digits: String): String {
        require(digits.all { it in '0'..'9' }) {
            "Le PAN doit contenir uniquement des chiffres"
        }
        require(digits.length in 13..19) {
            "Le PAN doit contenir entre 13 et 19 chiffres"
        }

        return when (panMode) {
            PanMode.FULL -> digits
            PanMode.NO_CHECK -> digits.dropLast(1)
            PanMode.ISO12 -> digits.dropLast(1).takeLast(12)
        }
    }

    /**
     * Réinitialise les options de diagnostic.
     */
    fun reset() {
        forceFormat = null
        panMode = PanMode.FULL
        panAsBytes = false
        swapTimeoutIndex = false
        lengthOverride = null
        presets.clear()
    }

    fun describe(): String =
        "format=${forceFormat ?: "demandé"}, " +
                "pan=$panMode, " +
                "octets=$panAsBytes, " +
                "ordreInverse=$swapTimeoutIndex, " +
                "longueurs=${lengthOverride ?: "requête"}, " +
                "nombreRéglages=${presets.size}"
}