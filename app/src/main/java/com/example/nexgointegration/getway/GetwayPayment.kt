package com.example.nexgointegration.getway

interface PaymentGateway {

    suspend fun authorize(

        request: GatewayRequest): GatewayResponse
}