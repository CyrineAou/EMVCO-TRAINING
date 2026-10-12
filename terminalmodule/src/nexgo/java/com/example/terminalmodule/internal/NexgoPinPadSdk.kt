
package com.example.terminalmodule.internal

import android.content.Context
import android.os.SystemClock
import com.example.terminalmodule.model.KeyKind
import com.example.terminalmodule.model.PinBlockFormat
import com.example.terminalmodule.model.PinRequest
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.SdkResult
import com.nexgo.oaf.apiv3.device.pinpad.OnPinPadInputListener
import com.nexgo.oaf.apiv3.device.pinpad.PinAlgorithmModeEnum
import com.nexgo.oaf.apiv3.device.pinpad.PinPad
import com.nexgo.oaf.apiv3.device.pinpad.PinPadTypeEnum
import com.nexgo.oaf.apiv3.device.pinpad.WorkKeyTypeEnum
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class NexgoPinPadSdk(
    private val engine: DeviceEngine,
    @Suppress("unused") private val context: Context
) {

    private val pinPad: PinPad
        get() = engine.pinPad

    private val initialized = AtomicBoolean(false)

    private fun initOnce(): Int {
        if (initialized.get()) {
            return SdkResult.Success
        }

        synchronized(initialized) {
            if (initialized.get()) {
                return SdkResult.Success
            }

            val code = pinPad.initPinPad(PinPadTypeEnum.INTERNAL)

            TerminalLog.i("initPinPad : retCode=$code")

            if (code == SdkResult.Success) {
                initialized.set(true)
            }

            return code
        }
    }

    /**
     * Applique uniquement les réglages explicitement configurés.
     * Aucun réglage n'est appliqué par défaut.
     */
    private fun applyPresets() {
        PinSdkOptions.presets.forEach { (method, valueName) ->
            runCatching {
                val targetMethod = PinPad::class.java.methods.firstOrNull {
                    it.name == method &&
                            it.parameterTypes.size == 1 &&
                            it.parameterTypes[0].isEnum
                } ?: error("Méthode introuvable : $method")

                val enumValue = targetMethod.parameterTypes[0]
                    .enumConstants
                    .firstOrNull { (it as Enum<*>).name == valueName }
                    ?: error("Valeur enum introuvable : $valueName")

                targetMethod.invoke(pinPad, enumValue)

                TerminalLog.i("Réglage appliqué : $method($valueName)")
            }.onFailure {
                TerminalLog.w(
                    "Réglage $method($valueName) échoué : " +
                            "${it.javaClass.simpleName}: ${it.message}"
                )
            }
        }
    }

    fun writeMasterKey(index: Int, key: ByteArray): Int {
        require(index > 0) {
            "Index de clé maître invalide"
        }
        require(key.isNotEmpty()) {
            "Clé maître vide"
        }

        val initCode = initOnce()
        if (initCode != SdkResult.Success) {
            return initCode
        }

        applyPresets()

        return pinPad.writeMKey(index, key, key.size)
    }

    fun writeWorkKey(
        parentIndex: Int,
        kind: KeyKind,
        cryptogram: ByteArray
    ): Int {
        require(parentIndex > 0) {
            "Index de clé maître parente invalide"
        }
        require(cryptogram.isNotEmpty()) {
            "Cryptogramme vide"
        }

        val keyType = when (kind) {
            KeyKind.PIN -> WorkKeyTypeEnum.PINKEY
            KeyKind.MAC -> WorkKeyTypeEnum.MACKEY
            KeyKind.DATA -> WorkKeyTypeEnum.TDKEY
            KeyKind.MASTER -> throw IllegalArgumentException(
                "Une clé maître n'est pas une clé de travail"
            )
        }

        val initCode = initOnce()
        if (initCode != SdkResult.Success) {
            return initCode
        }

        applyPresets()

        return pinPad.writeWKey(
            parentIndex,
            keyType,
            cryptogram,
            cryptogram.size
        )
    }

    fun startPinEntry(
        pan: String,
        req: PinRequest,
        onResult: (Int, ByteArray?) -> Unit
    ) {
        val digits = pan.filter { it in '0'..'9' }

        require(digits.length in 13..19) {
            "PAN invalide pour le PIN pad"
        }
        require(
            req.minLength >= 4 &&
                    req.maxLength in req.minLength..12
        ) {
            "Longueurs de PIN invalides"
        }
        require(req.timeout.inWholeSeconds in 1..Int.MAX_VALUE.toLong()) {
            "Délai invalide"
        }
        require(req.pinKeyIndex > 0) {
            "Index de clé PIN invalide"
        }

        val completed = AtomicBoolean(false)

        fun complete(code: Int, block: ByteArray?) {
            if (!completed.compareAndSet(false, true)) {
                TerminalLog.w("PIN pad : réponse supplémentaire ignorée")
                return
            }

            val invalidBlock =
                code == SdkResult.Success &&
                        (block == null ||
                                block.size != 8 ||
                                block.all { it == 0.toByte() })

            if (invalidBlock) {
                TerminalLog.e(
                    "PIN pad : succès annoncé, mais bloc absent, " +
                            "taille incorrecte ou bloc tout à zéro"
                )

                onResult(NexgoErrorMapper.LOCAL_INVALID_BLOCK, null)
                return
            }

            TerminalLog.i(
                "PIN pad : code=$code, tailleBloc=${block?.size ?: 0}"
            )

            onResult(
                code,
                if (code == SdkResult.Success) block else null
            )
        }

        val initCode = initOnce()

        if (initCode != SdkResult.Success) {
            complete(initCode, null)
            return
        }

        val masterExists = pinPad.isKeyExist(req.pinKeyIndex)
        val pinKeyExists = pinPad.isKeyExist(
            req.pinKeyIndex,
            WorkKeyTypeEnum.PINKEY
        )

        TerminalLog.i(
            "Clés présentes : maître=$masterExists, PIN=$pinKeyExists"
        )

        if (!masterExists || !pinKeyExists) {
            complete(NexgoErrorMapper.LOCAL_KEY_MISSING, null)
            return
        }

        // Ne jamais journaliser la clé ni le bloc PIN.
        val storedKcv = pinPad.calcWKeyKCV(
            req.pinKeyIndex,
            WorkKeyTypeEnum.PINKEY
        )

        TerminalLog.i(
            "KCV PIN : disponible=${storedKcv != null}, " +
                    "longueur=${storedKcv?.size ?: 0}, " +
                    "valeur=${storedKcv?.toHex() ?: "null"}"
        )

        applyPresets()

        if (pinPad.isInputting()) {
            TerminalLog.w(
                "Une saisie PIN précédente est encore active ; annulation"
            )

            pinPad.cancelInput()

            TerminalLog.i(
                "isInputting après annulation=${pinPad.isInputting()}"
            )

            if (pinPad.isInputting()) {
                TerminalLog.e(
                    "Le PIN pad est toujours occupé après annulation"
                )
                complete(NexgoErrorMapper.LOCAL_INVALID_BLOCK, null)
                return
            }
        }

        val lengths = (
                PinSdkOptions.lengthOverride
                    ?: buildList {
                        if (req.allowBypass) {
                            add(0)
                        }
                        addAll(req.minLength..req.maxLength)
                    }
                ).toIntArray()

        require(lengths.isNotEmpty()) {
            "La liste des longueurs PIN est vide"
        }
        require(lengths.all { it == 0 || it in 4..12 }) {
            "La liste des longueurs PIN contient une valeur invalide"
        }

        val format = PinSdkOptions.forceFormat ?: req.format

        val algorithm = when (format) {
            PinBlockFormat.ISO_0 -> PinAlgorithmModeEnum.ISO9564FMT0
            PinBlockFormat.ISO_1 -> PinAlgorithmModeEnum.ISO9564FMT1
            PinBlockFormat.ISO_3 -> PinAlgorithmModeEnum.ISO9564FMT3
        }

        val panValue = PinSdkOptions.pan(digits)

        val timeoutSeconds = req.timeout.inWholeSeconds.toInt()

        val p1 = if (PinSdkOptions.swapTimeoutIndex) {
            req.pinKeyIndex
        } else {
            timeoutSeconds
        }

        val p3 = if (PinSdkOptions.swapTimeoutIndex) {
            timeoutSeconds
        } else {
            req.pinKeyIndex
        }

        val startedAt = SystemClock.elapsedRealtime()
        val keyPresses = AtomicInteger(0)

        val listener = object : OnPinPadInputListener {

            override fun onInputResult(
                retCode: Int,
                data: ByteArray?
            ) {
                TerminalLog.i(
                    "onInputResult : retCode=$retCode, " +
                            "tailleBloc=${data?.size ?: 0}, " +
                            "événementsClavier=${keyPresses.get()}, " +
                            "durée=${SystemClock.elapsedRealtime() - startedAt} ms"
                )

                complete(retCode, data)
            }

            override fun onSendKey(keyCode: Byte) {
                // Ne jamais journaliser keyCode : il peut révéler une touche PIN.
                keyPresses.incrementAndGet()

                TerminalLog.d(
                    "Événement clavier PIN reçu : " +
                            "nombre=${keyPresses.get()}"
                )
            }
        }

        TerminalLog.i(
            "inputOnlinePin : ${PinSdkOptions.describe()}, " +
                    "algo=$algorithm, p1=$p1, p3=$p3, " +
                    "longueurPAN=${panValue.length}, " +
                    "longueurs=${lengths.joinToString(",")}"
        )


        // inputOnlinePin attend l'index de la clé maître,
        // c'est-à-dire l'index utilisé par writeWKey(parentIndex, ...).
        val masterKeyIndex = req.pinKeyIndex

        val panBlock = panValue.toByteArray(Charsets.US_ASCII)

        TerminalLog.i(
            "inputOnlinePin : surcharge byte[], " +
                    "algo=$algorithm, timeout=$timeoutSeconds, " +
                    "masterKeyIndex=$masterKeyIndex, " +
                    "panBytes=${panBlock.size}, " +
                    "longueurs=${lengths.joinToString(",")}"
        )

        val startCode = try {
            pinPad.inputOnlinePin(
                lengths,
                timeoutSeconds,
                panBlock,
                masterKeyIndex,
                algorithm,
                listener
            )
        } catch (exception: Exception) {
            TerminalLog.e(
                "Erreur démarrage PIN : " +
                        "${exception.javaClass.simpleName}: " +
                        "${exception.message}"
            )
            complete(NexgoErrorMapper.LOCAL_INVALID_BLOCK, null)
            return
        }

        TerminalLog.i(
            "inputOnlinePin : code de démarrage=$startCode"
        )

        if (startCode != SdkResult.Success) {
            complete(startCode, null)
        }
    }

    fun cancelPinEntry() {
        runCatching {
            pinPad.cancelInput()
            TerminalLog.d("cancelInput appelé")
        }.onFailure {
            TerminalLog.e(
                "Échec de cancelInput : " +
                        "${it.javaClass.simpleName}: ${it.message}"
            )
        }
    }
}