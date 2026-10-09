package com.example.terminalmodule.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import com.example.terminalmodule.api.PinPadService
import com.example.terminalmodule.model.EncryptedPin
import com.example.terminalmodule.model.KeyInjection
import com.example.terminalmodule.model.KeyKind
import com.example.terminalmodule.model.PinRequest
import com.example.terminalmodule.model.TerminalError
import com.example.terminalmodule.model.TerminalResult
import com.nexgo.oaf.apiv3.DeviceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sur Nexgo, les clés de travail sont rattachées à un groupe de clé maître :
 * PinRequest.pinKeyIndex désigne donc l'index de la clé MAÎTRE du groupe.
 */
internal class NexgoPinPadService(
    engine: DeviceEngine,
    private val context: Context,
) : PinPadService {

    private val sdk = NexgoPinPadSdk(engine, context)

    private val debuggable: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override suspend fun injectKey(injection: KeyInjection): TerminalResult<Unit> = terminalCall {
        TerminalLog.i("injectKey : ${describe(injection)}, debuggable=$debuggable")

        val invalid = KeyValidator.validate(injection, debuggable)
        if (invalid != null) {
            TerminalLog.w("injectKey refusée par la validation : $invalid")
            return@terminalCall TerminalResult.Failure(invalid)
        }

        if (injection is KeyInjection.UnderParent) {
            if (injection.kind == KeyKind.MASTER) {
                return@terminalCall TerminalResult.Failure(
                    TerminalError.InvalidKey("Une clé maître ne s'injecte pas sous un parent")
                )
            }
            if (injection.index != injection.parentIndex) {
                TerminalLog.w(
                    "Nexgo : la clé de travail est rattachée à la clé maître ${injection.parentIndex} " +
                            "(index ${injection.index} ignoré)"
                )
            }
        }
        TerminalLog.d("injectKey : validation OK")

        withContext(Dispatchers.IO) {
            val code: Int = when (injection) {
                is KeyInjection.ClearMasterForTest -> {
                    TerminalLog.d("writeMasterKey index=${injection.index}")
                    sdk.writeMasterKey(injection.index, injection.key)
                }
                is KeyInjection.UnderParent -> {
                    TerminalLog.d("writeWorkKey ${injection.kind} sous maître=${injection.parentIndex}")
                    sdk.writeWorkKey(injection.parentIndex, injection.kind, injection.cryptogram)
                }
            }
            TerminalLog.i("injectKey : retCode=$code")
            val error = NexgoErrorMapper.fromCode(code)
            if (error == null) TerminalResult.Success(Unit) else TerminalResult.Failure(error)
        }
    }

    override suspend fun requestPin(request: PinRequest): TerminalResult<EncryptedPin> = terminalCall {
        TerminalLog.i(
            "requestPin : session=${TerminalLog.short(request.sessionId)}, groupe maître=${request.pinKeyIndex}, " +
                    "format=${request.format}, longueur=${request.minLength}..${request.maxLength}, " +
                    "bypass=${request.allowBypass}, timeout=${request.timeout.inWholeSeconds}s"
        )

        val pan = CardSessionStore.take(request.sessionId)
        if (pan == null) {
            TerminalLog.w("requestPin : session absente ou expirée")
            return@terminalCall TerminalResult.Failure(TerminalError.SessionExpired)
        }

        awaitCallback<EncryptedPin>(
            name = "requestPin",
            onCancel = {
                TerminalLog.d("cancelPinEntry")
                sdk.cancelPinEntry()
            },
        ) { resume ->
            TerminalLog.d("startPinEntry appelé")

            sdk.startPinEntry(pan, request) { code: Int, block: ByteArray? ->
                // Ne jamais journaliser le PIN ni le contenu du PIN block.
                val blockSize = block?.size ?: 0
                val zero = block != null &&
                        block.isNotEmpty() &&
                        block.all { it == 0.toByte() }

                val error = NexgoErrorMapper.fromCode(code)

                TerminalLog.i(
                    "requestPin callback: code=$code, " +
                            "blockSize=$blockSize, allZero=$zero, " +
                            "mappedError=${error?.javaClass?.simpleName ?: "none"}"
                )

                val result: TerminalResult<EncryptedPin> = when {
                    error != null -> TerminalResult.Failure(error)

                    block == null || block.isEmpty() ->
                        TerminalResult.Failure(
                            if (request.allowBypass) {
                                TerminalError.PinBypassed
                            } else {
                                TerminalError.Cancelled
                            }
                        )

                    else -> TerminalResult.Success(
                        EncryptedPin(block, request.format)
                    )
                }

                resume(result)
            }
        }
    }

    // Description sûre d'une injection : jamais d'octet de clé.
    private fun describe(injection: KeyInjection): String = when (injection) {
        is KeyInjection.ClearMasterForTest ->
            "maître(test) index=${injection.index}, ${injection.key.size} octets, " +
                    "KCV calculé=${kcvOf(injection.key)}, KCV attendu=${injection.kcv?.toHex() ?: "aucun"}"
        is KeyInjection.UnderParent ->
            "${injection.kind} index=${injection.index} sous maître=${injection.parentIndex}, " +
                    "cryptogramme ${injection.cryptogram.size} octets, KCV attendu=${injection.kcv?.toHex() ?: "aucun"}"
    }

    private fun kcvOf(key: ByteArray): String =
        runCatching { Kcv.tdes(key).toHex() }.getOrDefault("n/a")
}