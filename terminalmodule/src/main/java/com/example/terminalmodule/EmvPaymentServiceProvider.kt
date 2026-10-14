
package com.example.terminalmodule

import android.content.Context
import com.example.terminalmodule.api.EmvPaymentService
import com.example.terminalmodule.internal.NexgoEmvConfigurator
import com.example.terminalmodule.internal.NexgoEmvPaymentService
import com.nexgo.oaf.apiv3.APIProxy

object EmvPaymentServiceProvider {

    @Volatile
    private var instance: EmvPaymentService? = null

    @Synchronized
    fun create(context: Context): EmvPaymentService {
        instance?.let { return it }

        val appContext = context.applicationContext

        // Réutilise le configurateur et le même EmvHandler2.
        val configurator =
            EmvKernelConfiguratorProvider.create(appContext) as NexgoEmvConfigurator

        val deviceEngine = APIProxy.getDeviceEngine(appContext)

        val service: EmvPaymentService =
            NexgoEmvPaymentService(
                deviceEngine,
                configurator.emvHandler,
                configurator
            )

        instance = service
        return service
    }
}
