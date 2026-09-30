package com.example.nexgointegration.gateway

interface PaymentGateway {

    suspend fun pay(
        request: PaymentGatewayRequest
    ): PaymentGatewayResponsePayment

    suspend fun cancel(
        transactionId: String
    ): PaymentGatewayResponsePayment

    suspend fun checkStatus(
        transactionId: String
    ): PaymentGatewayResponsePayment
}