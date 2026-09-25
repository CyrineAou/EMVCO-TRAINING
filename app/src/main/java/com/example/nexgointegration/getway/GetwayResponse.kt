package com.example.nexgointegration.getway



data class GatewayResponse(
    val responseCode: String,
    val authorizationCode: String? = null,
    val issuerAuthenticationData: String? = null,
    val issuerScripts: String? = null
)