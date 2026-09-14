package com.example.nexgointegration

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magellan.tapandgo.validatorsdk.T2UOpenPaymentSDK
import com.magellan.tapandgo.validatorsdk.ValidationSdkConfiguration

@Composable
fun PaymentSimulationScreen() {
    var statusText by remember { mutableStateOf("Prêt pour la transaction") }
    var isLoading by remember { mutableStateOf(false) }
    var statusColor by remember { mutableStateOf(Color.Gray) }

    val context = LocalContext.current

    val config = remember {
        ValidationSdkConfiguration(
            deviceModel = "PAX",
            deviceReference = "DEV_REF_001",
            ITP = "ITP_DEFAULT",
            keycloakBaseUrl = "https://your-keycloak-url.com",
            keycloakClientId = "your-client-id",
            keycloakClientSecret = "your-client-secret",
            keycloakRealm = "your-realm",
            openPaymentBoUrl = "https://your-bo-url.com",
            operator = "OPERATOR_NAME",
            paymentAppVersion = "1.0.0",
            poi = "POI_001",
            terminalPassword = "your-password",
            terminalUser = "your-user",
            ticketingAppVersion = "1.0.0",
            validatorSerialNumber = "SN_123456"
        )
    }

    LaunchedEffect(Unit) {
        Log.e("SDK_TEST", ">>> Lancement de init() ...")

        try {
            val result = T2UOpenPaymentSDK.init(context, config)

            // 1. Log direct de la classe
            Log.e("SDK_TEST", ">>> RETOUR INIT : $result")

            // 2. Conversion en JSON pour afficher toutes les propriétés de SDKResponse
            val jsonResult = com.google.gson.Gson().toJson(result)
            Log.e("SDK_TEST", ">>> CONTENU DETAILS : $jsonResult")

            // 3. Exemple d'accès aux propriétés (selon la structure de SDKResponse)
            // val isSuccess = result.isSuccess // ou result.status / result.code

            statusText = "Initialisation terminée (voir Logcat)"
            statusColor = Color.Green
        } catch (e: Throwable) {
            Log.e("SDK_TEST", ">>> ERREUR INIT : ${e.message}", e)
            statusText = "Erreur : ${e.localizedMessage}"
            statusColor = Color.Red
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Simulation de Paiement Magellan",
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Montant", fontSize = 14.sp)
                Text(text = "20.00 EUR", fontSize = 32.sp)
            }
        }

        Text(
            text = statusText,
            color = statusColor,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isLoading = true
                    statusText = "Lancement de la transaction..."
                    statusColor = Color.Blue
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Lancer le paiement", fontSize = 18.sp)
            }
        }
    }
}