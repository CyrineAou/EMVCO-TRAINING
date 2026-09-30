package com.example.nexgointegration.gateway


data class PaymentGatewayResponsePayment(
    val success: Boolean,
    val responseCode: String,
    val message: String?,
    val transactionId: String?,
    val authorizationCode: String?,
    val gatewayTransactionId: String?,
    val rawResponse: String? = null
)