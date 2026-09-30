package com.example.nexgointegration.gateway


data class PaymentGatewayRequest(
    val transactionId: String,
    val amount: String,
    val currency: String = "788",
    val terminalId: String,
    val merchantId: String,
    val entryMode: String,
    val pan: String? = null,
    val expiryDate: String? = null,
    val track2: String? = null,
    val emvData: String? = null,
    val timestamp: String
)