////package com.example.terminalmodule.internal
////
////import android.content.Context
////import com.example.terminalmodule.model.KeyKind
////import com.example.terminalmodule.model.PinBlockFormat
////import com.example.terminalmodule.model.PinRequest
////import com.nexgo.oaf.apiv3.DeviceEngine
////import com.nexgo.oaf.apiv3.device.pinpad.OnPinPadInputListener // SDK-VERIFY
////import com.nexgo.oaf.apiv3.device.pinpad.PinAlgorithmModeEnum // SDK-VERIFY
////import com.nexgo.oaf.apiv3.device.pinpad.WorkKeyTypeEnum // SDK-VERIFY
////
/////** Adaptateur : seul endroit qui connaît les signatures du PIN pad Nexgo. */
////internal class NexgoPinPadSdk(
////    private val engine: DeviceEngine,
////    private val context: Context,
////) {
////    private val pinPad get() = engine.pinPad // SDK-VERIFY
////
////    /** Clé maître en clair (test). Renvoie un code SdkResult. */
////    fun writeMasterKey(index: Int, key: ByteArray): Int {
////        return pinPad.writeMKey(index, key, key.size) // SDK-VERIFY
////    }
////
////    /** Clé de travail chiffrée sous la clé maître [parentIndex]. */
////    fun writeWorkKey(parentIndex: Int, kind: KeyKind, cryptogram: ByteArray): Int {
////        val type = when (kind) {
////            KeyKind.PIN -> WorkKeyTypeEnum.PINKEY // SDK-VERIFY
////            KeyKind.MAC -> WorkKeyTypeEnum.MACKEY // SDK-VERIFY
////            KeyKind.DATA -> WorkKeyTypeEnum.TDKEY // SDK-VERIFY
////            KeyKind.MASTER -> throw IllegalArgumentException("Clé maître non valide ici")
////        }
////        return pinPad.writeWKey(parentIndex, type, cryptogram, cryptogram.size) // SDK-VERIFY
////    }
////
////    /** Affiche le clavier sécurisé. [onResult] reçoit (code, bloc PIN chiffré). */
////    /** Affiche le clavier sécurisé. [onResult] reçoit (code, bloc PIN chiffré). */
////    fun startPinEntry(pan: String, req: PinRequest, onResult: (Int, ByteArray?) -> Unit) {
////        val lengths = buildList {
////            if (req.allowBypass) add(0)
////            for (n in req.minLength..req.maxLength) add(n)
////        }.toIntArray()
////
////        val algorithm = when (req.format) {
////            PinBlockFormat.ISO_0 -> PinAlgorithmModeEnum.ISO9564FMT0 // SDK-VERIFY
////            PinBlockFormat.ISO_1 -> PinAlgorithmModeEnum.ISO9564FMT1 // SDK-VERIFY
////            PinBlockFormat.ISO_3 -> PinAlgorithmModeEnum.ISO9564FMT2 // SDK-VERIFY
////        }
////
////        var keyPresses = 0
////        val listener = object : OnPinPadInputListener {
////            override fun onInputResult(retCode: Int, data: ByteArray?) {
////                TerminalLog.d("onInputResult retCode=$retCode, touches=$keyPresses")
////                onResult(retCode, data)
////            }
////
////            override fun onSendKey(keyCode: Byte) {
////                keyPresses++ // le code de touche n'est jamais loggé
////            }
////        }
////
////        val startCode: Int = pinPad.inputOnlinePin(
////            lengths,                              // p0 : longueurs autorisées
////            req.timeout.inWholeSeconds.toInt(),   // p1 : délai (secondes, à vérifier)
////            pan,                                  // p2 : PAN (version String)
////            req.pinKeyIndex,                      // p3 : index de la clé maître
////            algorithm,                            // p4 : format du bloc
////            listener,                             // p5 : callback
////        )
////
////        TerminalLog.d("inputOnlinePin : code de démarrage=$startCode")
////        // Si la saisie n'a pas démarré, le callback ne sera jamais appelé : on répond tout de suite.
////        if (NexgoErrorMapper.fromCode(startCode) != null) {
////            onResult(startCode, null)
////        }
////    }
////
////    fun cancelPinEntry() {
////        pinPad.cancelInput() // SDK-VERIFY
////    }
////}
//
//
//package com.example.terminalmodule.internal
//
//import android.content.Context
//import com.example.terminalmodule.model.KeyKind
//import com.example.terminalmodule.model.PinBlockFormat
//import com.example.terminalmodule.model.PinRequest
//import com.nexgo.oaf.apiv3.DeviceEngine
//import com.nexgo.oaf.apiv3.SdkResult
//import com.nexgo.oaf.apiv3.device.pinpad.OnPinPadInputListener
//import com.nexgo.oaf.apiv3.device.pinpad.PinAlgorithmModeEnum
//import com.nexgo.oaf.apiv3.device.pinpad.PinPadTypeEnum
//import com.nexgo.oaf.apiv3.device.pinpad.WorkKeyTypeEnum
//import java.util.concurrent.atomic.AtomicBoolean
//
///**
// * Adaptateur entre le module terminal et le SDK PIN pad NEXGO.
// *
// * Ce fichier est le seul endroit du module qui dépend directement
// * des signatures du SDK NEXGO.
// */
//internal class NexgoPinPadSdk(
//    private val engine: DeviceEngine,
//    @Suppress("unused") private val context: Context,
//) {
//
//    private val pinPad
//        get() = engine.pinPad
//
//    /**
//     * Écrit une clé maître de test.
//     *
//     * À utiliser uniquement dans un environnement de test autorisé.
//     */
//    fun writeMasterKey(index: Int, key: ByteArray): Int {
//        require(index > 0) { "L'index de la clé maître doit être positif" }
//        require(key.isNotEmpty()) { "La clé maître est vide" }
//
//        return pinPad.writeMKey(
//            index,
//            key,
//            key.size
//        )
//    }
//
//    /**
//     * Écrit une clé de travail chiffrée sous une clé maître.
//     *
//     * parentIndex correspond à l'index de la clé maître parente.
//     */
//    fun writeWorkKey(
//        parentIndex: Int,
//        kind: KeyKind,
//        cryptogram: ByteArray,
//    ): Int {
//        require(parentIndex > 0) {
//            "L'index de la clé maître parente doit être positif"
//        }
//        require(cryptogram.isNotEmpty()) {
//            "Le cryptogramme de la clé est vide"
//        }
//
//        val type = when (kind) {
//            KeyKind.PIN -> WorkKeyTypeEnum.PINKEY
//            KeyKind.MAC -> WorkKeyTypeEnum.MACKEY
//            KeyKind.DATA -> WorkKeyTypeEnum.TDKEY
//            KeyKind.MASTER -> throw IllegalArgumentException(
//                "Une clé maître ne peut pas être écrite comme clé de travail"
//            )
//        }
//
//        return pinPad.writeWKey(
//            parentIndex,
//            type,
//            cryptogram,
//            cryptogram.size
//        )
//    }
//
//    /**
//     * Lance la saisie sécurisée du PIN.
//     *
//     * onResult reçoit le code SDK et le bloc chiffré éventuellement retourné.
//     *
//     * Important :
//     * - ne jamais journaliser le PIN ou le contenu du bloc ;
//     * - le code de démarrage et le code du callback sont distincts ;
//     * - le callback n'est transmis à l'appelant qu'une seule fois.
//     */
//
//    fun startPinEntry(
//        pan: String,
//        req: PinRequest,
//        onResult: (Int, ByteArray?) -> Unit,
//    ) {
//        require(pan.length in 13..19 && pan.all { it in '0'..'9' }) {
//            "Le PAN fourni au PIN pad est invalide"
//        }
//        require(req.minLength >= 4 && req.maxLength >= req.minLength) {
//            "Longueurs du PIN invalides"
//        }
//        require(req.maxLength <= 12) {
//            "La longueur maximale du PIN ne doit pas dépasser 12"
//        }
//        require(req.timeout.inWholeSeconds > 0) {
//            "Le timeout doit être positif"
//        }
//        require(req.pinKeyIndex > 0) {
//            "L'index de la clé maître doit être positif"
//        }
//
//        val completed = AtomicBoolean(false)
//
//        fun completeOnce(code: Int, block: ByteArray?) {
//            if (!completed.compareAndSet(false, true)) {
//                TerminalLog.w("PIN pad : callback supplémentaire ignoré")
//                return
//            }
//
//            // Ne jamais afficher le contenu du bloc PIN.
//            TerminalLog.i(
//                "PIN pad : résultat code=$code, " +
//                        "tailleBloc=${block?.size ?: 0}"
//            )
//
//            onResult(code, block)
//        }
//
//        // 1. Initialiser le clavier sécurisé.
//        val initCode = try {
//            pinPad.initPinPad(PinPadTypeEnum.INTERNAL)
//        } catch (e: Exception) {
//            TerminalLog.e(
//                "initPinPad : exception ${e.javaClass.simpleName}"
//            )
//            throw e
//        }
//
//        TerminalLog.i("initPinPad : retCode=$initCode")
//
//        if (initCode != SdkResult.Success) {
//            completeOnce(initCode, null)
//            return
//        }
//
//        // 2. Vérifier la présence réelle des clés.
//        val masterExists = pinPad.isKeyExist(req.pinKeyIndex)
//        val pinKeyExists = pinPad.isKeyExist(
//            req.pinKeyIndex,
//            WorkKeyTypeEnum.PINKEY
//        )
//
//        TerminalLog.i(
//            "Vérification clés : maître=$masterExists, PIN=$pinKeyExists"
//        )
//
//        if (!masterExists || !pinKeyExists) {
//            TerminalLog.e(
//                "Saisie annulée : clé maître ou clé PIN absente"
//            )
//
//            // Évite de lancer une saisie avec une clé absente.
//            // Ce code générique signale un échec matériel au niveau du module.
//            completeOnce(-7999, null)
//            return
//        }
//
//        // 3. Calculer le KCV réellement stocké, sans afficher la clé.
//        val actualKcv = pinPad.calcWKeyKCV(
//            req.pinKeyIndex,
//            WorkKeyTypeEnum.PINKEY
//        )
//
//        TerminalLog.i(
//            "KCV réel clé PIN=" +
//                    (actualKcv?.joinToString("") {
//                        "%02X".format(it.toInt() and 0xFF)
//                    } ?: "indisponible")
//        )
//
//        // 4. Construire les longueurs autorisées.
//        val lengths = buildList {
//            if (req.allowBypass) add(0)
//            addAll(req.minLength..req.maxLength)
//        }.toIntArray()
//
//        // 5. Utiliser le format correspondant à la demande.
//        val algorithm = when (req.format) {
//            PinBlockFormat.ISO_0 ->
//                PinAlgorithmModeEnum.ISO9564FMT0
//
//            PinBlockFormat.ISO_1 ->
//                PinAlgorithmModeEnum.ISO9564FMT1
//
//            PinBlockFormat.ISO_3 ->
//                PinAlgorithmModeEnum.ISO9564FMT3
//        }
//
//        val listener = object : OnPinPadInputListener {
//            override fun onInputResult(
//                retCode: Int,
//                data: ByteArray?,
//            ) {
//                TerminalLog.i("onInputResult : retCode=$retCode")
//
//                // Un bloc tout-zéro ne doit jamais être accepté.
//                val invalidBlock = data == null ||
//                        data.size != 8 ||
//                        data.all { it == 0.toByte() }
//
//                if (retCode == SdkResult.Success && invalidBlock) {
//                    TerminalLog.e(
//                        "Résultat PIN invalide : bloc absent, " +
//                                "taille incorrecte ou bloc tout-zéro"
//                    )
//                    completeOnce(-7999, null)
//                    return
//                }
//
//                completeOnce(retCode, data)
//            }
//
//            override fun onSendKey(keyCode: Byte) {
//                // Ne pas journaliser les touches saisies.
//            }
//        }
//
//        TerminalLog.i(
//            "inputOnlinePin : groupeMaître=${req.pinKeyIndex}, " +
//                    "format=${req.format}, " +
//                    "longueurs=${lengths.joinToString(",")}, " +
//                    "timeout=${req.timeout.inWholeSeconds}s"
//        )
//
//        val startCode = try {
//            pinPad.inputOnlinePin(
//                lengths,
//                req.timeout.inWholeSeconds.toInt(),
//                pan,
//                req.pinKeyIndex,
//                algorithm,
//                listener
//            )
//        } catch (e: Exception) {
//            TerminalLog.e(
//                "inputOnlinePin : exception ${e.javaClass.simpleName}"
//            )
//            throw e
//        }
//
//        TerminalLog.i("inputOnlinePin : code de démarrage=$startCode")
//
//        if (startCode != SdkResult.Success) {
//            completeOnce(startCode, null)
//        }
//    }
//
//    /**
//     * Annule la saisie en cours.
//     */
//    fun cancelPinEntry() {
//        pinPad.cancelInput()
//        TerminalLog.d("cancelInput appelé")
//    }
//}

package com.example.terminalmodule.internal

import android.content.Context
import com.example.terminalmodule.model.KeyKind
import com.example.terminalmodule.model.PinBlockFormat
import com.example.terminalmodule.model.PinRequest
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.SdkResult
import com.nexgo.oaf.apiv3.device.pinpad.OnPinPadInputListener
import com.nexgo.oaf.apiv3.device.pinpad.PinAlgorithmModeEnum
import com.nexgo.oaf.apiv3.device.pinpad.PinPadTypeEnum
import com.nexgo.oaf.apiv3.device.pinpad.WorkKeyTypeEnum
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adaptateur entre le module terminal et le SDK PIN pad Nexgo.
 */
internal class NexgoPinPadSdk(
    private val engine: DeviceEngine,
    @Suppress("unused") private val context: Context,
    //sdkResult: SdkResult
) {

    private val pinPad
        get() = engine.pinPad

    /**
     * Écrit une clé maître.
     * À utiliser uniquement avec des clés de test autorisées.
     */
    fun writeMasterKey(index: Int, key: ByteArray): Int {
        require(index > 0) {
            "L'index de la clé maître doit être positif"
        }
        require(key.isNotEmpty()) {
            "La clé maître est vide"
        }

        return pinPad.writeMKey(index, key, key.size)
    }

    /**
     * Écrit une clé de travail chiffrée sous la clé maître parente.
     */
    fun writeWorkKey(
        parentIndex: Int,
        kind: KeyKind,
        cryptogram: ByteArray,
    ): Int {
        require(parentIndex > 0) {
            "L'index de la clé maître parente doit être positif"
        }
        require(cryptogram.isNotEmpty()) {
            "Le cryptogramme de la clé est vide"
        }

        val type = when (kind) {
            KeyKind.PIN -> WorkKeyTypeEnum.PINKEY
            KeyKind.MAC -> WorkKeyTypeEnum.MACKEY
            KeyKind.DATA -> WorkKeyTypeEnum.TDKEY
            KeyKind.MASTER -> throw IllegalArgumentException(
                "Une clé maître ne peut pas être écrite comme clé de travail"
            )
        }

        return pinPad.writeWKey(
            parentIndex,
            type,
            cryptogram,
            cryptogram.size
        )
    }

    /**
     * Lance la saisie sécurisée du PIN.
     *
     * onResult reçoit le code SDK et le bloc PIN chiffré.
     * Le contenu du PIN et du bloc ne doit jamais être journalisé.
     */
    fun startPinEntry(
        pan: String,
        req: PinRequest,
        onResult: (Int, ByteArray?) -> Unit,
    ) {
        val normalizedPan = pan.filter { it in '0'..'9' }

        require(normalizedPan.length in 13..19) {
            "Le PAN fourni au PIN pad est invalide"
        }
        require(req.minLength >= 4 && req.maxLength >= req.minLength) {
            "Longueurs du PIN invalides"
        }
        require(req.maxLength <= 12) {
            "La longueur maximale du PIN ne doit pas dépasser 12"
        }
        require(req.timeout.inWholeSeconds > 0) {
            "Le timeout doit être positif"
        }
        require(req.pinKeyIndex > 0) {
            "L'index de la clé maître doit être positif"
        }

        val completed = AtomicBoolean(false)

        fun completeOnce(code: Int, block: ByteArray?) {
            if (!completed.compareAndSet(false, true)) {
                TerminalLog.w("PIN pad : callback supplémentaire ignoré")
                return
            }

            TerminalLog.i(
                "PIN pad : résultat code=$code, " +
                        "tailleBloc=${block?.size ?: 0}"
            )

            // Ne jamais transmettre un bloc invalide.
            val validBlock = block != null &&
                    block.size == 8 &&
                    block.any { it != 0.toByte() }

            if (code == SdkResult.Success && !validBlock) {
                TerminalLog.e(
                    "PIN pad : bloc invalide, transmission interdite"
                )
                onResult(-7999, null)
                return
            }

            onResult(code, block)
        }

        // 1. Initialiser le PIN pad.
        val initCode = try {
            pinPad.initPinPad(PinPadTypeEnum.INTERNAL)
        } catch (e: Exception) {
            TerminalLog.e(
                "initPinPad : exception ${e.javaClass.simpleName}"
            )
            throw e
        }

        TerminalLog.i("initPinPad : retCode=$initCode")

        if (initCode != SdkResult.Success) {
            completeOnce(initCode, null)
            return
        }

        // 2. Vérifier la présence de la clé maître et de la clé PIN.
        val masterExists = pinPad.isKeyExist(req.pinKeyIndex)
        val pinKeyExists = pinPad.isKeyExist(
            req.pinKeyIndex,
            WorkKeyTypeEnum.PINKEY

        )

        TerminalLog.i(
            "Vérification clés : maître=$masterExists, PIN=$pinKeyExists"
        )

        if (!masterExists || !pinKeyExists) {
            TerminalLog.e(
                "Saisie PIN impossible : clé maître ou clé PIN absente"
            )
            completeOnce(-7999, null)
            return
        }

        // 3. Vérifier le KCV de la clé de travail PIN.
        val actualKcv = pinPad.calcWKeyKCV(
            req.pinKeyIndex,
            WorkKeyTypeEnum.PINKEY
        )

        TerminalLog.i(
            "KCV clé PIN disponible=${actualKcv != null && actualKcv.isNotEmpty()}"
        )

        // 4. Construire les longueurs PIN autorisées.
        val lengths = buildList {
            if (req.allowBypass) add(0)
            addAll(req.minLength..req.maxLength)
        }.toIntArray()

        // 5. Sélectionner le format ISO 9564 demandé.
        val algorithm = when (req.format) {
            PinBlockFormat.ISO_0 ->
                PinAlgorithmModeEnum.ISO9564FMT0

            PinBlockFormat.ISO_1 ->
                PinAlgorithmModeEnum.ISO9564FMT1

            PinBlockFormat.ISO_3 ->
                PinAlgorithmModeEnum.ISO9564FMT3
        }

        // 6. Recevoir le résultat sans journaliser les données sensibles.
        val listener = object : OnPinPadInputListener {

            override fun onInputResult(
                retCode: Int,
                data: ByteArray?,
            ) {
                TerminalLog.i(
                    "onInputResult : retCode=$retCode, " +
                            "tailleBloc=${data?.size ?: 0}"
                )

                if (retCode != SdkResult.Success) {
                    completeOnce(retCode, null)
                    return
                }

                val validBlock = data != null &&
                        data.size == 8 &&
                        data.any { it != 0.toByte() }

                if (!validBlock) {
                    TerminalLog.e(
                        "Résultat PIN invalide : bloc absent, " +
                                "taille incorrecte ou bloc tout-zéro"
                    )
                    completeOnce(-7999, null)
                    return
                }

                completeOnce(retCode, data)
            }

            override fun onSendKey(keyCode: Byte) {
                // Ne jamais journaliser les touches du PIN.
            }
        }

        TerminalLog.i(
            "PIN pad : format=${req.format}, " +
                    "longueurs=${lengths.joinToString(",")}, " +
                    "timeout=${req.timeout.inWholeSeconds}s"
        )

        // 7. Appeler le SDK avec l'ordre correct des paramètres.
        val startCode = try {
            pinPad.inputOnlinePin(
                lengths,
                req.pinKeyIndex,
                normalizedPan,
                req.timeout.inWholeSeconds.toInt(),
                algorithm,
                listener
            )
        } catch (e: Exception) {
            TerminalLog.e(
                "inputOnlinePin : exception ${e.javaClass.simpleName}"
            )
            throw e
        }

        TerminalLog.i(
            "inputOnlinePin : code de démarrage=$startCode"
        )

        // Un code de démarrage en erreur signifie que le callback
        // peut ne jamais être appelé.
        if (startCode != SdkResult.Success) {
            completeOnce(startCode, null)
        }
    }

    /**
     * Annule la saisie du PIN.
     */
    fun cancelPinEntry() {
        pinPad.cancelInput()
        TerminalLog.d("cancelInput appelé")
    }
}
