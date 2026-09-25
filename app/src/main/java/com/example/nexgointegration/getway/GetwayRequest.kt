package com.example.nexgointegration.getway

data class GatewayRequest(
    val amount: String,
    val currency: String,
    val transactionType: String,
    val emvData: Map<String, String>
)