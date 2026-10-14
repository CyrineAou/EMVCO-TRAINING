package com.example.nexgointegration.parameters

import android.util.Log
import com.example.terminalmodule.api.EmvKernelConfigurator

class EmvParametersManager(
    private val provider: EmvParametersProvider,
    private val configurator: EmvKernelConfigurator
) {

    companion object {
        private const val TAG = "EMV_PARAMS"
    }

    fun apply(): Result<Unit> = runCatching {
        val aids = provider.getAids()
        val capks = provider.getCapks()
        val terminal = provider.getTerminalParam()

        Log.d(TAG, "AID lus : ${aids.size}")
        Log.d(TAG, "CAPK lus : ${capks.size}")
        check(aids.isNotEmpty()) { "Aucun AID lu dans la base Worldline" }
        check(capks.isNotEmpty()) { "Aucune CAPK lue dans la base Worldline" }

        configurator.loadParameters(aids, capks, terminal).getOrThrow()


        Log.d(TAG, "Paramètres envoyés au kernel L2")
        Unit
    }.onFailure { Log.e(TAG, "Échec chargement paramètres", it) }
}