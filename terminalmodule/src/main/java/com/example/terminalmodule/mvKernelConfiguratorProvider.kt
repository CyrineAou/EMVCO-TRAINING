package com.example.terminalmodule

import android.content.Context
import com.example.terminalmodule.api.EmvKernelConfigurator
import com.example.terminalmodule.internal.NexgoEmvConfigurator
import com.nexgo.oaf.apiv3.APIProxy


object EmvKernelConfiguratorProvider {

    // Doit être le même nom que celui de getEmvHandler2(...) dans ta transaction
    const val EMV_KERNEL_NAME = "Main-Kernel2"

    @Volatile
    private var instance: EmvKernelConfigurator? = null

    @Synchronized
    fun create(context: Context): EmvKernelConfigurator {
        instance?.let { return it }

        val deviceEngine = APIProxy.getDeviceEngine(context.applicationContext)
        val emvHandler = deviceEngine.getEmvHandler2(EMV_KERNEL_NAME)

        val configurator: EmvKernelConfigurator = NexgoEmvConfigurator(emvHandler)
        instance = configurator
        return configurator
    }
}