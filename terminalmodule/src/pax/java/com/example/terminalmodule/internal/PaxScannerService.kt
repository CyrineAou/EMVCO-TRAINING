package com.example.terminalmodule.pax.internal


import com.example.terminalmodule.api.ScannerService
import com.example.terminalmodule.internal.awaitCallback
import com.example.terminalmodule.internal.terminalCall
import com.example.terminalmodule.model.TerminalError
import com.example.terminalmodule.model.TerminalResult
import kotlin.time.Duration

internal class PaxScannerService(private val dal: IDAL) : ScannerService {

    override suspend fun scan(timeout: Duration): TerminalResult<String> = terminalCall {
        val scanner = dal.getScanner(EScannerType.REAR) // SDK-VERIFY
        awaitCallback<String>(onCancel = { scanner.close() }) { resume ->
            scanner.open()
            scanner.setTimeOut(timeout.inWholeMilliseconds.toInt()) // SDK-VERIFY
            scanner.start(object : IScanner.IScanListener { // SDK-VERIFY
                override fun onRead(result: IScanner.IScanResult?) {
                    scanner.close()
                    val code = result?.content
                    resume(
                        if (code.isNullOrEmpty()) TerminalResult.Failure(TerminalError.Hardware(-1, "Code vide"))
                        else TerminalResult.Success(code)
                    )
                }
                override fun onFinish() { scanner.close(); resume(TerminalResult.Failure(TerminalError.Timeout)) }
                override fun onCancel() { scanner.close(); resume(TerminalResult.Failure(TerminalError.Cancelled)) }
            })
        }
    }
}
