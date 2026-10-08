package com.example.terminalmodule.internal

import com.example.terminalmodule.api.ScannerService
import com.example.terminalmodule.model.TerminalError
import com.example.terminalmodule.model.TerminalResult
import com.example.terminalmodule.nexgo.internal.NexgoErrorMapper
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.device.scanner.OnScannerListener
import com.nexgo.oaf.apiv3.device.scanner.ScannerCfgEntity
import kotlin.time.Duration

internal class NexgoScannerService(private val engine: DeviceEngine) : ScannerService {

    override suspend fun scan(timeout: Duration): TerminalResult<String> = terminalCall {
        val scanner = engine.scanner // SDK-VERIFY
        awaitCallback<String>(onCancel = { scanner.stopScan() }) { resume ->
            scanner.initScanner(ScannerCfgEntity(),null) // SDK-VERIFY
            scanner.startScan(timeout.inWholeSeconds.toInt(), object : OnScannerListener {
                override fun onInitResult(retCode: Int) {
                    NexgoErrorMapper.fromCode(retCode)?.let { resume(TerminalResult.Failure(it)) }
                }
                override fun onScannerResult(retCode: Int, data: String?) {
                    val error = NexgoErrorMapper.fromCode(retCode)
                    resume(
                        when {
                            error != null -> TerminalResult.Failure(error)
                            data.isNullOrEmpty() -> TerminalResult.Failure(TerminalError.Hardware(-1, "Code vide"))
                            else -> TerminalResult.Success(data)
                        }
                    )
                }
            })
        }
    }
}
