package com.example.nexgointegration.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PaymentGatewayImpl : PaymentGateway {

    private val client = OkHttpClient.Builder()
        .connectTimeout(
            GatewayConfig.CONNECT_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
        .readTimeout(
            GatewayConfig.READ_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
        .writeTimeout(
            GatewayConfig.WRITE_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
        .build()

    private val jsonMediaType =
        "application/json; charset=utf-8".toMediaType()

    override suspend fun pay(
        request: PaymentGatewayRequest
    ): PaymentGatewayResponsePayment =
        withContext(Dispatchers.IO) {

            val json = JSONObject().apply {

                put("transactionId", request.transactionId)
                put("amount", request.amount)
                put("currency", request.currency)
                put("terminalId", request.terminalId)
                put("merchantId", request.merchantId)
                put("entryMode", request.entryMode)
                put("timestamp", request.timestamp)

                request.pan?.let {
                    put("pan", it)
                }

                request.expiryDate?.let {
                    put("expiryDate", it)
                }

                request.track2?.let {
                    put("track2", it)
                }

                request.emvData?.let {
                    put("emvData", it)
                }
            }

            executeRequest(
                endpoint = GatewayConfig.PAYMENT_ENDPOINT,
                json = json
            )
        }

    override suspend fun cancel(
        transactionId: String
    ): PaymentGatewayResponsePayment =
        withContext(Dispatchers.IO) {

            val json = JSONObject().apply {
                put("transactionId", transactionId)
            }

            executeRequest(
                endpoint = GatewayConfig.CANCEL_ENDPOINT,
                json = json
            )
        }

    override suspend fun checkStatus(
        transactionId: String
    ): PaymentGatewayResponsePayment =
        withContext(Dispatchers.IO) {

            val json = JSONObject().apply {
                put("transactionId", transactionId)
            }

            executeRequest(
                endpoint = GatewayConfig.STATUS_ENDPOINT,
                json = json
            )
        }

    private fun executeRequest(
        endpoint: String,
        json: JSONObject
    ): PaymentGatewayResponsePayment {

        val body = json
            .toString()
            .toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(
                GatewayConfig.BASE_URL + endpoint
            )
            .post(body)
            .addHeader(
                "Content-Type",
                "application/json"
            )
            .addHeader(
                "Accept",
                "application/json"
            )
            .build()

        return try {

            client.newCall(request).execute().use { response ->

                val responseBody =
                    response.body?.string().orEmpty()

                if (!response.isSuccessful) {

                    return PaymentGatewayResponsePayment(
                        success = false,
                        responseCode = response.code.toString(),
                        message = "HTTP ${response.code}",
                        transactionId = json.optString(
                            "transactionId",
                            null
                        ),
                        authorizationCode = null,
                        gatewayTransactionId = null,
                        rawResponse = responseBody
                    )
                }

                parseResponse(
                    responseBody = responseBody,
                    fallbackTransactionId =
                        json.optString(
                            "transactionId",
                            null
                        )
                )
            }

        } catch (e: Exception) {

            PaymentGatewayResponsePayment(
                success = false,
                responseCode = "NETWORK_ERROR",
                message = e.message ?: "Erreur réseau",
                transactionId =
                    json.optString(
                        "transactionId",
                        null
                    ),
                authorizationCode = null,
                gatewayTransactionId = null,
                rawResponse = null
            )
        }
    }

    private fun parseResponse(
        responseBody: String,
        fallbackTransactionId: String?
    ): PaymentGatewayResponsePayment {

        return try {

            val json = JSONObject(responseBody)

            val responseCode =
                json.optString(
                    "responseCode",
                    "99"
                )

            val success =
                json.optBoolean(
                    "success",
                    responseCode == "00"
                )

            PaymentGatewayResponsePayment(
                success = success,
                responseCode = responseCode,
                message = json.optString(
                    "message",
                    null
                ),
                transactionId = json.optString(
                    "transactionId",
                    fallbackTransactionId
                ),
                authorizationCode =
                    json.optString(
                        "authorizationCode",
                        null
                    ),
                gatewayTransactionId =
                    json.optString(
                        "gatewayTransactionId",
                        null
                    ),
                rawResponse = responseBody
            )

        } catch (e: Exception) {

            PaymentGatewayResponsePayment(
                success = false,
                responseCode = "INVALID_RESPONSE",
                message = "Réponse passerelle invalide",
                transactionId = fallbackTransactionId,
                authorizationCode = null,
                gatewayTransactionId = null,
                rawResponse = responseBody
            )
        }
    }
}