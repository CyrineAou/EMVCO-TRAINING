package com.example.nexgointegration.getway



class PaymentGatewayImpl : PaymentGateway {

    override suspend fun authorize(
        request: GatewayRequest
    ): GatewayResponse {

        // TODO:
        // Remplacer cette partie par l'appel HTTPS
        // vers ton vrai Payment Gateway / Host.

        return GatewayResponse(
            responseCode = "00",
            authorizationCode = "123456"
        )
    }
}