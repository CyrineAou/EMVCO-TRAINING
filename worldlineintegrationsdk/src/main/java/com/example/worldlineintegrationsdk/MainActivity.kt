package com.example.worldlineintegrationsdk

import android.os.Bundle
import android.util.Log
import android.util.Log.d
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.onSuccess


class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WorldlineTest"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

      //wordLiine documentation data

//        val sessionInfo = WorldlineSessionInfo(
//            clientSessionId = "68a4b0b183a34016b4b694b97938f75b",
//            customerId = "cf7d7aafd42e4a1cb9e4b0624a5041b1",
//            clientApiUrl = "https://payment.preprod.anzworldline-solutions.com.au/",
//            assetUrl = "https://assets.test.cdn.v-psp.com/s2s/515c2c0bd13d5dd4bd42"
//        )
//
//        val worldlineManager = WorldlineManager(
//            context = this,
//            sessionInfo = sessionInfo,
//            isProduction = false
//        )

//        lifecycleScope.launch {
//            val result = worldlineManager.getAvailablePaymentProducts(
//                amountInCents = 1000L,
//                currencyCode = "AUD",
//                countryCode = "AU"
//            )
//
//
//            result
//            result.onSuccess { products ->
//                d(TAG, "Session valide. ${products.paymentProducts.size} moyens de paiement trouvés :")
//                products.paymentProducts.forEach { product ->
//                    d(TAG, "  - ${product.id} : ${product.label}")
//                }
//            }.onFailure { error ->
//                Log.e(TAG, "Échec (session invalide/expirée ou erreur réseau) : ${error.message}", error)
//            }
//        }
    }
}