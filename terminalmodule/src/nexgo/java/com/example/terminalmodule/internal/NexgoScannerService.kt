package com.example.terminalmodule.internal

import com.example.terminalmodule.api.ScannerService
import com.example.terminalmodule.model.TerminalError
import com.example.terminalmodule.model.TerminalResult
import com.nexgo.oaf.apiv3.DeviceEngine
import com.nexgo.oaf.apiv3.device.scanner.OnScannerListener
import com.nexgo.oaf.apiv3.device.scanner.ScannerCfgEntity
import kotlin.time.Duration

internal class NexgoScannerService(private val engine: DeviceEngine) : ScannerService {

    override suspend fun scan(timeout: Duration): TerminalResult<String> = terminalCall {
        val scanner = engine.scanner // SDK-VERIFY
        TerminalLog.i("scan : timeout=${timeout.inWholeSeconds}s")
        awaitCallback<String>(
            name = "scan",
            onCancel = { TerminalLog.d("stopScan"); scanner.stopScan() },
        ) { resume ->
            TerminalLog.d("initScanner")
            scanner.initScanner(ScannerCfgEntity(),null) // SDK-VERIFY
            TerminalLog.d("startScan appelé")
            scanner.startScan(timeout.inWholeSeconds.toInt(), object : OnScannerListener {
                override fun onInitResult(retCode: Int) {
                    TerminalLog.d("onInitResult retCode=$retCode")
                    NexgoErrorMapper.fromCode(retCode)?.let { resume(TerminalResult.Failure(it)) }
                }

                override fun onScannerResult(retCode: Int, data: String?) {
                    TerminalLog.d("onScannerResult retCode=$retCode, data=${data?.length ?: 0} car.")
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